package com.calyvora.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * The one pool for work that outlives a request.
 *
 * <p>Deliberately small. This process shares a free-tier container and a ten-connection pool with
 * every live request; two payroll runs at once is the most it can afford without the people
 * clicking around in the meantime noticing. A third run waits in the queue rather than starting.
 *
 * <p>Nothing here binds a tenant. A thread from this pool has no request, no principal and no
 * {@code TenantContext}; whatever it runs must set the tenant itself before touching the database,
 * or Row-Level Security will — correctly — show it nothing.
 */
@Configuration
@EnableAsync
public class BackgroundWorkConfig {

    public static final String PAYROLL_EXECUTOR = "payrollExecutor";

    @Bean(PAYROLL_EXECUTOR)
    public ThreadPoolTaskExecutor payrollExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("payroll-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        // If even the queue is full, run it on the caller's thread rather than drop it: the request
        // gets slower, which is the old behaviour, instead of a job that silently never happens.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
