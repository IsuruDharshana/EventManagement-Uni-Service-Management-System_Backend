package com.group8.eventservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.group8.eventservice.config.JwtConfig;
import com.group8.eventservice.config.SecurityConfig;

/**
 * Verifies the security filter chain's 401/403 behavior against a throwaway protected
 * controller ({@link TestProtectedController}), since no real business endpoints exist yet.
 */
@WebMvcTest(controllers = TestProtectedController.class)
@Import({SecurityConfig.class, JwtConfig.class, DevTokenIssuer.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@ActiveProfiles("dev")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevTokenIssuer tokenIssuer;

    private String tokenFor(String... roles) {
        return tokenIssuer.issue("usr-test-001", List.of(roles));
    }

    @Test
    void rejectsRequestWithNoTokenAsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/test/any-authenticated"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsRequestWithValidTokenRegardlessOfRoleWhenOnlyAuthenticationRequired() throws Exception {
        mockMvc.perform(get("/api/test/any-authenticated")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsWrongRoleAsForbidden() throws Exception {
        mockMvc.perform(get("/api/test/organizer-only")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsCorrectRole() throws Exception {
        mockMvc.perform(get("/api/test/organizer-only")
                        .header("Authorization", "Bearer " + tokenFor("EVENT_ORGANIZER")))
                .andExpect(status().isOk());
    }

    @Test
    void anyOfSeveralRolesIsEnough() throws Exception {
        mockMvc.perform(get("/api/test/organizer-only")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT", "EVENT_ORGANIZER")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMalformedTokenAsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/test/any-authenticated")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
