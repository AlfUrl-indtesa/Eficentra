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

import java.util.ArrayList;
import java.util.List;

@Data
public class DataExportScheduleRequest {

    private boolean enabled;
    private boolean allDevices;
    private List<String> deviceIds = new ArrayList<>();
    private List<String> keys = new ArrayList<>();
    private List<String> attributeKeys = new ArrayList<>();
    private boolean includeAttributes = true;
    private DataExportFormat format = DataExportFormat.ZIP;
    private String email;
    private DataExportPeriod period = DataExportPeriod.WEEKLY;
    private Integer dayOfWeek = 1;
    private Integer dayOfMonth = 1;
    private String timeOfDay = "08:00";
    private String timezone = "America/Monterrey";
    private DataExportMode mode = DataExportMode.INCREMENTAL;
    private Integer fullLookbackDays = 30;
}
