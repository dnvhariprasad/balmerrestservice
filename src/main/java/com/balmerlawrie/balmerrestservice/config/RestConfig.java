package com.balmerlawrie.balmerrestservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${http.client.connect-timeout-seconds:10}")
    private long connectTimeoutSeconds;

    @Value("${http.client.read-timeout-seconds:120}")
    private long readTimeoutSeconds;

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
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
    }
}
