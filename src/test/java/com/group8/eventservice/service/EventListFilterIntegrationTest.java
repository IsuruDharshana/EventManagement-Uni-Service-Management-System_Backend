package com.group8.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.group8.eventservice.dto.request.EventFilter;
import com.group8.eventservice.dto.response.EventResponse;
import com.group8.eventservice.entity.Event;
import com.group8.eventservice.entity.EventStatus;
import com.group8.eventservice.exception.ApiException;
import com.group8.eventservice.repository.EventRepository;

/**
 * Event list filters and paging against the real MySQL schema. Test events are placed in 2031 and
 * every query is limited to that year, so other rows in the database never affect the result.
 * Rolled back after each test.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/event_service_db}",
        "spring.datasource.username=${DB_USERNAME:group8}",
        "spring.datasource.password=${DB_PASSWORD:group8}",
        "events.auto-complete.enabled=false"
})
@Transactional
class EventListFilterIntegrationTest {

    private static final LocalDateTime YEAR_START = LocalDateTime.of(2031, 1, 1, 0, 0);
    private static final LocalDateTime YEAR_END = LocalDateTime.of(2031, 12, 31, 23, 59);

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void createEvents() {
        save("Jan published (org 1)", 1, EventStatus.PUBLISHED, "usr-filter-org-1");
        save("Feb draft (org 1)", 2, EventStatus.DRAFT, "usr-filter-org-1");
        save("Mar published (org 2)", 3, EventStatus.PUBLISHED, "usr-filter-org-2");
        save("Apr draft (org 2)", 4, EventStatus.DRAFT, "usr-filter-org-2");
        save("May cancelled (org 2)", 5, EventStatus.CANCELLED, "usr-filter-org-2");
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private void save(String title, int month, EventStatus status, String organizer) {
        LocalDateTime start = LocalDateTime.of(2031, month, 10, 10, 0);
        eventRepository.saveAndFlush(Event.builder()
                .title(title).organizerId(organizer).online(true)
                .scheduleStart(start).scheduleEnd(start.plusHours(2)).capacity(10)
                .eligibilityRule("{\"all\": true}")
                .registrationOpenAt(start.minusDays(30)).registrationCloseAt(start)
                .status(status).build());
    }

    private void loginAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private List<String> titles(EventFilter filter) {
        return eventService.listVisibleEvents(filter).events().stream().map(EventResponse::title).toList();
    }

    private static EventFilter in2031(EventStatus status, boolean mine, Integer page, Integer size) {
        return new EventFilter(status, YEAR_START, YEAR_END, false, mine, page, size);
    }

    @Test
    void studentSeesOnlyPublishedEventsInStartOrder() {
        loginAs("usr-filter-student", "STUDENT");

        assertThat(titles(in2031(null, false, null, null)))
                .containsExactly("Jan published (org 1)", "Mar published (org 2)");
    }

    @Test
    void organizerAlsoSeesTheirOwnDraftsButNotOthers() {
        loginAs("usr-filter-org-1", "EVENT_ORGANIZER");

        assertThat(titles(in2031(null, false, null, null)))
                .containsExactly("Jan published (org 1)", "Feb draft (org 1)", "Mar published (org 2)");
        assertThat(titles(in2031(null, true, null, null)))
                .containsExactly("Jan published (org 1)", "Feb draft (org 1)");
        assertThat(titles(in2031(EventStatus.DRAFT, false, null, null)))
                .containsExactly("Feb draft (org 1)");
    }

    @Test
    void adminSeesEverythingAndCanFilterByStatus() {
        loginAs("usr-admin-001", "ADMIN");

        assertThat(titles(in2031(null, false, null, null))).hasSize(5);
        assertThat(titles(in2031(EventStatus.CANCELLED, false, null, null))).containsExactly("May cancelled (org 2)");
    }

    @Test
    void dateRangeIsInclusiveOfStartTimes() {
        loginAs("usr-admin-001", "ADMIN");
        EventFilter febToApr = new EventFilter(null, LocalDateTime.of(2031, 2, 10, 10, 0),
                LocalDateTime.of(2031, 4, 10, 10, 0), false, false, null, null);

        assertThat(titles(febToApr)).containsExactly("Feb draft (org 1)", "Mar published (org 2)", "Apr draft (org 2)");
    }

    @Test
    void pagesAreOrderedAndReportTheTotal() {
        loginAs("usr-admin-001", "ADMIN");

        EventService.EventPage first = eventService.listVisibleEvents(in2031(null, false, 0, 2));
        EventService.EventPage last = eventService.listVisibleEvents(in2031(null, false, 2, 2));

        assertThat(first.total()).isEqualTo(5);
        assertThat(first.events()).extracting(EventResponse::title)
                .containsExactly("Jan published (org 1)", "Feb draft (org 1)");
        assertThat(last.events()).extracting(EventResponse::title).containsExactly("May cancelled (org 2)");
    }

    @Test
    void upcomingHidesEventsThatAlreadyStarted() {
        loginAs("usr-admin-001", "ADMIN");
        save("Started yesterday", 1, EventStatus.PUBLISHED, "usr-filter-org-1");
        Event past = eventRepository.findAll().stream().filter(e -> e.getTitle().equals("Started yesterday")).findFirst().orElseThrow();
        past.setScheduleStart(LocalDateTime.now().minusDays(1));
        past.setScheduleEnd(LocalDateTime.now().plusDays(1));
        eventRepository.saveAndFlush(past);

        EventFilter upcoming = new EventFilter(null, null, null, true, true, null, null);
        loginAs("usr-filter-org-1", "EVENT_ORGANIZER");

        assertThat(titles(upcoming)).containsExactly("Jan published (org 1)", "Feb draft (org 1)");
    }

    @Test
    void rejectsBadPagingAndReversedDates() {
        loginAs("usr-admin-001", "ADMIN");

        for (EventFilter bad : List.of(in2031(null, false, -1, 10), in2031(null, false, 0, 0), in2031(null, false, 0, 101),
                new EventFilter(null, YEAR_END, YEAR_START, false, false, null, null))) {
            assertThatThrownBy(() -> eventService.listVisibleEvents(bad)).isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode()).isEqualTo("VALIDATION_ERROR");
        }
    }
}
