package com.gbf.granblue_simulator.web;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.exception.DamageValidationException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ControllerAdvice {

    private final BattleContext battleContext;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("[handleIllegalArgumentException] message={}", e.getMessage());
        String stackTrace = Arrays.stream(e.getStackTrace())
                .limit(30)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
        log.error("[handleIllegalArgumentException] stackTrace={}", stackTrace);
        battleContext.print();
        return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MoveValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMoveValidationException(MoveValidationException e) {
        log.error("[handleMoveValidationException] isConditionFailed = {}, message={}", e.isConditionFailed(), e.getMessage());
        battleContext.print();
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "isConditionFailed", e.isConditionFailed()));
    }

    @ExceptionHandler(DamageValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDamageValidationException(DamageValidationException e) {
        log.error("[handleDamageValidationException] message={}", e.getMessage());
        battleContext.print();
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }


}
