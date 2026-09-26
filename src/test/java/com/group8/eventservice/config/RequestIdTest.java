package com.group8.eventservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** X-Request-ID: accepted or created per request, logged, returned, and forwarded to other services. */
class RequestIdTest {

    private HttpServer server;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    private String filter(String incomingId, AtomicReference<String> seenDuringRequest) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        if (incomingId != null) {
            request.addHeader(RequestIdFilter.HEADER, incomingId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                        seenDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));
                    }
                });
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void reusesTheGatewaysRequestId() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        assertThat(filter("gw-7f3a-001", seen)).isEqualTo("gw-7f3a-001");
        assertThat(seen.get()).isEqualTo("gw-7f3a-001");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void createsAnIdWhenNoneOrAnUnsafeOneIsSent() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        String created = filter(null, seen);
        assertThat(created).hasSize(36).isEqualTo(seen.get());

        String replaced = filter("bad id\r\ninjected: yes", seen);
        assertThat(replaced).hasSize(36).doesNotContain("injected");
    }

    @Test
    void forwardsTheCurrentIdOnOutgoingCalls() throws IOException {
        List<String> received = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            received.add(String.valueOf(exchange.getRequestHeaders().getFirst(RequestIdFilter.HEADER)));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .requestInterceptor(RequestIdPropagation.outgoingRequestId())
                .build();

        MDC.put(RequestIdFilter.MDC_KEY, "gw-7f3a-001");
        client.get().uri("/ping").retrieve().toBodilessEntity();
        MDC.clear();
        client.get().uri("/ping").retrieve().toBodilessEntity();

        assertThat(received).containsExactly("gw-7f3a-001", "null");
    }

    @Test
    void backgroundTasksKeepTheRequestId() throws InterruptedException {
        AtomicReference<String> seen = new AtomicReference<>();
        MDC.put(RequestIdFilter.MDC_KEY, "gw-7f3a-001");
        Runnable decorated = new RequestIdPropagation().mdcTaskDecorator()
                .decorate(() -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        MDC.clear();

        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seen.get()).isEqualTo("gw-7f3a-001");
    }
}
