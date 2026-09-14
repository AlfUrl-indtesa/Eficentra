--
-- Copyright © 2016-2026 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS data_export_schedule (
    id uuid NOT NULL CONSTRAINT data_export_schedule_pkey PRIMARY KEY,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    user_id uuid NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    all_devices boolean NOT NULL DEFAULT false,
    device_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    telemetry_keys jsonb NOT NULL DEFAULT '[]'::jsonb,
    attribute_keys jsonb NOT NULL DEFAULT '[]'::jsonb,
    include_attributes boolean NOT NULL DEFAULT true,
    format varchar(16) NOT NULL DEFAULT 'ZIP',
    email varchar(320) NOT NULL,
    period varchar(16) NOT NULL DEFAULT 'WEEKLY',
    day_of_week integer,
    day_of_month integer,
    time_of_day varchar(5) NOT NULL DEFAULT '08:00',
    timezone varchar(64) NOT NULL DEFAULT 'America/Monterrey',
    mode varchar(16) NOT NULL DEFAULT 'INCREMENTAL',
    full_lookback_days integer NOT NULL DEFAULT 30,
    next_run_ts bigint,
    last_run_ts bigint,
    last_success_ts bigint,
    last_status varchar(16) NOT NULL DEFAULT 'NEVER_RUN',
    last_error varchar(1000),
    created_time bigint NOT NULL,
    updated_time bigint NOT NULL,
    CONSTRAINT data_export_schedule_day_of_week_chk
        CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    CONSTRAINT data_export_schedule_day_of_month_chk
        CHECK (day_of_month IS NULL OR day_of_month BETWEEN 1 AND 28),
    CONSTRAINT data_export_schedule_lookback_chk
        CHECK (full_lookback_days > 0),
    CONSTRAINT data_export_schedule_owner_uk
        UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_data_export_schedule_due
    ON data_export_schedule(next_run_ts ASC)
    WHERE enabled = true AND next_run_ts IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_data_export_schedule_tenant
    ON data_export_schedule(tenant_id, updated_time DESC);
