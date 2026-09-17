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
<@eficentra.email title="Eficentra - Código de verificación" heading="Tu código de verificación" targetEmail=targetEmail>
  <p style="margin: 0 0 14px;">Usa este código para confirmar tu acceso a Eficentra:</p>
  <@eficentra.code value=code />
  <p style="margin: 0 0 14px;">El código caducará en ${expirationTimeSeconds?c} segundos.</p>
  <p style="margin: 0;">Si no solicitaste este código, ignora este mensaje. No compartas el código con nadie.</p>
</@eficentra.email>
