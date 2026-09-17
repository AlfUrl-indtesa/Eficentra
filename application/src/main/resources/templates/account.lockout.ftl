<#--

    Copyright © 2016-2026 The Thingsboard Authors

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
<#import "eficentra.email.layout.ftl" as eficentra>
<@eficentra.email title="Eficentra - Cuenta bloqueada" heading="Cuenta bloqueada por seguridad" targetEmail=targetEmail>
  <@eficentra.notice tone="danger">
    La cuenta <strong>${lockoutAccount?html}</strong> se bloqueó después de superar ${maxFailedLoginAttempts?c} intentos fallidos de autenticación.
  </@eficentra.notice>
  <p style="margin: 0;">Revisa la actividad de la cuenta y contacta al administrador del sistema para recuperar el acceso.</p>
</@eficentra.email>
