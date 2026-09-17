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
<@eficentra.email title="Eficentra - Contraseña actualizada" heading="Tu contraseña fue actualizada" targetEmail=targetEmail>
  <@eficentra.notice tone="success">
    La contraseña de tu cuenta de Eficentra se restableció correctamente.
  </@eficentra.notice>
  <p style="margin: 0;">Ya puedes iniciar sesión con tu nueva contraseña.</p>
  <@eficentra.action href=loginLink label="Iniciar sesión" />
  <p style="margin: 0; color: #718096; font-size: 13px;">Si no realizaste este cambio, contacta de inmediato al administrador del sistema.</p>
</@eficentra.email>
