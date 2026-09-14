/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.thingsboard.rule.engine.api.MailService;
import org.thingsboard.rule.engine.api.TbEmail;
import org.thingsboard.server.common.data.export.DataExportMode;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.dao.export.DataExportScheduleDao;
import org.thingsboard.server.dao.user.UserService;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DataExportScheduler {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final DataExportScheduleDao scheduleDao;
    private final DataExportService dataExportService;
    private final DataExportScheduleCalculator scheduleCalculator;
    private final DataExportProperties properties;
    private final MailService mailService;
    private final UserService userService;
    private final ThreadPoolTaskExecutor executor;

    public DataExportScheduler(
            DataExportScheduleDao scheduleDao,
            DataExportService dataExportService,
            DataExportScheduleCalculator scheduleCalculator,
            DataExportProperties properties,
            MailService mailService,
            UserService userService,
            @Qualifier("dataExportTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.scheduleDao = scheduleDao;
        this.dataExportService = dataExportService;
        this.scheduleCalculator = scheduleCalculator;
        this.properties = properties;
        this.mailService = mailService;
        this.userService = userService;
        this.executor = executor;
    }

    @Scheduled(
            initialDelayString = "${data-export.scheduler-initial-delay-ms:30000}",
            fixedDelayString = "${data-export.scheduler-interval-ms:60000}")
    public void dispatchDueSchedules() {
        if (!properties.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        scheduleDao.findDue(now, PageRequest.of(0, Math.max(1, properties.getScheduleBatchSize())))
                .forEach(schedule -> claimAndSubmit(schedule, now));
    }

    private void claimAndSubmit(DataExportSchedule schedule, long now) {
        long leaseUntil = now + TimeUnit.MINUTES.toMillis(Math.max(1, properties.getScheduleLeaseMinutes()));
        if (!scheduleDao.claim(schedule.getId(), now, leaseUntil)) {
            return;
        }
        try {
            executor.execute(() -> process(schedule));
        } catch (RuntimeException e) {
            long retryAt = now + TimeUnit.MINUTES.toMillis(Math.max(1, properties.getFailedRetryMinutes()));
            scheduleDao.markFailed(schedule.getId(), now, retryAt, "Worker queue is full");
            log.warn("[data-export] worker queue rejected schedule {}", schedule.getId());
        }
    }

    private void process(DataExportSchedule schedule) {
        try {
            deliver(schedule);
        } catch (Exception e) {
            long completedAt = System.currentTimeMillis();
            long retryAt = completedAt
                    + TimeUnit.MINUTES.toMillis(Math.max(1, properties.getFailedRetryMinutes()));
            scheduleDao.markFailed(schedule.getId(), completedAt, retryAt, safeError(e));
            log.error("[data-export] failed schedule {}", schedule.getId(), e);
        }
    }

    private void deliver(DataExportSchedule schedule) throws Exception {
        validateScheduleOwner(schedule);
        long endTs = System.currentTimeMillis();
        long startTs = resolveStartTs(schedule, endTs);
        DataExportRequest request = new DataExportRequest();
        request.setAllDevices(schedule.isAllDevices());
        request.setDeviceIds(schedule.getDeviceIds());
        request.setKeys(schedule.getKeys());
        request.setAttributeKeys(schedule.getAttributeKeys());
        request.setIncludeAttributes(schedule.isIncludeAttributes());
        request.setStartTs(startTs);
        request.setEndTs(endTs);
        request.setFormat(schedule.getFormat());

        try (DataExportArtifact artifact = dataExportService.exportScheduled(schedule, request)) {
            if (artifact.size() > properties.getMaxScheduleAttachmentBytes()) {
                throw new IllegalArgumentException("Scheduled export exceeds the attachment-size limit");
            }
            if (!mailService.isConfigured(schedule.getTenantId())) {
                throw new IllegalStateException("Mail service is not configured");
            }
            byte[] attachment = Files.readAllBytes(artifact.path());
            String zone = schedule.getTimezone();
            String range = DISPLAY_TIME.withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(startTs))
                    + " – " + DISPLAY_TIME.withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(endTs));
            String body = "<p>Tu exportación programada de Eficentra está lista.</p>"
                    + "<p><strong>Periodo:</strong> " + range + "<br>"
                    + "<strong>Registros:</strong> " + artifact.rowCount() + "</p>"
                    + "<p>El archivo se adjunta a este mensaje.</p>";
            mailService.send(schedule.getTenantId(), schedule.getCustomerId(), TbEmail.builder()
                    .to(schedule.getEmail())
                    .subject("Eficentra | Exportación de datos")
                    .body(body)
                    .html(true)
                    .attachments(Map.of(artifact.fileName(), attachment))
                    .build());

            long completedAt = System.currentTimeMillis();
            scheduleDao.markSuccess(schedule.getId(), completedAt, endTs,
                    scheduleCalculator.nextRun(schedule, completedAt));
            log.info("[data-export] delivered schedule {} with {} rows", schedule.getId(), artifact.rowCount());
        }
    }

    private long resolveStartTs(DataExportSchedule schedule, long endTs) {
        if (DataExportMode.INCREMENTAL.equals(schedule.getMode()) && schedule.getLastSuccessTs() != null) {
            return Math.min(endTs, schedule.getLastSuccessTs() + 1);
        }
        int days = DataExportMode.FULL.equals(schedule.getMode())
                ? schedule.getFullLookbackDays()
                : properties.getDefaultLookbackDays();
        return Math.max(0, endTs - TimeUnit.DAYS.toMillis(Math.max(1, days)));
    }

    private void validateScheduleOwner(DataExportSchedule schedule) {
        UserId userId = new UserId(schedule.getUserId());
        User owner = userService.findUserById(schedule.getTenantId(), userId);
        UserCredentials credentials = userService.findUserCredentialsByUserId(schedule.getTenantId(), userId);
        if (owner == null || credentials == null || !credentials.isEnabled()) {
            throw new org.springframework.security.access.AccessDeniedException("Schedule owner is unavailable");
        }
        if (Authority.TENANT_ADMIN.equals(owner.getAuthority())) {
            if (schedule.getCustomerId() != null && !schedule.getCustomerId().isNullUid()) {
                throw new org.springframework.security.access.AccessDeniedException("Schedule owner scope changed");
            }
            return;
        }
        if (!Authority.CUSTOMER_USER.equals(owner.getAuthority())
                || !Objects.equals(owner.getCustomerId(), schedule.getCustomerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Schedule owner scope changed");
        }
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
