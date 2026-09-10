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

import {
  Injectable
} from "@angular/core";

import {
  CanActivate,
  Router,
  UrlTree
} from "@angular/router";

import {
  MatSnackBar
} from "@angular/material/snack-bar";

import {
  Store
} from "@ngrx/store";

import {
  AppState
} from "@core/core.state";

import {
  getCurrentAuthState
} from "@core/auth/auth.selectors";

import { canAccessReports } from "./report-permissions";

@Injectable({
  providedIn: "root"
})
export class ReportsAccessGuard
  implements CanActivate {

  constructor(
    private store: Store<AppState>,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
  }

  canActivate():
    boolean | UrlTree {

    const authState =
      getCurrentAuthState(
        this.store
      );

    const authority =
      authState?.authUser?.authority;

    const hasAccess = canAccessReports(authority);

    if (hasAccess) {
      return true;
    }

    this.snackBar.open(
      "No tienes permisos para acceder a Reportes.",
      "Cerrar",
      {
        duration: 5000,
        horizontalPosition: "center",
        verticalPosition: "top"
      }
    );

    return this.router.parseUrl(
      "/home"
    );
  }
}
