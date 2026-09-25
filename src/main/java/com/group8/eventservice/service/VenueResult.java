package com.group8.eventservice.service;

/** Outcome of a Group 6 venue validation. Never throws — SERVICE_UNAVAILABLE covers any outage/timeout/bad reply. */
public record VenueResult(Status status, String message) {

    public enum Status { VALID, NOT_FOUND, NOT_AVAILABLE, SERVICE_UNAVAILABLE }

    public static VenueResult valid() {
        return new VenueResult(Status.VALID, null);
    }

    public static VenueResult notFound(String message) {
        return new VenueResult(Status.NOT_FOUND, message);
    }

    public static VenueResult notAvailable(String message) {
        return new VenueResult(Status.NOT_AVAILABLE, message);
    }

    public static VenueResult serviceUnavailable() {
        return new VenueResult(Status.SERVICE_UNAVAILABLE, null);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public boolean isServiceAvailable() {
        return status != Status.SERVICE_UNAVAILABLE;
    }
}
