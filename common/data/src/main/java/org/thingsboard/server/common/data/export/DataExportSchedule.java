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
package org.thingsboard.server.common.data.export;

import lombok.Data;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class DataExportSchedule {

    private UUID id;
    private TenantId tenantId;
    private CustomerId customerId;
    private UUID userId;
    private boolean enabled;
    private boolean allDevices;
    private List<String> deviceIds = new ArrayList<>();
    private List<String> keys = new ArrayList<>();
    private List<String> attributeKeys = new ArrayList<>();
    private boolean includeAttributes;
    private DataExportFormat format;
    private String email;
    private DataExportPeriod period;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private String timeOfDay;
    private String timezone;
    private DataExportMode mode;
    private Integer fullLookbackDays;
    private Long nextRunTs;
    private Long lastRunTs;
    private Long lastSuccessTs;
    private DataExportScheduleStatus lastStatus;
    private String lastError;
    private long createdTime;
    private long updatedTime;
}
