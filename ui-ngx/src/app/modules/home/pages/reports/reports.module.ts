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

import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";

import { ReportsRoutingModule } from "./reports-routing.module";
import { ReportsPageComponent } from "./pages/reports-page.component";
import { ReportExecutionsPageComponent } from "./pages/report-executions-page.component";
import { ReportTemplateDialogComponent } from "./components/report-template-dialog.component";
import { GenerateReportDialogComponent } from "./components/generate-report-dialog.component";

import { MatButtonModule } from "@angular/material/button";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { MatIconModule } from "@angular/material/icon";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { SharedModule } from "@app/shared/shared.module";
import { MatSnackBarModule } from "@angular/material/snack-bar";

import { MatPaginatorModule } from "@angular/material/paginator";

@NgModule({
  declarations: [
    ReportsPageComponent,
    ReportExecutionsPageComponent,
    ReportTemplateDialogComponent,
    GenerateReportDialogComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    SharedModule,
    ReactiveFormsModule,
    ReportsRoutingModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatIconModule,
    MatDatepickerModule,
    MatCheckboxModule,
    MatSnackBarModule,
    MatPaginatorModule,
  ],
})
export class ReportsModule {}
