package com.group8.eventservice.exception;

/** Standard error envelope for every non-2xx response (Section 3.3 of the backend guide). */
public record ApiErrorResponse(boolean success, ErrorDetail error) {

    public record ErrorDetail(String code, String message) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorDetail(code, message));
    }

    /**
     * Renders this fixed, known shape as JSON directly, without depending on whichever
     * Jackson major version happens to be auto-configured (Spring Boot 4 defaults to
     * {@code tools.jackson}, while some libraries still pull in {@code com.fasterxml.jackson}).
     */
    public String toJson() {
        return "{\"success\":false,\"error\":{\"code\":\"" + escape(error.code())
                + "\",\"message\":\"" + escape(error.message()) + "\"}}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
