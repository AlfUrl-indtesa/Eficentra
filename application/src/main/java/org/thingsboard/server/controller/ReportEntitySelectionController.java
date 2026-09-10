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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.report.ReportSelectableEntity;
import org.thingsboard.server.service.report.ReportEntitySelectionService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
public class ReportEntitySelectionController extends ReportBaseController {

    private final ReportEntitySelectionService reportEntitySelectionService;

    @GetMapping("/api/reports/selectable-entities")
    public PageData<ReportSelectableEntity> getSelectableEntities(
            @RequestParam(name = "entityType") EntityType entityType,
            @RequestParam(name = "customerId", required = false) UUID customerId,
            @RequestParam(name = "textSearch", required = false) String textSearch,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "50") int pageSize) throws Exception {

        reportPageRequest(page, pageSize);

        CustomerId targetCustomerId = customerId != null ? new CustomerId(customerId) : null;

        return reportEntitySelectionService.findSelectableEntities(
                getCurrentUser(),
                entityType,
                targetCustomerId,
                textSearch,
                page,
                pageSize);
    }

    @GetMapping("/api/reports/selectable-entity-keys")
    public List<String> getSelectableEntityKeys(
            @RequestParam(name = "entityType") EntityType entityType,
            @RequestParam(name = "entityId") UUID entityId) throws Exception {

        return reportEntitySelectionService.findSelectableEntityKeys(
                getCurrentUser(),
                EntityIdFactory.getByTypeAndUuid(entityType, entityId));
    }
}
