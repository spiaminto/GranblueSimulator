package com.gbf.granblue_simulator.web;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.exception.ChatException;
import com.gbf.granblue_simulator.battle.exception.DamageValidationException;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.web.mail.GmailSender;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ControllerAdvice {

    private final BattleContext battleContext;
    private final GmailSender gmailSender;

    @ExceptionHandler(RuntimeException.class) // 뷰 반환용
    public ModelAndView handleRuntimeException(RuntimeException e) {
        log.error("[handleRuntimeException] message={}", e.getMessage());
        printError(e);
        gmailSender.sendError(e.getMessage());
        battleContext.print();
        if (e.getMessage().contains("Batch update returned unexpected row count from update")) {
            throw new MoveProcessingException("커맨드 상태 처리중 오류가 발생했습니다. 새로고침해주세요.", "COMMAND_TRANSACTION_ERROR");
        }
        ModelAndView mav = new ModelAndView("error/5xx");
        mav.addObject("errorMessage", "서버 오류입니다. 타입스탬프: " + LocalDateTime.now());
        return mav;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("[handleIllegalArgumentException] message={}", e.getMessage());
        printError(e);
        gmailSender.sendError(e.getMessage());
        battleContext.print();
        return ResponseEntity.internalServerError().body(Map.of("message", "서버 오류입니다. 타임스탬프: " + LocalDateTime.now(), "code", "ILLEGAL_ARGUMENT_EXCEPTION"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ModelAndView handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        log.error("[handleIllegalStateException] message={}", e.getMessage());
        printError(e);
        battleContext.print();

        if ("/".equals(request.getServletPath())) {
            ModelAndView mav = new ModelAndView("error/5xx");
            mav.addObject("errorMessage", "홈 화면 접근에러입니다. 관리자에게 문의해주세요.");
            return mav;
        }

        request.getSession().setAttribute("errorMessage", e.getMessage());
        return new ModelAndView("redirect:/");
    }

    @ExceptionHandler(NullPointerException.class)
    public ModelAndView handleNullPointerException(NullPointerException e) {
        log.error("[handleNullPointerException] message={}", e.getMessage());
        printError(e);
        gmailSender.sendError(e.getMessage());
        battleContext.print();
        ModelAndView mav = new ModelAndView("error/5xx");
        mav.addObject("errorMessage", "서버 에러입니다. 타임스탬프: " + LocalDateTime.now());
        return mav;
    }

    /**
     * 구조상 발생 안할텐데... 일단 방어적으로 작성
     *
     * @param e
     * @return
     */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleCannotAcquireLockException(Exception e) {
        log.error("[handleCannotAcquireLockException] message={}", e.getMessage());
        printError(e);
        gmailSender.sendError(e.getMessage());
        battleContext.print();
        return ResponseEntity.internalServerError().body(Map.of("code", "TRANSACTION_LOCK_ERROR", "message", "트랜잭션 락 처리 실패. 다시 시도 해주세요."));
    }

    /**
     * BattleCommandService 의 커맨드 처리 메서드에 붙은 @Transactional(timeout) 에서 timeout 발생시 handle
     * 락 걸기 전에 터지면 hibernate.TransactionException, 락 걸고 터지면 JpaSystemException
     *
     * @param e
     */
    @ExceptionHandler({JpaSystemException.class, org.hibernate.TransactionException.class})
    public ResponseEntity<Map<String, Object>> TransactionTimeoutException(JpaSystemException e) {
        log.error("[TransactionTimeoutException] message={}", e.getMessage());
        printError(e);
        gmailSender.sendError(e.getMessage());
        battleContext.print();

        Map<String, Object> body = new HashMap<>();

        // timeout 인지 확인
        boolean isTimeout = e.getMessage() != null && e.getMessage().contains("timeout expired"); // "transaction timeout expired" (JpaSystemException 의 경우 다른 cause 로도 발생할수 있음)
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
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
        log.error("[printError] stackTrace=\n{}", stackTrace);
    }


}
