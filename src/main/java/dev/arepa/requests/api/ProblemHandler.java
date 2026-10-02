package dev.arepa.requests.api;

import dev.arepa.requests.application.*;
import dev.arepa.requests.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ProblemHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);
    @ExceptionHandler({DomainException.class, MethodArgumentNotValidException.class, MissingRequestHeaderException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error instanceof DomainException ? error.getMessage() : "Invalid body, parameter or required header");
    }
    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage()); }
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail missing(NotFoundException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage()); }
    @ExceptionHandler(Exception.class)
    ProblemDetail failure(Exception error) {
        LOG.error("Request processing failed: {}", error.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal processing error");
    }
}
