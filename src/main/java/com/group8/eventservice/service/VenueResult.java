package com.group8.eventservice.service;

/** Outcome of a Group 6 venue-availability check. Never throws — UNAVAILABLE covers any outage/timeout. */
public record VenueResult(Status status) {

    public enum Status { AVAILABLE, OCCUPIED, UNAVAILABLE }

    public static VenueResult available() {
        return new VenueResult(Status.AVAILABLE);
    }

    public static VenueResult occupied() {
        return new VenueResult(Status.OCCUPIED);
    }

    public static VenueResult unavailable() {
        return new VenueResult(Status.UNAVAILABLE);
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    public boolean isServiceAvailable() {
        return status != Status.UNAVAILABLE;
    }
}
