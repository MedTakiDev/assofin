package com.association.assofin.config;

import com.association.assofin.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the local environment setup. Plain unit test: it only reads files.
 *
 * <p>Several tools parse .env differently, and Spring reads it as a properties file, so a format
 * mistake or a missing key only shows up later as a confusing startup failure.
 */
class LocalEnvironmentContractTest {

    private static final Path ENV_EXAMPLE = Path.of("..", ".env.example");

    private static final Path COMPOSE_FILE = Path.of("..", "compose.yaml");

    /** ${NAME} without a default value: the application can't start unless NAME is provided. */
    private static final Pattern REQUIRED_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private static final Pattern VALID_LINE = Pattern.compile("^[A-Z][A-Z0-9_]*=[^\\s\"'#]*$");

    @Test
    void onlyTheDevProfileReadsTheEnvFile() throws IOException {
        assertThat(load("application-dev.yml").keySet()).contains("spring.config.import[0]");
        assertThat(load("application-dev.yml").values())
                .contains("optional:file:.env[.properties]", "optional:file:../.env[.properties]");

        assertThat(load("application.yml").keySet()).noneMatch(key -> key.startsWith("spring.config.import"));
        assertThat(load("application-prod.yml").keySet()).noneMatch(key -> key.startsWith("spring.config.import"));
    }

    @Test
    void theProfileComesFromTheVariableWithoutAFallback() throws IOException {
        // ${SPRING_PROFILES_ACTIVE:dev} would silently start production as dev, and no other test
        // would notice, because tests choose their own profile with @ActiveProfiles.
        assertThat(load("application.yml")).containsEntry("spring.profiles.active", "${SPRING_PROFILES_ACTIVE}");

        for (String file : List.of("application-dev.yml", "application-prod.yml")) {
            assertThat(load(file).keySet())
                    .as(file)
                    .noneMatch(key -> key.startsWith("spring.profiles.active") || key.startsWith("spring.profiles.default"));
        }
    }

    @Test
    void theExampleFileUsesTheDevProfile() throws IOException {
        // .env is for local development only. An empty value silently starts without a profile, and
        // prod breaks local runs. Production sets its profile on the deployment platform.
        assertThat(exampleLines()).contains("SPRING_PROFILES_ACTIVE=dev");
    }

    @Test
    void everyRequiredVariableIsDocumentedInTheExampleFile() throws IOException {
        Set<String> documented = exampleLines().stream()
                .map(line -> line.substring(0, line.indexOf('=')))
                .collect(Collectors.toSet());

        Map<String, Object> config = new HashMap<>(load("application.yml"));
        config.putAll(load("application-dev.yml"));

        for (Object value : config.values()) {
            Matcher matcher = REQUIRED_PLACEHOLDER.matcher(String.valueOf(value));
            while (matcher.find()) {
                assertThat(documented).as("missing from .env.example").contains(matcher.group(1));
            }
        }
    }

    @Test
    void theExampleFileFollowsTheFormatRules() throws IOException {
        // A value with a trailing "# comment" would reach Spring as part of the value.
        assertThat(exampleLines()).isNotEmpty().allMatch(line -> VALID_LINE.matcher(line).matches());
    }

    @Test
    void developmentAndTestsUseTheSamePostgresImage() throws IOException {
        // Otherwise the tests can pass on a database version nobody develops against.
        assertThat(loadCompose()).containsEntry("services.postgres.image", TestcontainersConfiguration.POSTGRES_IMAGE);
    }

    @Test
    void everyPublishedPortIsReachableFromThisMachineOnly() throws IOException {
        // Docker publishes ports past the host firewall, and a port without an address listens on
        // every network interface.
        Map<String, Object> ports = new HashMap<>(loadCompose());
        ports.keySet().removeIf(key -> !key.matches("services\\..+\\.ports\\[\\d+]"));

        assertThat(ports).isNotEmpty();
        assertThat(ports.values()).allMatch(port -> String.valueOf(port).startsWith("127.0.0.1:"));
    }

    /** Setting lines only: comments and blank lines are dropped. */
    private static List<String> exampleLines() throws IOException {
        return Files.readAllLines(ENV_EXAMPLE).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    private static Map<String, Object> load(String file) throws IOException {
        return load(new ClassPathResource(file));
    }

    private static Map<String, Object> loadCompose() throws IOException {
        return load(new FileSystemResource(COMPOSE_FILE));
    }

    private static Map<String, Object> load(Resource file) throws IOException {
        Map<String, Object> properties = new HashMap<>();
        for (var source : new YamlPropertySourceLoader().load(file.getDescription(), file)) {
            EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
            for (String name : enumerable.getPropertyNames()) {
                properties.put(name, String.valueOf(enumerable.getProperty(name)));
            }
        }
        return properties;
    }
}
