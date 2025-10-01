package com.smotrilka.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onException(Exception ex) {
        log.error("Unhandled exception:", ex);
        return ResponseEntity.status(500).body(Map.of(
                "error", "Internal Server Error",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> onBadArg(IllegalArgumentException ex) {
        log.warn("Bad request:", ex);
        return ResponseEntity.badRequest().body(Map.of("error", "Bad Request", "message", ex.getMessage()));
    }
}