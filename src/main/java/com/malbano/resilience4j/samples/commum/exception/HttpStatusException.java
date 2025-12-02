package com.malbano.resilience4j.samples.commum.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class HttpStatusException extends RuntimeException {
    
    private final HttpStatus httpStatus;
    
    public HttpStatusException(HttpStatus httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }
    
    public HttpStatusException(HttpStatus httpStatus) {
        super("HTTP " + httpStatus.value() + " - " + httpStatus.getReasonPhrase());
        this.httpStatus = httpStatus;
    }
}