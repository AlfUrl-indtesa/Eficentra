/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.mail;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class EficentraMailTemplateTest {

    private static final String TARGET_EMAIL = "usuario@eficentra.test";

    private static Configuration configuration;

    @BeforeAll
    static void setUpConfiguration() {
        configuration = new Configuration(Configuration.VERSION_2_3_32);
        configuration.setClassLoaderForTemplateLoading(
                EficentraMailTemplateTest.class.getClassLoader(), "templates");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    @Test
    void rendersEveryBrandedTransactionalTemplate() throws Exception {
        List<TemplateCase> cases = List.of(
                template("test.ftl"),
                template("activation.ftl",
                        "activationLink", "https://eficentra.test/activate?token=abc&source=email",
                        "activationLinkTtlInHours", 24),
                template("account.activated.ftl",
                        "loginLink", "https://eficentra.test/login"),
                template("reset.password.ftl",
                        "passwordResetLink", "https://eficentra.test/reset?token=abc&source=email",
                        "passwordResetLinkTtlInHours", 2),
                template("password.was.reset.ftl",
                        "loginLink", "https://eficentra.test/login"),
                template("account.lockout.ftl",
                        "lockoutAccount", "cuenta@eficentra.test",
                        "maxFailedLoginAttempts", 5),
                template("2fa.verification.code.ftl",
                        "code", "123456",
                        "expirationTimeSeconds", 300),
                template("state.enabled.ftl",
                        "apiFeature", "envío de correos",
                        "apiLabel", "enviar"),
                template("state.warning.ftl",
                        "apiFeature", "envío de correos",
                        "apiValueLabel", "ha consumido el 90 % del límite"),
                template("state.disabled.ftl",
                        "apiFeature", "envío de correos",
                        "apiLimitValueLabel", "superó el límite permitido")
        );

        for (TemplateCase templateCase : cases) {
            String rendered = render(templateCase);

            assertThat(rendered)
                    .as(templateCase.name())
                    .contains("EFICENTRA")
                    .contains("Equipo de Eficentra")
                    .contains(TARGET_EMAIL)
                    .contains("<html")
                    .contains("</html>")
                    .doesNotContain("media.thingsboard.io")
                    .doesNotContain("ThingsBoard")
                    .doesNotContain("Thingsboard")
                    .doesNotContain("${");
        }
    }

    @Test
    void usesEficentraInEveryTransactionalSubject() throws Exception {
        Properties subjects = new Properties();
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream("i18n/messages.properties")) {
            assertThat(stream).isNotNull();
            subjects.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        assertThat(subjects).hasSize(8);
        assertThat(subjects.stringPropertyNames()).allSatisfy(key ->
                assertThat(subjects.getProperty(key))
                        .startsWith("Eficentra - ")
                        .doesNotContain("ThingsBoard")
                        .doesNotContain("Thingsboard"));
    }

    private static TemplateCase template(String name, Object... entries) {
        Map<String, Object> model = new HashMap<>();
        model.put("targetEmail", TARGET_EMAIL);
        for (int i = 0; i < entries.length; i += 2) {
            model.put((String) entries[i], entries[i + 1]);
        }
        return new TemplateCase(name, model);
    }

    private static String render(TemplateCase templateCase) throws Exception {
        Template template = configuration.getTemplate(templateCase.name());
        StringWriter writer = new StringWriter();
        template.process(templateCase.model(), writer);
        return writer.toString();
    }

    private record TemplateCase(String name, Map<String, Object> model) {
    }

}
