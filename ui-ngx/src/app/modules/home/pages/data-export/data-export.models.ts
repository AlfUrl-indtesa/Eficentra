///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

export type DataExportFormat = 'CSV' | 'JSON' | 'ZIP';
export type DataExportPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY';
export type DataExportMode = 'FULL' | 'INCREMENTAL';
export type DataExportScheduleStatus = 'NEVER_RUN' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ExportableDevice {
  id: string;
  name: string;
  type?: string;
  label?: string;
}

export interface DataExportPreviewRequest {
  deviceIds: string[];
  includeAttributes: boolean;
}

export interface DataExportPreviewResponse {
  devices: ExportableDevice[];
  devicesTruncated: boolean;
  keys: string[];
  keysTruncated: boolean;
  attributeKeys: string[];
  attributeKeysTruncated: boolean;
  suggestedEmail?: string;
  defaultStartTs: number;
  defaultEndTs: number;
  maxDevices: number;
  maxKeys: number;
  maxRows: number;
  maxScheduleAttachmentBytes: number;
  maxRangeDays: number;
}

export interface DataExportRequest {
  deviceIds: string[];
  allDevices: boolean;
  keys: string[];
  attributeKeys: string[];
  includeAttributes: boolean;
  startTs: number;
  endTs: number;
  format: DataExportFormat;
}

export interface DataExportScheduleRequest {
  enabled: boolean;
  allDevices: boolean;
  deviceIds: string[];
  keys: string[];
  attributeKeys: string[];
  includeAttributes: boolean;
  format: DataExportFormat;
  email: string;
  period: DataExportPeriod;
  dayOfWeek: number;
  dayOfMonth: number;
  timeOfDay: string;
  timezone: string;
  mode: DataExportMode;
  fullLookbackDays: number;
}

export interface DataExportSchedule extends DataExportScheduleRequest {
  id: string;
  nextRunTs?: number;
  lastRunTs?: number;
  lastSuccessTs?: number;
  lastStatus: DataExportScheduleStatus;
  lastError?: string;
  createdTime: number;
  updatedTime: number;
}
