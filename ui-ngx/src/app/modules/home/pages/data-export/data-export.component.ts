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

import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import {
  DataExportFormat,
  DataExportMode,
  DataExportPeriod,
  DataExportPreviewResponse,
  DataExportSchedule,
  ExportableDevice
} from './data-export.models';
import { DataExportService } from './data-export.service';

@Component({
  selector: 'tb-data-export',
  standalone: false,
  templateUrl: './data-export.component.html',
  styleUrls: ['./data-export.component.scss']
})
export class DataExportComponent implements OnInit, OnDestroy {

  readonly formats: DataExportFormat[] = ['CSV', 'JSON', 'ZIP'];
  readonly periods: DataExportPeriod[] = ['DAILY', 'WEEKLY', 'MONTHLY'];
  readonly modes: DataExportMode[] = ['INCREMENTAL', 'FULL'];
  readonly weekdays = [1, 2, 3, 4, 5, 6, 7];
  readonly destroy$ = new Subject<void>();

  form: FormGroup;
  devices: ExportableDevice[] = [];
  telemetryKeys: string[] = [];
  attributeKeys: string[] = [];
  schedule?: DataExportSchedule;
  limits?: DataExportPreviewResponse;
  loading = false;
  loadingKeys = false;
  exporting = false;
  savingSchedule = false;

  constructor(
    private fb: FormBuilder,
    private dataExportService: DataExportService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    this.form = this.fb.group({
      deviceIds: [[]],
      keys: [[]],
      attributeKeys: [[]],
      includeAttributes: [true],
      startDateTime: ['', Validators.required],
      endDateTime: ['', Validators.required],
      format: ['CSV', Validators.required],
      scheduleEnabled: [false],
      allDevices: [false],
      email: ['', Validators.email],
      period: ['WEEKLY', Validators.required],
      dayOfWeek: [1, [Validators.required, Validators.min(1), Validators.max(7)]],
      dayOfMonth: [1, [Validators.required, Validators.min(1), Validators.max(28)]],
      timeOfDay: ['08:00', Validators.required],
      timezone: [this.browserTimezone(), Validators.required],
      mode: ['INCREMENTAL', Validators.required],
      fullLookbackDays: [30, [Validators.required, Validators.min(1)]]
    });
  }

