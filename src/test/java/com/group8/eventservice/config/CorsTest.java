package com.group8.eventservice.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.group8.eventservice.security.DevTokenIssuer;
import com.group8.eventservice.security.JwtAuthFilter;
import com.group8.eventservice.security.RestAccessDeniedHandler;
import com.group8.eventservice.security.RestAuthenticationEntryPoint;
import com.group8.eventservice.security.TestProtectedController;

@WebMvcTest(controllers = TestProtectedController.class)
@Import({SecurityConfig.class, JwtConfig.class, DevTokenIssuer.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, RequestIdFilter.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = "cors.allowed-origins=https://usm-frontend.example, http://localhost:5173")
class CorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromTheFrontendIsAllowedWithoutAToken() throws Exception {
        mockMvc.perform(options("/api/test/any-authenticated")
                        .header("Origin", "https://usm-frontend.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://usm-frontend.example"));
    }

    @Test
    void preflightFromAnUnknownSiteIsRejected() throws Exception {
        mockMvc.perform(options("/api/test/any-authenticated")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorResponsesStillCarryCorsAndRequestIdHeaders() throws Exception {
        mockMvc.perform(get("/api/test/any-authenticated")
                        .header("Origin", "http://localhost:5173")
                        .header(RequestIdFilter.HEADER, "gw-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string(RequestIdFilter.HEADER, "gw-1"));
    }
}
