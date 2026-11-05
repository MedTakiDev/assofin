package com.association.assofin.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the value each profile really uses, read from the real YAML files, so changing a setting
 * in those files fails here. Plain unit test: only this setting is bound, nothing else starts.
 */
class ApiDocsPropertiesTest {

    @Test
    void docsAreHiddenWithoutConfiguration() throws IOException {
        assertThat(propertiesFor(null).exposeApiDocs()).isFalse();
    }

    @Test
    void devProfileExposesTheDocs() throws IOException {
        assertThat(propertiesFor("application-dev.yml").exposeApiDocs()).isTrue();
    }

    @Test
    void prodProfileHidesTheDocs() throws IOException {
        assertThat(propertiesFor("application-prod.yml").exposeApiDocs()).isFalse();
    }

    private static ApiDocsProperties propertiesFor(String profileFile) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        if (profileFile != null) {
            new YamlPropertySourceLoader()
                    .load(profileFile, new ClassPathResource(profileFile))
                    .forEach(environment.getPropertySources()::addFirst);
        }
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bindOrCreate("application.security", ApiDocsProperties.class);
    }
}
