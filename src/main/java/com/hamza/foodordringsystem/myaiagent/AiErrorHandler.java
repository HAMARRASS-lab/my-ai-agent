package com.hamza.foodordringsystem.myaiagent;

import com.openai.errors.RateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiErrorHandler {
    @ExceptionHandler(com.openai.errors.RateLimitException.class)
    public ResponseEntity<String> handleRateLimit(RateLimitException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The AI assistant is temporarily unavailable. Please try again later.");
    }
}