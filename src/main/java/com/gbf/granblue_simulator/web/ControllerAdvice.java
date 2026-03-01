package com.gbf.granblue_simulator.web;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.exception.ChatException;
import com.gbf.granblue_simulator.battle.exception.DamageValidationException;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.TransactionException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.HashMap;
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
        printError(e);
        battleContext.print();
        return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class) // 뷰 반환용
    public ModelAndView handleIllegalStateException(IllegalStateException e) {
        log.error("[handleIllegalStateException] message={}", e.getMessage());
        printError(e);
        battleContext.print();
        ModelAndView mav = new ModelAndView("error/5xx");
        mav.addObject("errorMessage", e.getMessage());
        return mav;
    }

    /**
     * 구조상 발생 안할텐데... 일단 방어적으로 작성
     * @param e
     * @return
     */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleCannotAcquireLockException(Exception e) {
        log.error("[handleCannotAcquireLockException] message={}", e.getMessage());
        printError(e);
        battleContext.print();
        return ResponseEntity.internalServerError().body(Map.of("code", "TRANSACTION_LOCK_ERROR", "message", "트랜잭션 락 처리 실패. 다시 시도 해주세요."));
    }

    /**
     * BattleCommandService 의 커맨드 처리 메서드에 붙은 @Transactional(timeout) 에서 timeout 발생시 handle
     *
     * @param e
     * @return
     */
    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleHibernateTransactionException(TransactionException e) {
        log.error("[handleHibernateTransactionException] message={}", e.getMessage());
        printError(e);
        battleContext.print();

        Map<String, Object> body = new HashMap<>();

        // timeout 인지 확인
        boolean isTimeout = e.getMessage() != null && e.getMessage().contains("timeout expired"); // "transaction timeout expired"
        // CHECK 왜인지 TransactionTimedOutException.class 이쪽으로 변환되지 않고 그대로 반환되서 어쩔수 없이 이렇게 사용 (해결방법 없는것으로 보임)
        if (isTimeout) {
            body.put("code", "COMMAND_TIMEOUT");
            body.put("message", "커맨드 처리 시간이 초과되었습니다. 다시 시도해주세요.");
        } else {
            body.put("code", "COMMAND_TRANSACTION_ERROR");
            body.put("message", "커맨드 처리중 오류가 발생했습니다. 다시 시도해주세요");
        }

        return ResponseEntity.internalServerError().body(body);
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> handleChatException(ChatException e) {
        log.error("[handleChatException] message= {}", e.getMessage());
        log.error("[handleChatException] member = {}", battleContext.getMember());
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "code", e.getCode()));
    }

    @ExceptionHandler(MoveValidationException.class)
    public ResponseEntity<Map<String, Object>> handleMoveValidationException(MoveValidationException e) {
        log.error("[handleMoveValidationException] isConditionFailed = {}, message={}", e.isConditionFailed(), e.getMessage());
        String code = e.isConditionFailed() ? "MOVE_VALIDATION_CONDITION_FAILED" : "MOVE_VALIDATION_FAILED";
        battleContext.print();
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "code", code));
    }

    @ExceptionHandler(MoveProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleMoveProcessingException(MoveProcessingException e) {
        log.error("[handleMoveProcessingException] message={}, code = {}", e.getMessage(), e.getCode());
        battleContext.print();
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "code", e.getCode()));
    }

    @ExceptionHandler(DamageValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDamageValidationException(DamageValidationException e) {
        log.error("[handleDamageValidationException] message={}", e.getMessage());
        battleContext.print();
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "code", "DAMAGE_VALIDATION_FAILED"));
    }

    private void printError(Exception e) {
        String stackTrace = Arrays.stream(e.getStackTrace())
                .limit(30)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
        log.error("[printError] stackTrace={}", stackTrace);
    }


}
