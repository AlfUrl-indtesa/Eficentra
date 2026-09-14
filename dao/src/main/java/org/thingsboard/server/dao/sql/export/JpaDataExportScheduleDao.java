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
package org.thingsboard.server.dao.sql.export;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.export.DataExportSchedule;
import org.thingsboard.server.common.data.export.DataExportScheduleStatus;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.export.DataExportScheduleDao;
import org.thingsboard.server.dao.model.sql.DataExportScheduleEntity;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SqlDao
@Component
@RequiredArgsConstructor
public class JpaDataExportScheduleDao implements DataExportScheduleDao {

    private final DataExportScheduleRepository repository;

    @Override
    public DataExportSchedule save(DataExportSchedule schedule) {
        return repository.save(new DataExportScheduleEntity(schedule)).toData();
    }

    @Override
    public Optional<DataExportSchedule> findByTenantIdAndUserId(TenantId tenantId, UUID userId) {
        return repository.findByTenantIdAndUserId(tenantId.getId(), userId)
                .map(DataExportScheduleEntity::toData);
    }

    @Override
    public List<DataExportSchedule> findDue(long now, Pageable pageable) {
        return repository.findByEnabledTrueAndNextRunTsLessThanEqualOrderByNextRunTsAsc(now, pageable)
                .stream()
                .map(DataExportScheduleEntity::toData)
                .toList();
    }

    @Override
    public boolean claim(UUID id, long now, long leaseUntil) {
        return repository.claim(id, now, leaseUntil, DataExportScheduleStatus.RUNNING) == 1;
    }

    @Override
    public void markSuccess(UUID id, long completedAt, long exportedUntilTs, long nextRunTs) {
        repository.markSuccess(id, completedAt, exportedUntilTs, nextRunTs, DataExportScheduleStatus.SUCCESS);
    }

    @Override
    public void markFailed(UUID id, long completedAt, long nextRunTs, String error) {
        repository.markFailed(id, completedAt, nextRunTs, error, DataExportScheduleStatus.FAILED);
    }
}
