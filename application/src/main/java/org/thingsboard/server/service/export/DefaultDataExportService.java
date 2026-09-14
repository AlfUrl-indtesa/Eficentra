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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.export.DataExportFormat;
import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.common.data.export.DataExportScheduleStatus;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.export.DataExportScheduleDao;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.security.model.SecurityUser;

import jakarta.mail.internet.InternetAddress;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class DefaultDataExportService implements DataExportService {

    private static final String[] CSV_HEADERS = {
            "deviceId", "deviceName", "recordType", "scope", "key", "ts", "value", "valueType"
    };
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.of("UTC"));

    private final DeviceService deviceService;
    private final AttributesService attributesService;
    private final TimeseriesService timeseriesService;
    private final DataExportScheduleDao scheduleDao;
    private final DataExportScheduleCalculator scheduleCalculator;
    private final DataExportProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public DataExportPreviewResponse preview(SecurityUser user, DataExportPreviewRequest request) {
        ExportScope scope = scopeOf(user);
        DataExportPreviewRequest effective = request != null ? request : new DataExportPreviewRequest();
        DevicePage devicePage = loadAccessibleDevicePage(scope);

        DataExportPreviewResponse response = new DataExportPreviewResponse();
        response.setDevices(devicePage.devices().stream().map(this::toDeviceInfo).toList());
        response.setDevicesTruncated(devicePage.truncated());
        response.setSuggestedEmail(user.getEmail());
        long now = System.currentTimeMillis();
        response.setDefaultEndTs(now);
        response.setDefaultStartTs(now - TimeUnit.DAYS.toMillis(properties.getDefaultLookbackDays()));
        response.setMaxDevices(properties.getMaxDevices());
        response.setMaxKeys(properties.getMaxKeys());
        response.setMaxRows(properties.getMaxRows());
        response.setMaxScheduleAttachmentBytes(properties.getMaxScheduleAttachmentBytes());
        response.setMaxRangeDays(properties.getMaxRangeDays());

        List<String> ids = normalizeIds(effective.getDeviceIds(), true);
        if (!ids.isEmpty()) {
            List<Device> selected = loadDevicesByIds(scope, ids);
            List<String> telemetryKeys = discoverTelemetryKeys(scope.tenantId(), selected);
            response.setKeys(limitForPreview(telemetryKeys));
            response.setKeysTruncated(telemetryKeys.size() > properties.getMaxKeys());
            if (effective.isIncludeAttributes()) {
                List<String> attributeKeys = discoverAttributeKeys(scope.tenantId(), selected);
                response.setAttributeKeys(limitForPreview(attributeKeys));
                response.setAttributeKeysTruncated(attributeKeys.size() > properties.getMaxKeys());
            }
        }
        return response;
    }

    @Override
    public DataExportArtifact export(SecurityUser user, DataExportRequest request) throws Exception {
        return export(scopeOf(user), request);
    }

    @Override
    public DataExportArtifact exportScheduled(DataExportSchedule schedule, DataExportRequest request) throws Exception {
        if (schedule == null || schedule.getTenantId() == null || schedule.getUserId() == null) {
            throw new IllegalArgumentException("Invalid scheduled export scope");
        }
        return export(new ExportScope(schedule.getTenantId(), schedule.getCustomerId(), schedule.getUserId()), request);
    }

    @Override
    public DataExportSchedule saveSchedule(SecurityUser user, DataExportScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Schedule request is required");
        }
        ExportScope scope = scopeOf(user);
        validateSchedule(request);

        List<String> deviceIds = normalizeIds(
                request.getDeviceIds(), request.isAllDevices() || !request.isEnabled());
        if (request.isEnabled() && !request.isAllDevices()) {
            loadDevicesByIds(scope, deviceIds);
        }

        long now = System.currentTimeMillis();
        DataExportSchedule schedule = scheduleDao
                .findByTenantIdAndUserId(scope.tenantId(), scope.userId())
                .orElseGet(DataExportSchedule::new);

        if (schedule.getId() == null) {
            schedule.setId(UUID.randomUUID());
            schedule.setCreatedTime(now);
            schedule.setLastStatus(DataExportScheduleStatus.NEVER_RUN);
        }
        schedule.setTenantId(scope.tenantId());
        schedule.setCustomerId(scope.customerId());
        schedule.setUserId(scope.userId());
        schedule.setEnabled(request.isEnabled());
        schedule.setAllDevices(request.isAllDevices());
        schedule.setDeviceIds(deviceIds);
        schedule.setKeys(normalizeKeys(request.getKeys()));
        schedule.setAttributeKeys(normalizeKeys(request.getAttributeKeys()));
        schedule.setIncludeAttributes(request.isIncludeAttributes());
        schedule.setFormat(request.getFormat() != null ? request.getFormat() : DataExportFormat.ZIP);
        schedule.setEmail(trim(request.getEmail()));
        schedule.setPeriod(request.getPeriod());
        schedule.setDayOfWeek(request.getDayOfWeek());
        schedule.setDayOfMonth(request.getDayOfMonth());
        schedule.setTimeOfDay(request.getTimeOfDay());
        schedule.setTimezone(request.getTimezone());
        schedule.setMode(request.getMode());
        schedule.setFullLookbackDays(request.getFullLookbackDays());
        schedule.setUpdatedTime(now);
        schedule.setLastError(null);
        schedule.setNextRunTs(request.isEnabled() ? scheduleCalculator.nextRun(schedule, now) : null);

        return sanitizeSchedule(scheduleDao.save(schedule));
    }

    @Override
    public DataExportSchedule getSchedule(SecurityUser user) {
        ExportScope scope = scopeOf(user);
        return scheduleDao.findByTenantIdAndUserId(scope.tenantId(), scope.userId())
                .map(this::sanitizeSchedule)
                .orElse(null);
    }

    private DataExportArtifact export(ExportScope scope, DataExportRequest request) throws Exception {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Data export is disabled");
        }
        ValidatedExport validated = validateAndResolve(scope, request);
        DataExportFormat format = validated.request().getFormat();
        Path tempDir = Path.of(properties.getTempDir()).toAbsolutePath().normalize();
        boolean createTempDir = Files.notExists(tempDir);
        Files.createDirectories(tempDir);
        if (createTempDir) {
            restrictPermissions(tempDir, "rwx------");
        }
        Path output = Files.createTempFile(tempDir, "eficentra-export-", "." + format.getExtension());
        restrictPermissions(output, "rw-------");

        try {
            long rows;
            try (OutputStream fileOutput = Files.newOutputStream(output);
                 LimitedOutputStream limited = new LimitedOutputStream(fileOutput, properties.getMaxOutputBytes())) {
                rows = switch (format) {
                    case CSV -> writeCsv(limited, scope, validated);
                    case JSON -> writeJson(limited, scope, validated);
                    case ZIP -> writeZip(limited, scope, validated);
                };
            }
            long size = Files.size(output);
            String fileName = "eficentra-data-" + FILE_TIMESTAMP.format(java.time.Instant.now())
                    + "." + format.getExtension();
            return new DataExportArtifact(output, fileName, format, rows, size);
        } catch (OutputLimitException e) {
            Files.deleteIfExists(output);
            throw new IllegalArgumentException("The export exceeds the configured file-size limit", e);
        } catch (Exception e) {
            Files.deleteIfExists(output);
            throw e;
        }
    }

    private ValidatedExport validateAndResolve(ExportScope scope, DataExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Export request is required");
        }
        DataExportFormat format = request.getFormat() != null ? request.getFormat() : DataExportFormat.CSV;
        request.setFormat(format);

        long endTs = request.getEndTs() != null ? request.getEndTs() : System.currentTimeMillis();
        if (request.getStartTs() == null) {
            throw new IllegalArgumentException("startTs is required");
        }
        long startTs = request.getStartTs();
        if (startTs < 0 || endTs <= 0 || startTs > endTs) {
            throw new IllegalArgumentException("Invalid export time range");
        }
        long maxRange = TimeUnit.DAYS.toMillis(properties.getMaxRangeDays());
        if (endTs - startTs > maxRange) {
            throw new IllegalArgumentException("The export time range exceeds the configured limit");
        }
        request.setEndTs(endTs);

        List<Device> devices;
        if (request.isAllDevices()) {
            devices = loadAllAccessibleDevices(scope);
        } else {
            devices = loadDevicesByIds(scope, normalizeIds(request.getDeviceIds(), false));
        }
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("At least one accessible device is required");
        }

        List<String> keys = normalizeKeys(request.getKeys());
        if (keys.isEmpty()) {
            keys = discoverTelemetryKeys(scope.tenantId(), devices);
            enforceKeyLimit(keys);
        }
        List<String> attributeKeys = normalizeKeys(request.getAttributeKeys());
        if (keys.isEmpty() && !request.isIncludeAttributes()) {
            throw new IllegalArgumentException("Select telemetry keys or include attributes");
        }
        request.setKeys(keys);
        request.setAttributeKeys(attributeKeys);
        return new ValidatedExport(request, devices);
    }

    private long writeCsv(OutputStream output, ExportScope scope, ValidatedExport validated) throws Exception {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .setQuoteMode(QuoteMode.MINIMAL)
                .setRecordSeparator("\r\n")
                .build();
        CSVPrinter printer = new CSVPrinter(writer, csvFormat);
        long rows = produceRows(scope, validated, row -> printer.printRecord(
                safeSpreadsheetText(row.deviceId()),
                safeSpreadsheetText(row.deviceName()),
                row.recordType(),
                row.scope(),
                safeSpreadsheetText(row.key()),
                row.ts(),
                csvValue(row.value()),
                row.valueType()));
        printer.flush();
        return rows;
    }

    private long writeJson(OutputStream output, ExportScope scope, ValidatedExport validated) throws Exception {
        JsonGenerator generator = objectMapper.getFactory().createGenerator(output);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartObject();
        generator.writeStringField("schema", "eficentra-data-export/v1");
        generator.writeNumberField("generatedAt", System.currentTimeMillis());
        generator.writeNumberField("startTs", validated.request().getStartTs());
        generator.writeNumberField("endTs", validated.request().getEndTs());
        generator.writeArrayFieldStart("rows");
        long rows = produceRows(scope, validated, row -> writeJsonRow(generator, row));
        generator.writeEndArray();
        generator.writeNumberField("rowCount", rows);
        generator.writeEndObject();
        generator.flush();
        return rows;
    }

    private long writeZip(OutputStream output, ExportScope scope, ValidatedExport validated) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry("data.csv"));
        long rows = writeCsv(zip, scope, validated);
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("manifest.json"));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartObject();
        generator.writeStringField("schema", "eficentra-data-export/v1");
        generator.writeNumberField("generatedAt", System.currentTimeMillis());
        generator.writeNumberField("startTs", validated.request().getStartTs());
        generator.writeNumberField("endTs", validated.request().getEndTs());
        generator.writeNumberField("rowCount", rows);
        generator.writeNumberField("deviceCount", validated.devices().size());
        generator.writeObjectField("telemetryKeys", validated.request().getKeys());
        generator.writeBooleanField("includeAttributes", validated.request().isIncludeAttributes());
        generator.writeObjectField("attributeKeys", validated.request().getAttributeKeys());
        generator.writeEndObject();
        generator.flush();
        zip.closeEntry();
        zip.finish();
        return rows;
    }

    private long produceRows(ExportScope scope, ValidatedExport validated, RowSink sink) throws Exception {
        long count = 0;
        for (Device device : validated.devices()) {
            for (String key : validated.request().getKeys()) {
                long cursor = validated.request().getStartTs();
                long end = validated.request().getEndTs();
                while (cursor <= end) {
                    BaseReadTsKvQuery query = new BaseReadTsKvQuery(
                            key, cursor, end, properties.getHistoryBatchSize(), "ASC");
                    List<TsKvEntry> batch = timeseriesService.findAll(
                                    scope.tenantId(), device.getId(), List.of(query))
                            .get(properties.getQueryTimeoutSeconds(), TimeUnit.SECONDS);
                    if (batch == null || batch.isEmpty()) {
                        break;
                    }
                    batch = batch.stream()
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparingLong(TsKvEntry::getTs))
                            .toList();
                    long lastTs = -1;
                    for (TsKvEntry entry : batch) {
                        count = accept(sink, count, row(device, "TIMESERIES", "", entry.getKey(), entry.getTs(), entry));
                        lastTs = Math.max(lastTs, entry.getTs());
                    }
                    if (lastTs < cursor || batch.size() < properties.getHistoryBatchSize() || lastTs == Long.MAX_VALUE) {
                        break;
                    }
                    cursor = lastTs + 1;
                }
            }

            if (validated.request().isIncludeAttributes()) {
                Set<String> requested = new LinkedHashSet<>(validated.request().getAttributeKeys());
                for (AttributeScope attributeScope : AttributeScope.values()) {
                    List<AttributeKvEntry> attributes = attributesService
                            .findAll(scope.tenantId(), device.getId(), attributeScope)
                            .get(properties.getQueryTimeoutSeconds(), TimeUnit.SECONDS);
                    if (attributes == null) {
                        continue;
                    }
                    for (AttributeKvEntry attribute : attributes.stream()
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(AttributeKvEntry::getKey))
                            .toList()) {
                        if (!requested.isEmpty() && !requested.contains(attribute.getKey())) {
                            continue;
                        }
                        count = accept(sink, count, row(device, "ATTRIBUTE", attributeScope.name(),
                                attribute.getKey(), attribute.getLastUpdateTs(), attribute));
                    }
                }
            }
        }
        return count;
    }

    private long accept(RowSink sink, long current, ExportRow row) throws Exception {
        long next = current + 1;
        if (next > properties.getMaxRows()) {
            throw new IllegalArgumentException("The export exceeds the configured row limit");
        }
        sink.accept(row);
        return next;
    }

    private ExportRow row(Device device, String recordType, String scope, String key, long ts, KvEntry entry) {
        return new ExportRow(
                device.getId().getId().toString(),
                device.getName(),
                recordType,
                scope,
                key,
                ts,
                entry.getValue(),
                entry.getDataType().name());
    }

    private void writeJsonRow(JsonGenerator generator, ExportRow row) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("deviceId", row.deviceId());
        generator.writeStringField("deviceName", row.deviceName());
        generator.writeStringField("recordType", row.recordType());
        generator.writeStringField("scope", row.scope());
        generator.writeStringField("key", row.key());
        generator.writeNumberField("ts", row.ts());
        generator.writeObjectField("value", row.value());
        generator.writeStringField("valueType", row.valueType());
        generator.writeEndObject();
    }

    private DevicePage loadAccessibleDevicePage(ExportScope scope) {
        List<Device> result = new ArrayList<>();
        int page = 0;
        boolean truncated = false;
        while (result.size() <= properties.getMaxDevices()) {
            PageData<Device> pageData = hasCustomerScope(scope)
                    ? deviceService.findDevicesByTenantIdAndCustomerId(scope.tenantId(), scope.customerId(),
                    new PageLink(properties.getDevicePageSize(), page))
                    : deviceService.findDevicesByTenantId(scope.tenantId(),
                    new PageLink(properties.getDevicePageSize(), page));
            if (pageData == null || pageData.getData() == null) {
                break;
            }
            result.addAll(pageData.getData().stream().filter(Objects::nonNull).toList());
            if (!pageData.hasNext()) {
                break;
            }
            page++;
        }
        if (result.size() > properties.getMaxDevices()) {
            result = new ArrayList<>(result.subList(0, properties.getMaxDevices()));
            truncated = true;
        }
        result.sort(Comparator.comparing(device -> trim(device.getName()).toLowerCase(Locale.ROOT)));
        return new DevicePage(result, truncated);
    }

    private List<Device> loadAllAccessibleDevices(ExportScope scope) {
        DevicePage page = loadAccessibleDevicePage(scope);
        if (page.truncated()) {
            throw new IllegalArgumentException("The accessible device count exceeds the configured export limit");
        }
        return page.devices();
    }

    private List<Device> loadDevicesByIds(ExportScope scope, List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        try {
            List<DeviceId> deviceIds = ids.stream()
                    .map(UUID::fromString)
                    .map(DeviceId::new)
                    .toList();
            List<Device> devices = hasCustomerScope(scope)
                    ? deviceService.findDevicesByTenantIdCustomerIdAndIdsAsync(
                    scope.tenantId(), scope.customerId(), deviceIds)
                    .get(properties.getQueryTimeoutSeconds(), TimeUnit.SECONDS)
                    : deviceService.findDevicesByTenantIdAndIdsAsync(scope.tenantId(), deviceIds)
                    .get(properties.getQueryTimeoutSeconds(), TimeUnit.SECONDS);
            Map<String, Device> byId = new LinkedHashMap<>();
            if (devices != null) {
                devices.stream().filter(Objects::nonNull).forEach(device ->
                        byId.put(device.getId().getId().toString(), device));
            }
            if (byId.size() != ids.size()) {
                throw new IllegalArgumentException("One or more devices are not accessible");
            }
            return ids.stream().map(byId::get).toList();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to validate selected devices", e);
        }
    }

    private List<String> discoverTelemetryKeys(TenantId tenantId, List<Device> devices) {
        try {
            List<String> discovered = timeseriesService.findAllKeysByEntityIds(
                    tenantId, entityIds(devices));
            Set<String> keys = new TreeSet<>();
            if (discovered != null) {
                discovered.stream().filter(this::validKey).forEach(keys::add);
            }
            return new ArrayList<>(keys);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to discover telemetry keys", e);
        }
    }

    private List<String> discoverAttributeKeys(TenantId tenantId, List<Device> devices) {
        Set<String> keys = new TreeSet<>();
        try {
            List<EntityId> entityIds = entityIds(devices);
            for (AttributeScope scope : AttributeScope.values()) {
                List<String> discovered = attributesService.findAllKeysByEntityIdsAndScope(
                        tenantId, entityIds, scope);
                if (discovered != null) {
                    discovered.stream().filter(this::validKey).forEach(keys::add);
                }
            }
            return new ArrayList<>(keys);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to discover attribute keys", e);
        }
    }

    private List<EntityId> entityIds(List<Device> devices) {
        return devices.stream().map(device -> (EntityId) device.getId()).toList();
    }

    private List<String> normalizeIds(Collection<String> values, boolean allowEmpty) {
        List<String> normalized = normalize(values);
        if (!allowEmpty && normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one device must be selected");
        }
        if (normalized.size() > properties.getMaxDevices()) {
            throw new IllegalArgumentException("The selected device count exceeds the configured limit");
        }
        for (String value : normalized) {
            UUID.fromString(value);
        }
        return normalized;
    }

    private List<String> normalizeKeys(Collection<String> values) {
        List<String> normalized = normalize(values);
        normalized.forEach(key -> {
            if (!validKey(key)) {
                throw new IllegalArgumentException("Invalid telemetry or attribute key");
            }
        });
        enforceKeyLimit(normalized);
        return normalized;
    }

    private List<String> limitForPreview(List<String> values) {
        if (values.size() <= properties.getMaxKeys()) {
            return values;
        }
        return new ArrayList<>(values.subList(0, properties.getMaxKeys()));
    }

    private List<String> normalize(Collection<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 255
                && key.chars().noneMatch(Character::isISOControl);
    }

    private void enforceKeyLimit(Collection<String> keys) {
        if (keys.size() > properties.getMaxKeys()) {
            throw new IllegalArgumentException("The selected key count exceeds the configured limit");
        }
    }

    private void validateSchedule(DataExportScheduleRequest request) {
        if (request.getPeriod() == null || request.getMode() == null) {
            throw new IllegalArgumentException("Schedule period and mode are required");
        }
        if (request.getFormat() == null) {
            request.setFormat(DataExportFormat.ZIP);
        }
        if (request.getTimeOfDay() == null || !request.getTimeOfDay().matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("timeOfDay must use HH:mm");
        }
        try {
            LocalTime.parse(request.getTimeOfDay());
        } catch (Exception e) {
            throw new IllegalArgumentException("timeOfDay must use HH:mm", e);
        }
        if (request.getTimezone() == null || request.getTimezone().length() > 64) {
            throw new IllegalArgumentException("Invalid schedule timezone");
        }
        try {
            ZoneId.of(request.getTimezone());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid schedule timezone", e);
        }
        if (request.getDayOfWeek() == null || request.getDayOfWeek() < 1 || request.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
        }
        if (request.getDayOfMonth() == null || request.getDayOfMonth() < 1 || request.getDayOfMonth() > 28) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 28");
        }
        if (request.getFullLookbackDays() == null || request.getFullLookbackDays() < 1
                || request.getFullLookbackDays() > properties.getMaxRangeDays()) {
            throw new IllegalArgumentException("Invalid full export lookback");
        }
        if (request.isEnabled()) {
            validateEmail(request.getEmail());
        }
    }

    private void validateEmail(String value) {
        String email = trim(value);
        if (email.length() > 320) {
            throw new IllegalArgumentException("Invalid email address");
        }
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            if (!email.equals(address.getAddress()) || !email.contains("@")) {
                throw new IllegalArgumentException("Invalid email address");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid email address", e);
        }
    }

    private ExportScope scopeOf(SecurityUser user) {
        if (user == null || user.getTenantId() == null || user.getId() == null
                || (!Authority.TENANT_ADMIN.equals(user.getAuthority())
                && !Authority.CUSTOMER_USER.equals(user.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Data export access denied");
        }
        CustomerId customerId = Authority.CUSTOMER_USER.equals(user.getAuthority())
                ? user.getCustomerId() : null;
        if (Authority.CUSTOMER_USER.equals(user.getAuthority())
                && (customerId == null || customerId.isNullUid())) {
            throw new org.springframework.security.access.AccessDeniedException("Customer scope is required");
        }
        return new ExportScope(user.getTenantId(), customerId, user.getId().getId());
    }

    private boolean hasCustomerScope(ExportScope scope) {
        return scope.customerId() != null && !scope.customerId().isNullUid();
    }

    private DataExportPreviewResponse.ExportableDeviceInfo toDeviceInfo(Device device) {
        return new DataExportPreviewResponse.ExportableDeviceInfo(
                device.getId().getId().toString(), device.getName(), device.getType(), device.getLabel());
    }

    private DataExportSchedule sanitizeSchedule(DataExportSchedule schedule) {
        if (schedule.getLastError() != null) {
            schedule.setLastError("The last scheduled export could not be delivered.");
        }
        return schedule;
    }

    private Object csvValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return safeSpreadsheetText(String.valueOf(value));
    }

    static String safeSpreadsheetText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char rawFirst = value.charAt(0);
        if (rawFirst == '\t' || rawFirst == '\r' || rawFirst == '\n') {
            return "'" + value;
        }
        int firstVisible = 0;
        while (firstVisible < value.length() && Character.isWhitespace(value.charAt(firstVisible))) {
            firstVisible++;
        }
        char first = firstVisible < value.length() ? value.charAt(firstVisible) : value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void restrictPermissions(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystems use their platform defaults.
        }
    }

    private record ExportScope(TenantId tenantId, CustomerId customerId, UUID userId) {
    }

    private record DevicePage(List<Device> devices, boolean truncated) {
    }

    private record ValidatedExport(DataExportRequest request, List<Device> devices) {
    }

    private record ExportRow(String deviceId, String deviceName, String recordType, String scope,
                             String key, long ts, Object value, String valueType) {
    }

    @FunctionalInterface
    private interface RowSink {
        void accept(ExportRow row) throws Exception;
    }

    private static final class LimitedOutputStream extends FilterOutputStream {

        private final long limit;
        private long count;

        private LimitedOutputStream(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            check(1);
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            check(length);
            out.write(values, offset, length);
            count += length;
        }

        private void check(int length) throws OutputLimitException {
            if (length < 0 || count > limit - length) {
                throw new OutputLimitException();
            }
        }
    }

    private static final class OutputLimitException extends IOException {
    }
}
