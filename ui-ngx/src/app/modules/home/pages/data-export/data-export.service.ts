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

import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DataExportPreviewRequest,
  DataExportPreviewResponse,
  DataExportRequest,
  DataExportSchedule,
  DataExportScheduleRequest
} from './data-export.models';

@Injectable({ providedIn: 'root' })
export class DataExportService {

  constructor(private http: HttpClient) {}

  preview(request: DataExportPreviewRequest): Observable<DataExportPreviewResponse> {
    return this.http.post<DataExportPreviewResponse>('/api/data-export/preview', request);
  }

  export(request: DataExportRequest): Observable<HttpResponse<Blob>> {
    return this.http.post('/api/data-export/export', request, {
      observe: 'response',
      responseType: 'blob'
    });
  }

  getSchedule(): Observable<DataExportSchedule | null> {
    return this.http.get<DataExportSchedule | null>('/api/data-export/schedule');
  }

  saveSchedule(request: DataExportScheduleRequest): Observable<DataExportSchedule> {
    return this.http.post<DataExportSchedule>('/api/data-export/schedule', request);
  }
}
