package com.group8.eventservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.group8.eventservice.config.JwtConfig;
import com.group8.eventservice.config.SecurityConfig;
import com.group8.eventservice.dto.response.EventResponse;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.GlobalExceptionHandler;
import com.group8.eventservice.security.JwtAuthFilter;
import com.group8.eventservice.security.DevTokenIssuer;
import com.group8.eventservice.security.RestAccessDeniedHandler;
import com.group8.eventservice.security.RestAuthenticationEntryPoint;
import com.group8.eventservice.service.EventService;

@WebMvcTest(controllers = EventController.class)
@Import({SecurityConfig.class, JwtConfig.class, DevTokenIssuer.class, JwtAuthFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@ActiveProfiles("dev")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevTokenIssuer tokenIssuer;

    @MockitoBean
    private EventService eventService;

    private String tokenFor(String role) {
        return tokenIssuer.issue("usr-test-001", List.of(role));
    }

    @Test
    void createEventRejectsBlankTitleWithFieldLevelMessage() throws Exception {
        String body = """
                {
                  "title": "",
                  "scheduleStart": "2027-01-01T10:00:00",
                  "scheduleEnd": "2027-01-01T12:00:00",
                  "capacity": 10,
                  "eligibilityRule": "{\\"all\\":true}",
                  "registrationOpenAt": "2026-12-01T00:00:00",
                  "registrationCloseAt": "2026-12-31T00:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenFor("EVENT_ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    void createEventRejectsWrongRole() throws Exception {
        String body = """
                {
                  "title": "Valid Title",
                  "scheduleStart": "2027-01-01T10:00:00",
                  "scheduleEnd": "2027-01-01T12:00:00",
                  "capacity": 10,
                  "eligibilityRule": "{\\"all\\":true}",
                  "registrationOpenAt": "2026-12-01T00:00:00",
                  "registrationCloseAt": "2026-12-31T00:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createEventSucceedsForOrganizer() throws Exception {
        EventResponse response = new EventResponse(UUID.randomUUID(), "Valid Title", null, "usr-test-001",
                null, false, LocalDateTime.now(), LocalDateTime.now().plusHours(1), 10, "{\"all\":true}",
                LocalDateTime.now(), LocalDateTime.now(), EventStatus.DRAFT, LocalDateTime.now(), LocalDateTime.now());
        when(eventService.createEvent(any())).thenReturn(response);

        String body = """
                {
                  "title": "Valid Title",
                  "scheduleStart": "2027-01-01T10:00:00",
                  "scheduleEnd": "2027-01-01T12:00:00",
                  "capacity": 10,
                  "eligibilityRule": "{\\"all\\":true}",
                  "registrationOpenAt": "2026-12-01T00:00:00",
                  "registrationCloseAt": "2026-12-31T00:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenFor("EVENT_ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Valid Title"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createEventAllowsAcademicStaffAndAdmin() throws Exception {
        EventResponse response = new EventResponse(UUID.randomUUID(), "Valid Title", null, "usr-test-001",
                null, false, LocalDateTime.now(), LocalDateTime.now().plusHours(1), 10, "{\"all\":true}",
                LocalDateTime.now(), LocalDateTime.now(), EventStatus.DRAFT, LocalDateTime.now(), LocalDateTime.now());
        when(eventService.createEvent(any())).thenReturn(response);
        String body = """
                {
                  "title": "Valid Title",
                  "scheduleStart": "2027-01-01T10:00:00",
                  "scheduleEnd": "2027-01-01T12:00:00",
                  "capacity": 10,
                  "eligibilityRule": "{\\"all\\":true}",
                  "registrationOpenAt": "2026-12-01T00:00:00",
                  "registrationCloseAt": "2026-12-31T00:00:00"
                }
                """;

        for (String role : List.of("ACADEMIC_STAFF", "ADMIN")) {
            mockMvc.perform(post("/api/events")
                            .header("Authorization", "Bearer " + tokenFor(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void administrativeStaffCannotCreateEvents() throws Exception {
        String body = """
                {
                  "title": "Valid Title",
                  "scheduleStart": "2027-01-01T10:00:00",
                  "scheduleEnd": "2027-01-01T12:00:00",
                  "capacity": 10,
                  "eligibilityRule": "{\\"all\\":true}",
                  "registrationOpenAt": "2026-12-01T00:00:00",
                  "registrationCloseAt": "2026-12-31T00:00:00"
                }
                """;

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + tokenFor("ADMINISTRATIVE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void listEventsIsReachableByAnyAuthenticatedUser() throws Exception {
        when(eventService.listVisibleEvents()).thenReturn(List.of());

        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + tokenFor("STUDENT")))
                .andExpect(status().isOk());
    }
}
