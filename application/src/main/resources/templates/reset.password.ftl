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
<@eficentra.email title="Eficentra - Restablecer contraseña" heading="Restablece tu contraseña" targetEmail=targetEmail>
  <p style="margin: 0 0 14px;">Recibimos una solicitud para restablecer la contraseña de tu cuenta de Eficentra.</p>
  <p style="margin: 0;">El enlace caducará en ${passwordResetLinkTtlInHours?c} horas.</p>
  <@eficentra.action href=passwordResetLink label="Restablecer contraseña" />
  <p style="margin: 0; color: #718096; font-size: 13px;">Si no solicitaste el cambio, ignora este mensaje. Tu contraseña actual seguirá funcionando.</p>
</@eficentra.email>
