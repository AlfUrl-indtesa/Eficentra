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

import org.thingsboard.server.common.data.export.DataExportPreviewRequest;
import org.thingsboard.server.common.data.export.DataExportPreviewResponse;
import org.thingsboard.server.common.data.export.DataExportRequest;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleRequest;
import org.thingsboard.server.service.security.model.SecurityUser;

public interface DataExportService {

    DataExportPreviewResponse preview(SecurityUser user, DataExportPreviewRequest request);

    DataExportArtifact export(SecurityUser user, DataExportRequest request) throws Exception;

    DataExportArtifact exportScheduled(DataExportSchedule schedule, DataExportRequest request) throws Exception;

    DataExportSchedule saveSchedule(SecurityUser user, DataExportScheduleRequest request);

    DataExportSchedule getSchedule(SecurityUser user);
}
