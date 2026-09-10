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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.ReportExecutionId;
import org.thingsboard.server.common.data.id.ReportTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.report.GenerateReportRequest;
import org.thingsboard.server.common.data.report.ReportErrorCode;
import org.thingsboard.server.common.data.report.ReportExecution;
import org.thingsboard.server.common.data.report.ReportExecutionStatus;
import org.thingsboard.server.common.data.report.ReportTemplate;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.report.ReportExecutionDao;
import org.thingsboard.server.service.report.DefaultReportExecutionService;
import org.thingsboard.server.service.report.ReportAccessService;
import org.thingsboard.server.service.report.ReportEntitySecurityService;
import org.thingsboard.server.service.report.ReportEntitySelectionService;
import org.thingsboard.server.service.report.ReportExecutionDispatcher;
import org.thingsboard.server.service.report.ReportExecutionService;
import org.thingsboard.server.service.report.ReportRequestBuilderService;
import org.thingsboard.server.service.report.ReportServiceException;
import org.thingsboard.server.service.report.ReportStorageService;
import org.thingsboard.server.service.report.ReportTemplateService;
import org.thingsboard.server.service.report.ReportValidationService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportRestApiTest {

    private final TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
    private final CustomerId customerId = new CustomerId(UUID.randomUUID());
    private final UUID userId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();

    private ReportTemplateService templates;
    private ReportExecutionService executions;
    private ReportStorageService storage;
    private ReportEntitySecurityService entitySecurity;
    private ReportEntitySelectionService selection;
    private ReportExecutionController executionController;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        templates = mock(ReportTemplateService.class);
        executions = mock(ReportExecutionService.class);
        storage = mock(ReportStorageService.class);
        entitySecurity = mock(ReportEntitySecurityService.class);
        selection = mock(ReportEntitySelectionService.class);
        ReportAccessService access = new ReportAccessService();

        ReportTemplateController templateController = secured(new ReportTemplateController(
                templates, executions, access, entitySecurity));
        executionController = secured(new ReportExecutionController(executions, storage, access));
        ReportEntitySelectionController selectionController = secured(new ReportEntitySelectionController(selection));
        mvc = MockMvcBuilders.standaloneSetup(templateController, executionController, selectionController).build();
        authenticate(Authority.CUSTOMER_USER);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestsAreRejectedBeforeServices() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/report-templates")).andExpect(status().isUnauthorized());
        verifyNoInteractions(templates, executions, storage, selection, entitySecurity);
    }

    @Test
    void systemAdminCannotUseTenantReportEndpoints() throws Exception {
        authenticate(Authority.SYS_ADMIN);
        mvc.perform(get("/api/report-templates")).andExpect(status().isForbidden());
        mvc.perform(get("/api/report-executions")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/selectable-entities").param("entityType", "DEVICE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(templates, executions, storage, selection, entitySecurity);
    }

    @Test
    void customerCannotCreateOrDeleteTemplates() throws Exception {
        mvc.perform(post("/api/report-templates").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/report-templates/{id}", templateId)).andExpect(status().isForbidden());
        verifyNoInteractions(templates, executions, entitySecurity);
    }

    @Test
    void tenantAdminValidatesBeforeSavingTemplate() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        when(templates.save(eq(tenantId), eq(userId), any())).thenReturn(template());
        mvc.perform(post("/api/report-templates").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        InOrder order = inOrder(entitySecurity, templates);
        order.verify(entitySecurity).validateTemplateDefinition(eq(tenantId), any(ReportTemplate.class));
        order.verify(templates).save(eq(tenantId), eq(userId), any(ReportTemplate.class));
    }

    @Test
    void tenantAdminCanDeleteTemplateInsideAuthenticatedTenant() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        mvc.perform(delete("/api/report-templates/{id}", templateId)).andExpect(status().isOk());
        verify(templates).delete(tenantId, templateId);
    }

    @Test
    void rejectedDefinitionIsNeverSaved() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        doThrow(new AccessDeniedException("private detail"))
                .when(entitySecurity).validateTemplateDefinition(eq(tenantId), any());
        mvc.perform(post("/api/report-templates").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(content().string(not(containsString("private detail"))));
        verifyNoInteractions(templates);
    }

    @Test
    void customerTemplateListUsesAuthenticatedCustomer() throws Exception {
        when(templates.findByTenantIdAndCustomerId(eq(tenantId), eq(customerId), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(2, Pageable.class)));
        mvc.perform(get("/api/report-templates").param("page", "2").param("pageSize", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(5));
        verify(templates).findByTenantIdAndCustomerId(tenantId, customerId, PageRequest.of(2, 5));
        verify(templates, never()).findByTenantId(any(), any());
    }

    @Test
    void tenantAdminCanListTemplates() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        when(templates.findByTenantId(eq(tenantId), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1, Pageable.class)));
        mvc.perform(get("/api/report-templates")).andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(10));
        verify(templates).findByTenantId(tenantId, PageRequest.of(0, 10));
    }

    @Test
    void crossTenantTemplateIsRejectedEvenForTenantAdmin() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        ReportTemplate foreign = template();
        foreign.setTenantId(TenantId.fromUUID(UUID.randomUUID()));
        when(templates.findById(tenantId, templateId)).thenReturn(foreign);
        mvc.perform(get("/api/report-templates/{id}", templateId)).andExpect(status().isForbidden());
    }

    @Test
    void customerCanReadOwnTemplate() throws Exception {
        when(templates.findById(tenantId, templateId)).thenReturn(template());
        mvc.perform(get("/api/report-templates/{id}", templateId)).andExpect(status().isOk());
    }

    @Test
    void crossCustomerGenerationIsRejectedBeforeQueueing() throws Exception {
        ReportTemplate foreign = template();
        foreign.setCustomerId(new CustomerId(UUID.randomUUID()));
        when(templates.findById(tenantId, templateId)).thenReturn(foreign);
        mvc.perform(post("/api/report-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(executions, entitySecurity);
    }

    @Test
    void rejectedRuntimeEntityOverrideIsNeverQueued() throws Exception {
        ReportTemplate template = template();
        when(templates.findById(tenantId, templateId)).thenReturn(template);
        doThrow(new AccessDeniedException("foreign device"))
                .when(entitySecurity).validateUserGenerationAccess(any(), same(template), any());
        mvc.perform(post("/api/report-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(executions);
    }

    @Test
    void authorizedGenerationValidatesScopeBeforeDispatchAndReturnsPendingId() throws Exception {
        ReportTemplate template = template();
        ReportExecution execution = execution();
        execution.setStatus(ReportExecutionStatus.PENDING);
        when(templates.findById(tenantId, templateId)).thenReturn(template);
        when(executions.generate(eq(tenantId), eq(userId), eq(templateId), any())).thenReturn(execution);
        mvc.perform(post("/api/report-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(content().string(containsString(executionId.toString())));
        InOrder order = inOrder(entitySecurity, executions);
        order.verify(entitySecurity).validateUserGenerationAccess(any(SecurityUser.class), same(template), any(GenerateReportRequest.class));
        order.verify(executions).generate(eq(tenantId), eq(userId), eq(templateId), any(GenerateReportRequest.class));
    }

    @Test
    void crossCustomerDownloadIsDeniedBeforeStorage() throws Exception {
        ReportExecution foreign = execution();
        foreign.setCustomerId(new CustomerId(UUID.randomUUID()));
        when(executions.findById(tenantId, executionId)).thenReturn(foreign);
        mvc.perform(get("/api/report-executions/{id}/download", executionId)).andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void sameCustomerCannotDownloadAnotherUsersExecution() throws Exception {
        ReportExecution foreign = execution();
        foreign.setRequestedBy(UUID.randomUUID());
        when(executions.findById(tenantId, executionId)).thenReturn(foreign);
        mvc.perform(get("/api/report-executions/{id}/download", executionId)).andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void tenantAdminCannotDownloadAcrossTenants() throws Exception {
        authenticate(Authority.TENANT_ADMIN);
        ReportExecution foreign = execution();
        foreign.setTenantId(TenantId.fromUUID(UUID.randomUUID()));
        when(executions.findById(tenantId, executionId)).thenReturn(foreign);
        mvc.perform(get("/api/report-executions/{id}/download", executionId)).andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void unfinishedAndFailedExecutionsCannotLoadFiles() throws Exception {
        ReportExecution execution = execution();
        when(executions.findById(tenantId, executionId)).thenReturn(execution);
        for (ReportExecutionStatus state : List.of(ReportExecutionStatus.PENDING, ReportExecutionStatus.RUNNING,
                ReportExecutionStatus.FAILED, ReportExecutionStatus.CANCELLED)) {
            execution.setStatus(state);
            mvc.perform(get("/api/report-executions/{id}/download", executionId)).andExpect(status().isConflict());
        }
        verifyNoInteractions(storage);
    }

    @Test
    void authorizedDownloadReturnsAttachmentWithNoCacheAndNoSniff() throws Exception {
        ReportExecution execution = execution();
        byte[] bytes = "%PDF-1.7\nexample".getBytes(StandardCharsets.UTF_8);
        execution.setFileName("report.pdf");
        execution.setMimeType("application/pdf");
        when(executions.findById(tenantId, executionId)).thenReturn(execution);
        when(storage.loadFile(tenantId, execution)).thenReturn(new ByteArrayResource(bytes));
        mvc.perform(get("/api/report-executions/{id}/download", executionId))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("report.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().longValue("Content-Length", bytes.length));
    }

    @Test
    void executionResponseHidesInternalValuesWithoutMutatingServiceObject() throws Exception {
        ReportExecution execution = execution();
        execution.setFilePath("/private/report.pdf");
        execution.setExternalFileId("secret-file-id");
        execution.setChecksum("private-checksum");
        execution.setPayloadSnapshot(mapper.createObjectNode().put("secret", "private-payload"));
        execution.setErrorMessage("/private/internal-error");
        when(executions.findById(tenantId, executionId)).thenReturn(execution);
        mvc.perform(get("/api/report-executions/{id}", executionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filePath").doesNotExist())
                .andExpect(jsonPath("$.externalFileId").doesNotExist())
                .andExpect(jsonPath("$.checksum").doesNotExist())
                .andExpect(jsonPath("$.payloadSnapshot").doesNotExist())
                .andExpect(content().string(not(containsString("/private/"))));
        assertEquals("/private/report.pdf", execution.getFilePath());
        assertEquals("secret-file-id", execution.getExternalFileId());
        assertEquals("private-checksum", execution.getChecksum());
        assertEquals("private-payload", execution.getPayloadSnapshot().get("secret").asText());
        assertEquals("/private/internal-error", execution.getErrorMessage());
        assertNotSame(execution, executionController.getReportExecutionById(executionId.toString()));
    }

    @Test
    void customerExecutionListIsRestrictedToRequesterAndSanitized() throws Exception {
        ReportExecution execution = execution();
        execution.setFilePath("/private/report.pdf");
        when(executions.findByTenantIdAndCustomerIdAndRequestedBy(eq(tenantId), eq(customerId), eq(userId), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(execution),
                        invocation.getArgument(3, Pageable.class), 1));
        mvc.perform(get("/api/report-executions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filePath").doesNotExist());
        verify(executions).findByTenantIdAndCustomerIdAndRequestedBy(tenantId, customerId, userId, PageRequest.of(0, 10));
        verify(executions, never()).findByTenantId(any(), any());
        assertEquals("/private/report.pdf", execution.getFilePath());
    }

    @Test
    void customerTemplateHistoryIsRestrictedToRequester() throws Exception {
        when(executions.findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                eq(tenantId), eq(templateId), eq(customerId), eq(userId), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(4, Pageable.class)));
        mvc.perform(get("/api/report-executions/template/{id}", templateId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(10));
        verify(executions).findByTenantIdAndTemplateIdAndCustomerIdAndRequestedBy(
                tenantId, templateId, customerId, userId, PageRequest.of(0, 10));
        verify(executions, never()).findByTenantIdAndTemplateId(any(), any(), any());
    }

    @Test
    void customerCannotDeleteAnotherUsersExecution() throws Exception {
        ReportExecution execution = execution();
        execution.setRequestedBy(UUID.randomUUID());
        when(executions.findById(tenantId, executionId)).thenReturn(execution);
        mvc.perform(delete("/api/report-executions/{id}", executionId)).andExpect(status().isForbidden());
        verify(executions, never()).delete(any(), any());
    }

    @Test
    void customerCanDeleteOwnExecution() throws Exception {
        when(executions.findById(tenantId, executionId)).thenReturn(execution());
        mvc.perform(delete("/api/report-executions/{id}", executionId)).andExpect(status().isOk());
        verify(executions).delete(tenantId, executionId);
    }

    @Test
    void entitySelectionForwardsAuthenticatedPrincipalAndRequestedScope() throws Exception {
        SecurityUser user = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        doThrow(new AccessDeniedException("foreign customer"))
                .when(selection).findSelectableEntities(same(user), eq(EntityType.DEVICE), any(CustomerId.class), isNull(), eq(0), eq(50));
        mvc.perform(get("/api/reports/selectable-entities").param("entityType", "DEVICE")
                        .param("customerId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void telemetryKeysUseAuthenticatedPrincipalAndTypedEntity() throws Exception {
        UUID deviceUuid = UUID.randomUUID();
        SecurityUser user = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        when(selection.findSelectableEntityKeys(user, new DeviceId(deviceUuid))).thenReturn(List.of("temperature"));
        mvc.perform(get("/api/reports/selectable-entity-keys").param("entityType", "DEVICE")
                        .param("entityId", deviceUuid.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("temperature"));
        verify(selection).findSelectableEntityKeys(user, new DeviceId(deviceUuid));
    }

    @Test
    void invalidIdsAndPaginationAreRejectedBeforeServiceCalls() throws Exception {
        mvc.perform(get("/api/report-templates/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/report-executions/not-a-uuid")).andExpect(status().isBadRequest());
        for (String pageSize : List.of("0", "-1", "1001")) {
            mvc.perform(get("/api/report-templates").param("pageSize", pageSize)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/report-executions").param("pageSize", pageSize)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/reports/selectable-entities").param("entityType", "DEVICE")
                    .param("pageSize", pageSize)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/report-templates").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/selectable-entity-keys").param("entityType", "DEVICE")
                .param("entityId", "invalid")).andExpect(status().isBadRequest());
        verifyNoInteractions(templates, executions, storage, selection);
    }

    @Test
    void missingTemplateReturnsNotFoundWithoutPrivateDetails() throws Exception {
        when(templates.findById(tenantId, templateId)).thenThrow(
                new ReportServiceException(ReportErrorCode.TEMPLATE_NOT_FOUND, "private identifier"));
        mvc.perform(get("/api/report-templates/{id}", templateId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reportErrorCode").value("TEMPLATE_NOT_FOUND"))
                .andExpect(content().string(not(containsString("private identifier"))));
    }

    @Test
    void missingExecutionFromRealServiceMapsToNotFound() throws Exception {
        ReportExecutionDao dao = mock(ReportExecutionDao.class);
        when(dao.findById(tenantId, executionId)).thenReturn(Optional.empty());
        DefaultReportExecutionService real = new DefaultReportExecutionService(dao, templates,
                mock(ReportValidationService.class), mock(ReportRequestBuilderService.class),
                storage, mock(ReportExecutionDispatcher.class));
        when(executions.findById(tenantId, executionId)).thenAnswer(invocation -> real.findById(tenantId, executionId));
        mvc.perform(get("/api/report-executions/{id}", executionId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reportErrorCode").value("EXECUTION_NOT_FOUND"));
        verifyNoInteractions(storage);
    }

    @Test
    void missingFileReturnsNotFoundWithoutLeakingPath() throws Exception {
        ReportExecution execution = execution();
        when(executions.findById(tenantId, executionId)).thenReturn(execution);
        when(storage.loadFile(tenantId, execution)).thenThrow(
                new ReportServiceException(ReportErrorCode.FILE_NOT_FOUND, "/private/report.pdf"));
        mvc.perform(get("/api/report-executions/{id}/download", executionId)).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("/private/"))));
    }

    @Test
    void invalidGenerationConfigurationReturnsBadRequest() throws Exception {
        when(templates.findById(tenantId, templateId)).thenReturn(template());
        when(executions.generate(eq(tenantId), eq(userId), eq(templateId), any())).thenThrow(
                new ReportServiceException(ReportErrorCode.INVALID_TIME_RANGE, "invalid timestamps"));
        mvc.perform(post("/api/report-templates/{id}/generate", templateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.reportErrorCode").value("INVALID_TIME_RANGE"));
    }

    private void authenticate(Authority authority) {
        SecurityUser user = new SecurityUser(new UserId(userId));
        user.setTenantId(tenantId);
        user.setCustomerId(customerId);
        user.setAuthority(authority);
        user.setEnabled(true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @SuppressWarnings("unchecked")
    private <T> T secured(T controller) {
        ProxyFactory factory = new ProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (T) factory.getProxy();
    }

    private ReportTemplate template() {
        ReportTemplate template = new ReportTemplate();
        template.setId(new ReportTemplateId(templateId));
        template.setTenantId(tenantId);
        template.setCustomerId(customerId);
        template.setName("Report template");
        return template;
    }

    private ReportExecution execution() {
        ReportExecution execution = new ReportExecution();
        execution.setId(new ReportExecutionId(executionId));
        execution.setTenantId(tenantId);
        execution.setCustomerId(customerId);
        execution.setRequestedBy(userId);
        execution.setStatus(ReportExecutionStatus.SUCCESS);
        return execution;
    }
}
