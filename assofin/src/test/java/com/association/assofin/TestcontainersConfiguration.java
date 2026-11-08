package com.association.assofin;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway PostgreSQL (same image as compose.yaml) and wires the datasource to it
 * through {@code @ServiceConnection}. Import it with {@code @Import(TestcontainersConfiguration.class)}
 * together with {@code @ActiveProfiles("test")}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Must match compose.yaml: LocalEnvironmentContractTest fails if they differ. */
    public static final String POSTGRES_IMAGE = "postgres:17.11-trixie";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }
}
