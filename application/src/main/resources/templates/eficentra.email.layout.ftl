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
<#macro email title heading targetEmail>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="es">
<head>
  <meta name="viewport" content="width=device-width" />
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
  <title>${title?html}</title>
</head>
<body style="font-family: Arial, Helvetica, sans-serif; width: 100% !important; margin: 0; padding: 0; color: #1b2f5c; background-color: #f3f6f9;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width: 100%; background-color: #f3f6f9;">
    <tr>
      <td align="center" style="padding: 28px 12px;">
        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width: 100%; max-width: 600px; overflow: hidden; border: 1px solid #dbe5ec; border-radius: 10px; background-color: #ffffff;">
          <tr>
            <td style="padding: 22px 30px; color: #ffffff; background-color: #0c5e86;">
              <div style="font-size: 24px; font-weight: 700; letter-spacing: 1.5px;">EFICENTRA</div>
              <div style="padding-top: 4px; font-size: 12px; color: #d9f3ff;">Monitoreo y eficiencia industrial</div>
            </td>
          </tr>
          <tr>
            <td style="padding: 30px;">
              <h1 style="margin: 0 0 22px; color: #0c5e86; font-size: 22px; line-height: 1.3;">${heading?html}</h1>
              <div style="color: #344665; font-size: 15px; line-height: 1.65;">
                <#nested>
              </div>
              <div style="margin-top: 28px; color: #586681; font-size: 14px;">&mdash; Equipo de Eficentra</div>
            </td>
          </tr>
        </table>
        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width: 100%; max-width: 600px;">
          <tr>
            <td align="center" style="padding: 18px 16px 0; color: #718096; font-size: 12px; line-height: 1.5;">
              Este correo fue enviado a <a href="mailto:${targetEmail?html}" style="color: #0c5e86; text-decoration: none;">${targetEmail?html}</a> por Eficentra.
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
</#macro>

<#macro action href label>
  <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin: 24px 0;">
    <tr>
      <td style="border-radius: 6px; background-color: #03aef3;">
        <a href="${href?html}" style="display: inline-block; padding: 12px 22px; color: #ffffff; font-size: 15px; font-weight: 700; text-decoration: none;">${label?html}</a>
      </td>
    </tr>
  </table>
</#macro>

<#macro notice tone="info">
  <#assign borderColor="#03aef3">
  <#assign backgroundColor="#eef9fe">
  <#if tone == "warning">
    <#assign borderColor="#f2994a">
    <#assign backgroundColor="#fff8f0">
  <#elseif tone == "danger">
    <#assign borderColor="#c62828">
    <#assign backgroundColor="#fff3f3">
  <#elseif tone == "success">
    <#assign borderColor="#2e7d32">
    <#assign backgroundColor="#f1f8f2">
  </#if>
  <div style="margin: 20px 0; padding: 14px 16px; border-left: 4px solid ${borderColor}; border-radius: 4px; background-color: ${backgroundColor};">
    <#nested>
  </div>
</#macro>

<#macro code value>
  <div style="margin: 22px 0; padding: 16px; border: 1px solid #bee1f2; border-radius: 8px; color: #0c5e86; background-color: #f4fbfe; font-family: monospace; font-size: 30px; font-weight: 700; letter-spacing: 6px; text-align: center;">${value?html}</div>
</#macro>
