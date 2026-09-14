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

import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.export.DataExportPeriod;
import org.thingsboard.server.common.data.export.DataExportSchedule;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataExportScheduleCalculatorTest {

    private final DataExportScheduleCalculator calculator = new DataExportScheduleCalculator();

    @Test
    void calculatesNextDailyRunWithoutRepeatingCurrentInstant() {
        DataExportSchedule schedule = schedule(DataExportPeriod.DAILY, "08:00");
        long after = at(2026, 9, 14, 8, 0);

        assertEquals(at(2026, 9, 15, 8, 0), calculator.nextRun(schedule, after));
    }

    @Test
    void calculatesConfiguredWeekday() {
        DataExportSchedule schedule = schedule(DataExportPeriod.WEEKLY, "08:30");
        schedule.setDayOfWeek(5);

        assertEquals(at(2026, 9, 18, 8, 30),
                calculator.nextRun(schedule, at(2026, 9, 14, 12, 0)));
    }

    @Test
    void calculatesConfiguredMonthDay() {
        DataExportSchedule schedule = schedule(DataExportPeriod.MONTHLY, "06:15");
        schedule.setDayOfMonth(10);

        assertEquals(at(2026, 10, 10, 6, 15),
                calculator.nextRun(schedule, at(2026, 9, 14, 12, 0)));
    }

    private DataExportSchedule schedule(DataExportPeriod period, String time) {
        DataExportSchedule schedule = new DataExportSchedule();
        schedule.setPeriod(period);
        schedule.setTimeOfDay(time);
        schedule.setTimezone("America/Monterrey");
        schedule.setDayOfWeek(1);
        schedule.setDayOfMonth(1);
        return schedule;
    }

    private long at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0,
                ZoneId.of("America/Monterrey")).toInstant().toEpochMilli();
    }
}
