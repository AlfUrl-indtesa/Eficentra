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

import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.export.DataExportPeriod;
import org.thingsboard.server.common.data.export.DataExportSchedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class DataExportScheduleCalculator {

    public long nextRun(DataExportSchedule schedule, long afterTs) {
        ZoneId zone = ZoneId.of(schedule.getTimezone());
        LocalTime time = LocalTime.parse(schedule.getTimeOfDay());
        ZonedDateTime after = Instant.ofEpochMilli(afterTs).atZone(zone);

        return switch (schedule.getPeriod()) {
            case DAILY -> nextDaily(after, time).toInstant().toEpochMilli();
            case WEEKLY -> nextWeekly(after, time, schedule.getDayOfWeek()).toInstant().toEpochMilli();
            case MONTHLY -> nextMonthly(after, time, schedule.getDayOfMonth()).toInstant().toEpochMilli();
        };
    }

    private ZonedDateTime nextDaily(ZonedDateTime after, LocalTime time) {
        ZonedDateTime candidate = after.toLocalDate().atTime(time).atZone(after.getZone());
        return candidate.isAfter(after) ? candidate : candidate.plusDays(1);
    }

    private ZonedDateTime nextWeekly(ZonedDateTime after, LocalTime time, Integer configuredDay) {
        int day = configuredDay == null ? DayOfWeek.MONDAY.getValue() : configuredDay;
        DayOfWeek target = DayOfWeek.of(day);
        LocalDate date = after.toLocalDate();
        int addDays = Math.floorMod(target.getValue() - date.getDayOfWeek().getValue(), 7);
        ZonedDateTime candidate = date.plusDays(addDays).atTime(time).atZone(after.getZone());
        return candidate.isAfter(after) ? candidate : candidate.plusWeeks(1);
    }

    private ZonedDateTime nextMonthly(ZonedDateTime after, LocalTime time, Integer configuredDay) {
        int day = configuredDay == null ? 1 : configuredDay;
        YearMonth month = YearMonth.from(after);
        ZonedDateTime candidate = month.atDay(Math.min(day, month.lengthOfMonth()))
                .atTime(time)
                .atZone(after.getZone());
        if (candidate.isAfter(after)) {
            return candidate;
        }
        YearMonth next = month.plusMonths(1);
        return next.atDay(Math.min(day, next.lengthOfMonth()))
                .atTime(time)
                .atZone(after.getZone());
    }
}
