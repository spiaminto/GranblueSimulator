package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.exception.MoveValidationException;
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
    public ResponseEntity<ExceptionResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.internalServerError().body(ExceptionResponse.of(e));
    }

    @ExceptionHandler(MoveValidationException.class)
    public ResponseEntity<ErrorResponse> handleMoveValidationException(MoveValidationException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage()));
    }

    /**
     * 일반 사용자에게 보여줄 에러 결과
     */
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter
    static class ErrorResponse {
        public String message;

        public static ErrorResponse of(String message) {
            return new ErrorResponse(message);
        }
    }

    /**
     * 개발용 에러 결과
     */
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter
    static class ExceptionResponse {
        public String message;
        public String className;
        public String stackTrace; // 10개까지

        public static ExceptionResponse of(Throwable throwable) {
            String message = throwable.getMessage();
            String className = throwable.getClass().getName();
            String stackTrace = Arrays.stream(throwable.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            return new ExceptionResponse(message, className, stackTrace);
        }
    }

}
