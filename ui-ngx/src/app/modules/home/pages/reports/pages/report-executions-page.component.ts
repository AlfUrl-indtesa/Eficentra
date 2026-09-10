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

import { Component, OnDestroy, OnInit } from "@angular/core";
import { Subject, Subscription, timer } from "rxjs";
import { finalize, exhaustMap, takeUntil } from "rxjs/operators";

import { ReportExecution } from "../models/report.models";

import { ReportService } from "../services/report.service";

import { PageEvent } from '@angular/material/paginator';
import { canDownloadReport } from '../report-permissions';

@Component({
    selector: "tb-report-executions-page",
    standalone: false,
    templateUrl: "./report-executions-page.component.html",
    styleUrls: [
        "./report-executions-page.component.scss",
    ],
})
export class ReportExecutionsPageComponent implements OnInit, OnDestroy {
    displayedColumns = [
        "templateNameSnapshot",
        "status",
        "requestedTime",
        "finishedTime",
        "actions",
    ];

    private readonly destroy$ = new Subject<void>();
    private disposed = false;
    page = 0;
    pageSize = 20;
    totalElements = 0;
    readonly canDownload = canDownloadReport;

    onPage(event: PageEvent): void {
        this.page = event.pageIndex;
        this.pageSize = event.pageSize;
        this.stopExecutionPolling();
        this.loadExecutions();
    }

    executions: ReportExecution[] = [];

    loading = false;

    private executionPollingSubscription?: Subscription;

    private readonly executionPollingIntervalMs = 2000;

    constructor(
        private reportService: ReportService,
    ) {
    }

    ngOnInit(): void {
        this.loadExecutions();
    }

    ngOnDestroy(): void {
        this.disposed = true;
        this.destroy$.next();
        this.destroy$.complete();
        this.stopExecutionPolling();
    }

    loadExecutions(
        showLoading = true,
    ): void {
        if (showLoading) {
            this.loading = true;
        }

        this.reportService
            .getReportExecutions(this.page, this.pageSize)
            .pipe(takeUntil(this.destroy$),
                finalize(() => {
                    if (showLoading) {
                        this.loading = false;
                    }
                }),
            )
            .subscribe({
                next: (pageData) => {
                    this.executions = pageData.data ||
                        pageData.content ||
                        [];

                    this.totalElements = pageData.totalElements ?? this.executions.length;
                    this.syncExecutionPolling();
                },
                error: () => {
                    this.stopExecutionPolling();
                },
            });
    }

    refresh(): void {
        if (this.disposed) { return; }
        this.loadExecutions();
    }

    private syncExecutionPolling(): void {
        if (this.hasActiveExecutions()) {
            this.startExecutionPolling();
        } else {
            this.stopExecutionPolling();
        }
    }

    private startExecutionPolling(): void {
        if (
            this.executionPollingSubscription &&
            !this.executionPollingSubscription.closed
        ) {
            return;
        }

        this.executionPollingSubscription = timer(
            this.executionPollingIntervalMs,
            this.executionPollingIntervalMs,
        )
            .pipe(takeUntil(this.destroy$),
                exhaustMap(() =>
                    this.reportService
                        .getReportExecutions(this.page, this.pageSize)
                ),
            )
            .subscribe({
                next: (pageData) => {
                    this.executions = pageData.data ||
                        pageData.content ||
                        [];

                    this.totalElements = pageData.totalElements ?? this.executions.length;
                    if (!this.hasActiveExecutions()) {
                        this.stopExecutionPolling();
                    }
                },
                error: () => {
                    this.stopExecutionPolling();
                },
            });
    }

    private stopExecutionPolling(): void {
        if (this.executionPollingSubscription) {
            this.executionPollingSubscription
                .unsubscribe();

            this.executionPollingSubscription = undefined;
        }
    }

    private hasActiveExecutions(): boolean {
        return this.executions.some(
            (execution) =>
                execution?.status === "PENDING" ||
                execution?.status === "RUNNING",
        );
    }

    downloadExecution(
        execution: any,
    ): void {
        if (!this.canDownload(execution)) { return; }
        const executionId = this.executionUuid(
            execution,
        );

        if (!executionId) {
            return;
        }

        this.reportService
            .downloadReportExecution(
                executionId,
            )
            .subscribe((blob) => {
                const fileName = execution.fileName ||
                    "report.pdf";

                const url = window.URL
                    .createObjectURL(blob);

                const anchor = document.createElement(
                    "a",
                );

                anchor.href = url;
                anchor.download = fileName;

                anchor.click();

                window.URL.revokeObjectURL(
                    url,
                );
            });
    }

    deleteExecution(
        execution: any,
    ): void {
        const executionId = this.executionUuid(
            execution,
        );

        if (!executionId) {
            return;
        }

        if (
            !confirm(
                `¿Eliminar el reporte generado "${
                    execution.fileName ||
                    executionId
                }"?`,
            )
        ) {
            return;
        }

        this.reportService
            .deleteReportExecution(
                executionId,
            )
            .subscribe(() => {
                this.refresh();
            });
    }

    private executionUuid(
        execution: any,
    ): string | null {
        const id: any = execution?.id;

        if (!id) {
            return null;
        }

        if (typeof id === "string") {
            return id;
        }

        if (id.id) {
            return id.id;
        }

        return null;
    }
}
