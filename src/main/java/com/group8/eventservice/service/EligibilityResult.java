package com.group8.eventservice.service;

/**
 * Outcome of a Group 5 eligibility check. Never throws — UNAVAILABLE covers any outage/timeout.
 * {@code message} is Group 5's user-facing reason when the user is not eligible, otherwise null.
 */
public record EligibilityResult(Status status, String message) {

    public enum Status { ELIGIBLE, INELIGIBLE, INVALID_USER, UNAVAILABLE }

    public static EligibilityResult invalidUser() {
        return new EligibilityResult(Status.INVALID_USER, null);
    }

    public static EligibilityResult eligible() {
        return new EligibilityResult(Status.ELIGIBLE, null);
    }

    public static EligibilityResult ineligible() {
        return ineligible(null);
    }

    public static EligibilityResult ineligible(String message) {
        return new EligibilityResult(Status.INELIGIBLE, message);
    }

    public static EligibilityResult unavailable() {
        return new EligibilityResult(Status.UNAVAILABLE, null);
    }

    public boolean isEligible() {
        return status == Status.ELIGIBLE;
    }

    public boolean isAvailable() {
        return status != Status.UNAVAILABLE;
    }
}
