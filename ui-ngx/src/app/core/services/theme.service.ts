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

import { Injectable, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'tb-ui-theme';
  private isDarkMode = false;

  constructor(@Inject(DOCUMENT) private document: Document) {
    this.initTheme();
  }

  initTheme(): void {
    const view = this.document.defaultView;
    let savedTheme: string | null = null;
    try {
      savedTheme = view?.localStorage.getItem(this.storageKey) ?? null;
    } catch {
      // The system preference remains usable when browser storage is blocked.
    }
    const prefersDark = view?.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    this.setDark(savedTheme === 'dark' || (savedTheme !== 'light' && prefersDark));
  }

  toggleTheme(): void {
    this.setDark(!this.isDarkMode);
  }

  isDark(): boolean {
    return this.isDarkMode;
  }

  private setDark(isDark: boolean): void {
    this.isDarkMode = isDark;
    try {
      this.document.defaultView?.localStorage.setItem(this.storageKey, isDark ? 'dark' : 'light');
    } catch {
      // Apply the theme even if it cannot be saved in this browser.
    }
    this.document.documentElement.classList.toggle('dark', isDark);
    this.document.body.classList.toggle('tb-dark', isDark);
    this.document.body.classList.toggle('tb-light', !isDark);
  }
}
