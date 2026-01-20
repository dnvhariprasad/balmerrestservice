package com.balmerlawrie.balmerrestservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST clients used throughout the application.
 * Provides a singleton RestTemplate with proper timeout configuration.
 */
@Configuration
public class RestConfig {

    /**
     * Creates a RestTemplate bean with connection and read timeouts.
     * This should be injected into services instead of creating new instances.
     *
     * @param builder RestTemplateBuilder provided by Spring Boot
     * @return Configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
