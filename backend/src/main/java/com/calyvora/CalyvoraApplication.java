package com.calyvora;

import com.calyvora.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Calyvora backend — the platform foundation (Sprint 1).
 *
 * <p>Feature-organized (bounded-context) packages under {@code com.calyvora}: {@code common},
 * {@code auth}, {@code identity}, {@code company}, {@code invitation}, {@code email},
 * {@code dashboard}. See docs/Sprint1.md §6 and docs/14 §14.2.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CalyvoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalyvoraApplication.class, args);
    }
}
