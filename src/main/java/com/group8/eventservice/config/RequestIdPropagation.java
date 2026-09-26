package com.group8.eventservice.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/** Carries the current request id onto outgoing HTTP calls and into background (@Async) work. */
@Configuration
public class RequestIdPropagation {

    /** Adds X-Request-ID to calls made through the shared RestClient.Builder. */
    public static ClientHttpRequestInterceptor outgoingRequestId() {
        return (request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null && !request.getHeaders().containsHeader(RequestIdFilter.HEADER)) {
                request.getHeaders().set(RequestIdFilter.HEADER, requestId);
            }
            return execution.execute(request, body);
        };
    }

    /** Picked up by Spring Boot's task executor so notifications sent in the background keep the id in their logs. */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                try {
                    task.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
