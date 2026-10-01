package com.calyvora.common.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Runs the app against a throwaway <em>embedded</em> Postgres — no Docker, no external database.
 * Activated with the {@code embedded} Spring profile:
 *
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=embedded</pre>
 *
 * Data does not survive a restart; this is a local-dev / demo convenience only. Real environments
 * use the configured Postgres (infra/docker-compose.yml or a managed instance).
 */
@Configuration
@Profile("embedded")
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    @Bean
    @Primary
    @FlywayDataSource
    public DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        return embeddedPostgres.getPostgresDatabase();
    }
}
