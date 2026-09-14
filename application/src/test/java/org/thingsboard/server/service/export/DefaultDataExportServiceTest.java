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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.export.DataExportFormat;
import org.thingsboard.server.common.data.export.DataExportMode;
import org.thingsboard.server.common.data.export.DataExportPeriod;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.common.data.export.DataExportScheduleStatus;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.export.DataExportScheduleDao;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDataExportServiceTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private AttributesService attributesService;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private DataExportScheduleDao scheduleDao;

    @TempDir
    private Path tempDir;

    private DefaultDataExportService service;
    private ObjectMapper objectMapper;
    private SecurityUser user;
    private Device device;

    @BeforeEach
    void setUp() {
        DataExportProperties properties = new DataExportProperties();
        properties.setTempDir(tempDir.toString());
        objectMapper = new ObjectMapper();
        service = new DefaultDataExportService(
                deviceService,
                attributesService,
                timeseriesService,
                scheduleDao,
                new DataExportScheduleCalculator(),
                properties,
                objectMapper);

        TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
        user = new SecurityUser(new UserId(UUID.randomUUID()));
        user.setTenantId(tenantId);
        user.setAuthority(Authority.TENANT_ADMIN);
        user.setEmail("exports@example.com");

        device = new Device(new DeviceId(UUID.randomUUID()));
        device.setTenantId(tenantId);
        device.setName("Compressor room");
    }

    @Test
    void protectsCsvCellsFromSpreadsheetFormulas() {
        assertEquals("'=HYPERLINK(\"https://example.com\")",
                DefaultDataExportService.safeSpreadsheetText("=HYPERLINK(\"https://example.com\")"));
        assertEquals("'  =1+1", DefaultDataExportService.safeSpreadsheetText("  =1+1"));
        assertEquals("normal", DefaultDataExportService.safeSpreadsheetText("normal"));
    }

    @Test
    void persistsCompleteScheduleOwnershipAndNextRun() throws Exception {
        when(deviceService.findDevicesByTenantIdAndIdsAsync(any(), any()))
                .thenReturn(Futures.immediateFuture(List.of(device)));
        when(scheduleDao.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(scheduleDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DataExportScheduleRequest request = request();
        DataExportSchedule saved = service.saveSchedule(user, request);

        assertNotNull(saved.getId());
        assertEquals(user.getTenantId(), saved.getTenantId());
        assertEquals(user.getId().getId(), saved.getUserId());
        assertEquals(DataExportScheduleStatus.NEVER_RUN, saved.getLastStatus());
        assertNotNull(saved.getNextRunTs());
        assertEquals(List.of(device.getId().getId().toString()), saved.getDeviceIds());
    }

    @Test
    void rejectsDeviceOutsideCurrentTenantScope() throws Exception {
        when(deviceService.findDevicesByTenantIdAndIdsAsync(any(), any()))
                .thenReturn(Futures.immediateFuture(List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveSchedule(user, request()));
    }

    @Test
    void preservesCustomerScopeAndNeverFallsBackToTenantWideLookup() throws Exception {
        CustomerId customerId = new CustomerId(UUID.randomUUID());
        user.setAuthority(Authority.CUSTOMER_USER);
        user.setCustomerId(customerId);
        when(deviceService.findDevicesByTenantIdCustomerIdAndIdsAsync(any(), any(), any()))
                .thenReturn(Futures.immediateFuture(List.of(device)));
        when(scheduleDao.findByTenantIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(scheduleDao.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DataExportSchedule saved = service.saveSchedule(user, request());

        assertEquals(customerId, saved.getCustomerId());
        verify(deviceService).findDevicesByTenantIdCustomerIdAndIdsAsync(any(), any(), any());
        verify(deviceService, never()).findDevicesByTenantIdAndIdsAsync(any(), any());
    }

    @Test
    void createsCsvJsonAndZipArtifactsAndDeletesTemporaryFiles() throws Exception {
        when(deviceService.findDevicesByTenantIdAndIdsAsync(any(), any()))
                .thenReturn(Futures.immediateFuture(List.of(device)));
        when(timeseriesService.findAll(any(), any(), any())).thenReturn(Futures.immediateFuture(List.of(
                new BasicTsKvEntry(1_700_000_000_000L,
                        new StringDataEntry("temperature", "=1+1")))));

        verifyArtifact(DataExportFormat.CSV, "text/csv", "deviceId", "'=1+1");
        verifyArtifact(DataExportFormat.JSON, "application/json", "eficentra-data-export/v1", "=1+1");
        verifyZipArtifact();
    }

    private void verifyArtifact(DataExportFormat format, String mediaType, String... expectedText) throws Exception {
        Path artifactPath;
        try (DataExportArtifact artifact = service.export(user, exportRequest(format))) {
            artifactPath = artifact.path();
            assertTrue(Files.exists(artifactPath));
            assertEquals(1, artifact.rowCount());
            assertEquals(mediaType, artifact.format().getMediaType());
            String content = Files.readString(artifactPath, StandardCharsets.UTF_8);
            for (String value : expectedText) {
                assertTrue(content.contains(value));
            }
        }
        assertFalse(Files.exists(artifactPath));
    }

    private void verifyZipArtifact() throws Exception {
        Path artifactPath;
        try (DataExportArtifact artifact = service.export(user, exportRequest(DataExportFormat.ZIP))) {
            artifactPath = artifact.path();
            assertEquals(1, artifact.rowCount());
            var entries = readZip(artifactPath);
            assertTrue(entries.get("data.csv").contains("'=1+1"));
            assertEquals(1, objectMapper.readTree(entries.get("manifest.json")).get("rowCount").asLong());
        }
        assertFalse(Files.exists(artifactPath));
    }

    private java.util.Map<String, String> readZip(Path path) throws IOException {
        java.util.Map<String, String> entries = new java.util.HashMap<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private DataExportRequest exportRequest(DataExportFormat format) {
        DataExportRequest request = new DataExportRequest();
        request.setDeviceIds(List.of(device.getId().getId().toString()));
        request.setKeys(List.of("temperature"));
        request.setIncludeAttributes(false);
        request.setStartTs(1_699_999_999_000L);
        request.setEndTs(1_700_000_001_000L);
        request.setFormat(format);
        return request;
    }

    private DataExportScheduleRequest request() {
        DataExportScheduleRequest request = new DataExportScheduleRequest();
        request.setEnabled(true);
        request.setDeviceIds(List.of(device.getId().getId().toString()));
        request.setEmail("exports@example.com");
        request.setFormat(DataExportFormat.ZIP);
        request.setPeriod(DataExportPeriod.WEEKLY);
        request.setDayOfWeek(5);
        request.setDayOfMonth(1);
        request.setTimeOfDay("08:00");
        request.setTimezone("America/Monterrey");
        request.setMode(DataExportMode.INCREMENTAL);
        request.setFullLookbackDays(30);
        return request;
    }
}
