package com.gbf.granblue_simulator.controller;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ControllerAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of(e));
    }

    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter
    static class ErrorResponse {
        public String message;
        public String className;
        public String stackTrace; // 10개까지

        public static ErrorResponse of(Throwable throwable) {
            String message = throwable.getMessage();
            String className = throwable.getClass().getName();
            String stackTrace = Arrays.stream(throwable.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            return new ErrorResponse(message, className, stackTrace);
        }
    }

}
