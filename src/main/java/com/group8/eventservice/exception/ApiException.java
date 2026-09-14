package com.group8.eventservice.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/** Business-rule failure carrying a machine-readable code and the HTTP status to respond with. */
@Getter
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