  ngOnInit(): void {
    this.loadPreview([]);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onDevicesChanged(): void {
    this.form.patchValue({ keys: [], attributeKeys: [] }, { emitEvent: false });
    this.loadPreview(this.selectedDeviceIds(), true);
  }

  onIncludeAttributesChanged(): void {
    if (!this.form.get('includeAttributes')?.value) {
      this.form.patchValue({ attributeKeys: [] }, { emitEvent: false });
    }
    if (this.selectedDeviceIds().length) {
      this.loadPreview(this.selectedDeviceIds(), true);
    }
  }

  exportData(): void {
    if (!this.validateManualExport()) {
      return;
    }
    const startTs = this.fromDateTimeLocal(this.form.get('startDateTime')?.value);
    const endTs = this.fromDateTimeLocal(this.form.get('endDateTime')?.value);
    if (startTs === null || endTs === null || startTs > endTs) {
      this.notify('data_export.invalid_range');
      return;
    }

    this.exporting = true;
    this.dataExportService.export({
      deviceIds: this.selectedDeviceIds(),
      allDevices: false,
      keys: this.form.get('keys')?.value || [],
      attributeKeys: this.form.get('attributeKeys')?.value || [],
      includeAttributes: !!this.form.get('includeAttributes')?.value,
      startTs,
      endTs,
      format: this.form.get('format')?.value as DataExportFormat
    }).pipe(
      takeUntil(this.destroy$),
      finalize(() => this.exporting = false)
    ).subscribe({
      next: response => {
        this.download(response);
        this.notify('data_export.export_ready');
      },
      error: error => this.showError(error)
    });
  }

  saveSchedule(): void {
    const enabled = !!this.form.get('scheduleEnabled')?.value;
    const allDevices = !!this.form.get('allDevices')?.value;
    const email = (this.form.get('email')?.value || '').trim();
    if (enabled && (!email || this.form.get('email')?.invalid)) {
      this.form.get('email')?.markAsTouched();
      this.notify('data_export.valid_email_required');
      return;
    }
    if (enabled && !allDevices && !this.selectedDeviceIds().length) {
      this.notify('data_export.select_device_required');
      return;
    }
    if (enabled && this.scheduleControlsInvalid()) {
      this.notify('data_export.fix_form');
      return;
    }

    this.savingSchedule = true;
    this.dataExportService.saveSchedule({
      enabled,
      allDevices,
      deviceIds: this.selectedDeviceIds(),
      keys: this.form.get('keys')?.value || [],
      attributeKeys: this.form.get('attributeKeys')?.value || [],
      includeAttributes: !!this.form.get('includeAttributes')?.value,
      format: this.form.get('format')?.value as DataExportFormat,
      email,
      period: this.form.get('period')?.value as DataExportPeriod,
      dayOfWeek: Number(this.form.get('dayOfWeek')?.value),
      dayOfMonth: Number(this.form.get('dayOfMonth')?.value),
      timeOfDay: this.form.get('timeOfDay')?.value,
      timezone: this.form.get('timezone')?.value,
      mode: this.form.get('mode')?.value as DataExportMode,
      fullLookbackDays: Number(this.form.get('fullLookbackDays')?.value)
    }).pipe(
      takeUntil(this.destroy$),
      finalize(() => this.savingSchedule = false)
    ).subscribe({
      next: schedule => {
        this.schedule = schedule;
        this.notify(enabled ? 'data_export.schedule_saved' : 'data_export.schedule_disabled');
      },
      error: error => this.showError(error)
    });
  }

  showWeekday(): boolean {
    return this.form.get('period')?.value === 'WEEKLY';
  }

  showMonthDay(): boolean {
    return this.form.get('period')?.value === 'MONTHLY';
  }

  showFullLookback(): boolean {
    return this.form.get('mode')?.value === 'FULL';
  }

  private loadPreview(deviceIds: string[], preserveDates = false): void {
    if (deviceIds.length) {
      this.loadingKeys = true;
    } else {
      this.loading = true;
    }
    this.dataExportService.preview({
      deviceIds,
      includeAttributes: !!this.form.get('includeAttributes')?.value
    }).pipe(
      takeUntil(this.destroy$),
      finalize(() => {
        this.loading = false;
        this.loadingKeys = false;
      })
    ).subscribe({
      next: response => {
        this.devices = response.devices || [];
        this.telemetryKeys = response.keys || [];
        this.attributeKeys = response.attributeKeys || [];
        this.limits = response;
        if (!preserveDates) {
          this.form.patchValue({
            startDateTime: this.toDateTimeLocal(response.defaultStartTs),
            endDateTime: this.toDateTimeLocal(response.defaultEndTs),
            email: response.suggestedEmail || this.form.get('email')?.value
          }, { emitEvent: false });
          this.loadSchedule();
        }
      },
      error: error => this.showError(error)
    });
  }

  private loadSchedule(): void {
    this.dataExportService.getSchedule()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: schedule => {
          if (!schedule) {
            return;
          }
          this.schedule = schedule;
          this.form.patchValue({
            deviceIds: schedule.deviceIds || [],
            keys: schedule.keys || [],
            attributeKeys: schedule.attributeKeys || [],
            includeAttributes: schedule.includeAttributes,
            format: schedule.format,
            scheduleEnabled: schedule.enabled,
            allDevices: schedule.allDevices,
            email: schedule.email,
            period: schedule.period,
            dayOfWeek: schedule.dayOfWeek,
            dayOfMonth: schedule.dayOfMonth,
            timeOfDay: schedule.timeOfDay,
            timezone: schedule.timezone,
            mode: schedule.mode,
            fullLookbackDays: schedule.fullLookbackDays
          }, { emitEvent: false });
          if (schedule.deviceIds?.length) {
            this.loadPreview(schedule.deviceIds, true);
          }
        },
        error: error => this.showError(error)
      });
  }

  private validateManualExport(): boolean {
    if (!this.selectedDeviceIds().length) {
      this.form.get('deviceIds')?.markAsTouched();
      this.notify('data_export.select_device_required');
      return false;
    }
    if (!this.form.get('keys')?.value?.length && !this.form.get('includeAttributes')?.value) {
      this.notify('data_export.select_data_required');
      return false;
    }
    return true;
  }

  private scheduleControlsInvalid(): boolean {
    const controls = ['email', 'period', 'timeOfDay', 'timezone', 'mode'];
    if (this.showWeekday()) {
      controls.push('dayOfWeek');
    }
    if (this.showMonthDay()) {
      controls.push('dayOfMonth');
    }
    if (this.showFullLookback()) {
      controls.push('fullLookbackDays');
    }
    controls.forEach(name => this.form.get(name)?.markAsTouched());
    return controls.some(name => !!this.form.get(name)?.invalid);
  }

  private selectedDeviceIds(): string[] {
    return this.form.get('deviceIds')?.value || [];
  }

  private download(response: HttpResponse<Blob>): void {
    if (!response.body) {
      return;
    }
    const disposition = response.headers.get('content-disposition') || '';
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
    const fallbackFormat = (this.form.get('format')?.value || 'CSV').toLowerCase();
    const fileName = match ? decodeURIComponent(match[1]) : `eficentra-data.${fallbackFormat}`;
    const url = URL.createObjectURL(response.body);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  private showError(error: HttpErrorResponse): void {
    const backendMessage = error.error?.message || error.error?.error || error.message;
    this.snackBar.open(
      backendMessage || this.translate.instant('data_export.operation_failed'),
      this.translate.instant('action.close'),
      { duration: 7000 }
    );
  }

  private notify(key: string): void {
    this.snackBar.open(
      this.translate.instant(key),
      this.translate.instant('action.close'),
      { duration: 4000 }
    );
  }

  private toDateTimeLocal(timestamp: number): string {
    const date = new Date(timestamp);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  private fromDateTimeLocal(value: string): number | null {
    const timestamp = new Date(value).getTime();
    return Number.isFinite(timestamp) ? timestamp : null;
  }

  private browserTimezone(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Monterrey';
  }
}
