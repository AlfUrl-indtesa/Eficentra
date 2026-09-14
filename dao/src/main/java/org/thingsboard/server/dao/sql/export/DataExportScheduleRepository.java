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

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.common.data.export.DataExportScheduleStatus;
import org.thingsboard.server.dao.model.sql.DataExportScheduleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataExportScheduleRepository extends JpaRepository<DataExportScheduleEntity, UUID> {

    Optional<DataExportScheduleEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<DataExportScheduleEntity> findByEnabledTrueAndNextRunTsLessThanEqualOrderByNextRunTsAsc(
            long now, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DataExportScheduleEntity s
               set s.lastStatus = :running,
                   s.lastRunTs = :now,
                   s.nextRunTs = :leaseUntil,
                   s.lastError = null,
                   s.updatedTime = :now
             where s.id = :id
               and s.enabled = true
               and s.nextRunTs is not null
               and s.nextRunTs <= :now
            """)
    int claim(@Param("id") UUID id,
              @Param("now") long now,
              @Param("leaseUntil") long leaseUntil,
              @Param("running") DataExportScheduleStatus running);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DataExportScheduleEntity s
               set s.lastStatus = :success,
                   s.lastSuccessTs = :exportedUntilTs,
                   s.nextRunTs = :nextRunTs,
                   s.lastError = null,
                   s.updatedTime = :completedAt
             where s.id = :id
            """)
    int markSuccess(@Param("id") UUID id,
                    @Param("completedAt") long completedAt,
                    @Param("exportedUntilTs") long exportedUntilTs,
                    @Param("nextRunTs") long nextRunTs,
                    @Param("success") DataExportScheduleStatus success);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DataExportScheduleEntity s
               set s.lastStatus = :failed,
                   s.nextRunTs = :nextRunTs,
                   s.lastError = :error,
                   s.updatedTime = :completedAt
             where s.id = :id
            """)
    int markFailed(@Param("id") UUID id,
                   @Param("completedAt") long completedAt,
                   @Param("nextRunTs") long nextRunTs,
                   @Param("error") String error,
                   @Param("failed") DataExportScheduleStatus failed);
}
