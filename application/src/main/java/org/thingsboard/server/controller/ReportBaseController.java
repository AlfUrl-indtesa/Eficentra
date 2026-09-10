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

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.thingsboard.server.service.report.ReportExecutionNotFoundException;
import org.thingsboard.server.service.report.ReportServiceException;

import java.util.Map;

public abstract class ReportBaseController extends BaseController {

    protected PageRequest reportPageRequest(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("Invalid report pagination");
        }
        return PageRequest.of(page, pageSize);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> handleReportAccessDenied(AccessDeniedException exception) {
        return reportError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Report access denied.");
    }

    @ExceptionHandler(AuthenticationException.class)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> handleReportAuthentication(AuthenticationException exception) {
        return reportError(HttpStatus.UNAUTHORIZED, "AUTHENTICATION", "Authentication is required.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> handleReportBadRequest(Exception exception) {
        return reportError(HttpStatus.BAD_REQUEST, "BAD_REQUEST_PARAMS", "Invalid report request parameters.");
    }

    @ExceptionHandler(ReportServiceException.class)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> handleReportServiceException(ReportServiceException exception) {
        if (exception instanceof ReportExecutionNotFoundException) {
            return reportError(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "Report execution not found.");
        }
        if (exception.getErrorCode() == null) {
            return reportError(HttpStatus.INTERNAL_SERVER_ERROR, "UNKNOWN_ERROR", "The report operation failed.");
        }
        switch (exception.getErrorCode()) {
            case TEMPLATE_NOT_FOUND:
            case FILE_NOT_FOUND:
                return reportError(HttpStatus.NOT_FOUND, exception.getErrorCode().name(), "Report resource not found.");
            case ACCESS_DENIED:
                return reportError(HttpStatus.FORBIDDEN, exception.getErrorCode().name(), "Report access denied.");
            case INVALID_TIME_RANGE:
            case INVALID_ENTITY_SCOPE:
                return reportError(HttpStatus.BAD_REQUEST, exception.getErrorCode().name(), "Invalid report configuration.");
            case TEMPLATE_DISABLED:
                return reportError(HttpStatus.CONFLICT, exception.getErrorCode().name(), "Report template is disabled.");
            default:
                return reportError(HttpStatus.INTERNAL_SERVER_ERROR, exception.getErrorCode().name(), "The report operation failed.");
        }
    }

    private ResponseEntity<Map<String, Object>> reportError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(), "reportErrorCode", code, "message", message));
    }
}
