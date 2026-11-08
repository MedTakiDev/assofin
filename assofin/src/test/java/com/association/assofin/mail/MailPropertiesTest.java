package com.association.assofin.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sender address is bound and validated the way Spring does it at startup. Only this one
 * properties class is started, nothing else.
 */
class MailPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void aValidSenderIsBound() {
        runner.withPropertyValues("application.mail.from=no-reply@assofin.org")
                .run(context -> assertThat(context.getBean(MailProperties.class).from())
                        .isEqualTo("no-reply@assofin.org"));
    }

    @Test
    void aMissingSenderStopsStartupAndNamesTheVariable() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("MAIL_FROM"));
    }

    @ParameterizedTest(name = "rejected: \"{0}\"")
    @ValueSource(strings = {" ", "no-reply", "no reply@assofin.org"})
    void aMalformedSenderStopsStartup(String from) {
        runner.withPropertyValues("application.mail.from=" + from)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailProperties.class)
    static class Config {
    }
}
