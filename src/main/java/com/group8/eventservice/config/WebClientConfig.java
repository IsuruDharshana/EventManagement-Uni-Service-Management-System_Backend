package com.group8.eventservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Shared RestClient.Builder for cross-service calls (Group 5/6, notifications): conservative
 * timeouts, and the current X-Request-ID forwarded on every call.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(RequestIdPropagation.outgoingRequestId());
    }
}
