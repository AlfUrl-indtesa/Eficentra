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
package org.thingsboard.server.dao.model.sql;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.thingsboard.server.common.data.export.DataExportFormat;
import org.thingsboard.server.common.data.export.DataExportMode;
import org.thingsboard.server.common.data.export.DataExportPeriod;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleStatus;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "data_export_schedule")
public class DataExportScheduleEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "all_devices", nullable = false)
    private boolean allDevices;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_ids", columnDefinition = "jsonb", nullable = false)
    private List<String> deviceIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "telemetry_keys", columnDefinition = "jsonb", nullable = false)
    private List<String> keys = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_keys", columnDefinition = "jsonb", nullable = false)
    private List<String> attributeKeys = new ArrayList<>();

    @Column(name = "include_attributes", nullable = false)
    private boolean includeAttributes;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private DataExportFormat format;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 16)
    private DataExportPeriod period;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "time_of_day", nullable = false, length = 5)
    private String timeOfDay;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private DataExportMode mode;

    @Column(name = "full_lookback_days", nullable = false)
    private Integer fullLookbackDays;

    @Column(name = "next_run_ts")
    private Long nextRunTs;

    @Column(name = "last_run_ts")
    private Long lastRunTs;

    @Column(name = "last_success_ts")
    private Long lastSuccessTs;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", nullable = false, length = 16)
    private DataExportScheduleStatus lastStatus;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_time", nullable = false)
    private long createdTime;

    @Column(name = "updated_time", nullable = false)
    private long updatedTime;

    public DataExportScheduleEntity() {
    }

    public DataExportScheduleEntity(DataExportSchedule schedule) {
        id = schedule.getId();
        tenantId = schedule.getTenantId().getId();
        customerId = schedule.getCustomerId() != null && !schedule.getCustomerId().isNullUid()
                ? schedule.getCustomerId().getId() : null;
        userId = schedule.getUserId();
        enabled = schedule.isEnabled();
        allDevices = schedule.isAllDevices();
        deviceIds = copy(schedule.getDeviceIds());
        keys = copy(schedule.getKeys());
        attributeKeys = copy(schedule.getAttributeKeys());
        includeAttributes = schedule.isIncludeAttributes();
        format = schedule.getFormat();
        email = schedule.getEmail();
        period = schedule.getPeriod();
        dayOfWeek = schedule.getDayOfWeek();
        dayOfMonth = schedule.getDayOfMonth();
        timeOfDay = schedule.getTimeOfDay();
        timezone = schedule.getTimezone();
        mode = schedule.getMode();
        fullLookbackDays = schedule.getFullLookbackDays();
        nextRunTs = schedule.getNextRunTs();
        lastRunTs = schedule.getLastRunTs();
        lastSuccessTs = schedule.getLastSuccessTs();
        lastStatus = schedule.getLastStatus();
        lastError = schedule.getLastError();
        createdTime = schedule.getCreatedTime();
        updatedTime = schedule.getUpdatedTime();
    }

    public DataExportSchedule toData() {
        DataExportSchedule schedule = new DataExportSchedule();
        schedule.setId(id);
        schedule.setTenantId(TenantId.fromUUID(tenantId));
        schedule.setCustomerId(customerId != null ? new CustomerId(customerId) : null);
        schedule.setUserId(userId);
        schedule.setEnabled(enabled);
        schedule.setAllDevices(allDevices);
        schedule.setDeviceIds(copy(deviceIds));
        schedule.setKeys(copy(keys));
        schedule.setAttributeKeys(copy(attributeKeys));
        schedule.setIncludeAttributes(includeAttributes);
        schedule.setFormat(format);
        schedule.setEmail(email);
        schedule.setPeriod(period);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setDayOfMonth(dayOfMonth);
        schedule.setTimeOfDay(timeOfDay);
        schedule.setTimezone(timezone);
        schedule.setMode(mode);
        schedule.setFullLookbackDays(fullLookbackDays);
        schedule.setNextRunTs(nextRunTs);
        schedule.setLastRunTs(lastRunTs);
        schedule.setLastSuccessTs(lastSuccessTs);
        schedule.setLastStatus(lastStatus);
        schedule.setLastError(lastError);
        schedule.setCreatedTime(createdTime);
        schedule.setUpdatedTime(updatedTime);
        return schedule;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
