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

export interface ReportTemplate {
  id?: string;
  name: string;
  description?: string;
  type: 'operational' | 'executive' | 'compressed_air';
  entityFilter: ReportEntityFilter;
  sections: ReportSectionConfig[];
  branding?: ReportBrandingConfig;
  defaultTimeRange?: ReportTimeRangeConfig;
  outputFormat: 'pdf';
  enabled: boolean;
  createdTime?: number;
}

export interface ReportEntityFilter {
  entityType: 'DEVICE' | 'ASSET' | 'ENTITY_GROUP';
  entityIds?: string[];
  entityGroupId?: string;
}

export interface ReportSectionConfig {
  key: string;
  title: string;
  enabled: boolean;
  order: number;
  config?: any;
}

export interface ReportBrandingConfig {
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  companyName?: string;
  footerText?: string;
}

export interface ReportTimeRangeConfig {
  mode: 'LAST_24_HOURS' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM';
}
