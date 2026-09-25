package com.group8.eventservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.group8.eventservice.dto.request.CreateEventRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Bean Validation rules on event creation (S2-01.3). */
class CreateEventRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private CreateEventRequest valid() {
        LocalDateTime start = LocalDateTime.now().plusDays(7);
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Innovation Week Workshop");
        request.setScheduleStart(start);
        request.setScheduleEnd(start.plusHours(2));
        request.setCapacity(30);
        request.setEligibilityRule("{\"all\":true}");
        request.setRegistrationOpenAt(LocalDateTime.now());
        request.setRegistrationCloseAt(start.minusDays(1));
        return request;
    }

    private Set<String> violatedFields(CreateEventRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void blankTitleIsRejected() {
        CreateEventRequest request = valid();
        request.setTitle("  ");
        assertThat(violatedFields(request)).contains("title");
    }

    @Test
    void titleOver200CharactersIsRejected() {
        CreateEventRequest request = valid();
        request.setTitle("x".repeat(201));
        assertThat(violatedFields(request)).contains("title");
    }

    @Test
    void zeroAndNegativeCapacityAreRejected() {
        CreateEventRequest request = valid();
        request.setCapacity(0);
        assertThat(violatedFields(request)).contains("capacity");
        request.setCapacity(-5);
        assertThat(violatedFields(request)).contains("capacity");
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        CreateEventRequest request = new CreateEventRequest();
        assertThat(violatedFields(request)).contains("title", "scheduleStart", "scheduleEnd", "capacity",
                "eligibilityRule", "registrationOpenAt", "registrationCloseAt");
    }

    @Test
    void endBeforeStartIsRejected() {
        CreateEventRequest request = valid();
        request.setScheduleEnd(request.getScheduleStart().minusHours(1));
        assertThat(violatedFields(request)).contains("scheduleValid");
    }

    @Test
    void endEqualToStartIsRejected() {
        CreateEventRequest request = valid();
        request.setScheduleEnd(request.getScheduleStart());
        assertThat(violatedFields(request)).contains("scheduleValid");
    }

    @Test
    void registrationClosingAfterStartIsRejected() {
        CreateEventRequest request = valid();
        request.setRegistrationCloseAt(request.getScheduleStart().plusMinutes(1));
        assertThat(violatedFields(request)).contains("registrationWindowValid");
    }

    @Test
    void registrationClosingExactlyAtStartIsAllowed() {
        CreateEventRequest request = valid();
        request.setRegistrationCloseAt(request.getScheduleStart());
        assertThat(validator.validate(request)).isEmpty();
    }
}
