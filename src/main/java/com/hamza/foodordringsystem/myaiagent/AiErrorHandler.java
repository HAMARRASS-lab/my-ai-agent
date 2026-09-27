package com.hamza.foodordringsystem.myaiagent;

import com.openai.errors.OpenAIException;
import com.openai.errors.RateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Extends ResponseEntityExceptionHandler so standard MVC errors (missing param, unknown path, ...)
// keep their 4xx status instead of falling into the 500 catch-all below.
@RestControllerAdvice
public class AiErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiErrorHandler.class);

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<String> handleRateLimit(RateLimitException e) {
        log.warn("OpenAI rate limit hit", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The AI assistant is temporarily unavailable. Please try again later.");
    }

    // Catches every other OpenAI SDK error (auth, bad request, timeout, 5xx from
    // OpenAI, etc.) so none of them ever leak a raw stack trace to the client.
    @ExceptionHandler(OpenAIException.class)
    public ResponseEntity<String> handleOpenAiError(OpenAIException e) {
        log.error("OpenAI request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("The AI assistant could not process your request right now. Please try again later.");
    }

    // Last-resort catch-all so unexpected errors never leak internals either.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Something went wrong. Please try again later.");
    }
}
