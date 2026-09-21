package com.group8.eventservice.service;

/** Outcome of a Group 5 eligibility check. Never throws — UNAVAILABLE covers any outage/timeout. */
public record EligibilityResult(Status status) {

    public enum Status { ELIGIBLE, INELIGIBLE, UNAVAILABLE }

    public static EligibilityResult eligible() {
        return new EligibilityResult(Status.ELIGIBLE);
    }

    public static EligibilityResult ineligible() {
        return new EligibilityResult(Status.INELIGIBLE);
    }

    public static EligibilityResult unavailable() {
        return new EligibilityResult(Status.UNAVAILABLE);
    }

    public boolean isEligible() {
        return status == Status.ELIGIBLE;
    }

    public boolean isAvailable() {
        return status != Status.UNAVAILABLE;
    }
}
