package com.example.projectname.apps.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core workers always ready to go
        executor.setCorePoolSize(5);

        // Max workers if the queue gets full
        executor.setMaxPoolSize(10);

        // How many emails can wait in line before we start rejecting them
        executor.setQueueCapacity(500);

        // Thread names for easier debugging in logs
        executor.setThreadNamePrefix("EmailThread-");

        executor.initialize();
        return executor;
    }
}
