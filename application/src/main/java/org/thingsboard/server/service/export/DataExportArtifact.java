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

import org.thingsboard.server.common.data.export.DataExportFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DataExportArtifact(
        Path path,
        String fileName,
        DataExportFormat format,
        long rowCount,
        long size) implements AutoCloseable {

    @Override
    public void close() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The scheduled janitor will retry removal of an orphaned artifact.
        }
    }
}
