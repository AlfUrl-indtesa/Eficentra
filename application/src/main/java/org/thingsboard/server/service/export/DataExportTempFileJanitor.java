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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataExportTempFileJanitor {

    private final DataExportProperties properties;

    @Scheduled(
            initialDelayString = "${data-export.cleanup-initial-delay-ms:3600000}",
            fixedDelayString = "${data-export.cleanup-interval-ms:3600000}")
    public void removeExpiredArtifacts() {
        Path directory = Path.of(properties.getTempDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        FileTime cutoff = FileTime.fromMillis(System.currentTimeMillis()
                - TimeUnit.HOURS.toMillis(Math.max(1, properties.getTempFileRetentionHours())));
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().startsWith("eficentra-export-"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> olderThan(path, cutoff))
                    .forEach(this::delete);
        } catch (Exception e) {
            log.warn("[data-export] unable to clean temporary export directory", e);
        }
    }

    private boolean olderThan(Path path, FileTime cutoff) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).compareTo(cutoff) < 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.debug("[data-export] unable to delete expired artifact {}", path, e);
        }
    }
}
