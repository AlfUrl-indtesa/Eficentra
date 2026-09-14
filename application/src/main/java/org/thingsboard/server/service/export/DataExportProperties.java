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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "data-export")
public class DataExportProperties {

    private boolean enabled = true;
    private int maxDevices = 100;
    private int maxKeys = 100;
    private long maxRows = 1_000_000;
    private long maxOutputBytes = 52_428_800;
    private long maxScheduleAttachmentBytes = 10_485_760;
    private int maxRangeDays = 3650;
    private int defaultLookbackDays = 7;
    private int devicePageSize = 200;
    private int historyBatchSize = 1000;
    private int queryTimeoutSeconds = 30;
    private int scheduleBatchSize = 20;
    private int scheduleLeaseMinutes = 30;
    private int failedRetryMinutes = 30;
    private int workerPoolSize = 1;
    private int workerQueueCapacity = 10;
    private int shutdownAwaitTerminationSeconds = 30;
    private int tempFileRetentionHours = 24;
    private String tempDir = System.getProperty("java.io.tmpdir") + "/eficentra-data-export";
}
