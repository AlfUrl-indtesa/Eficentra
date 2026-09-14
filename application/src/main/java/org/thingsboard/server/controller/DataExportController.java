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
package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.service.export.DataExportArtifact;
import org.thingsboard.server.service.export.DataExportService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.IOException;

@RestController
@RequestMapping("/api/data-export")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
public class DataExportController extends BaseController {

    private final DataExportService dataExportService;

    @PostMapping("/preview")
    public DataExportPreviewResponse preview(@RequestBody(required = false) DataExportPreviewRequest request)
            throws ThingsboardException {
        return dataExportService.preview(getCurrentUser(), request);
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestBody DataExportRequest request) throws Exception {
        DataExportArtifact artifact = dataExportService.export(getCurrentUser(), request);
        StreamingResponseBody body = output -> {
            try (artifact) {
                Files.copy(artifact.path(), output);
                output.flush();
            } catch (IOException e) {
                log.warn("Data export stream closed before completion: {}", artifact.fileName(), e);
                throw e;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(artifact.format().getMediaType()));
        headers.setContentLength(artifact.size());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(artifact.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore().getHeaderValue());
        headers.set("X-Content-Type-Options", "nosniff");

        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping("/schedule")
    public DataExportSchedule saveSchedule(@RequestBody DataExportScheduleRequest request)
            throws ThingsboardException {
        return dataExportService.saveSchedule(getCurrentUser(), request);
    }

    @GetMapping("/schedule")
    public DataExportSchedule getSchedule() throws ThingsboardException {
        return dataExportService.getSchedule(getCurrentUser());
    }
}
