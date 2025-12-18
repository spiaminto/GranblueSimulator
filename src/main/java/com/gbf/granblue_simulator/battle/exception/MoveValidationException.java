package com.gbf.granblue_simulator.battle.exception;

/**
 * 행동을 위한 검증에 실패한 경우 발생
 */
public class MoveValidationException extends RuntimeException {

    private final boolean isConditionFailed;

    public MoveValidationException(String message) {
        super(message);
        this.isConditionFailed = false;
    }

    /**
     * 행동시 필요조건 달성에 실패했을경우 사용 <br>
     * 프론트에서 확인 후 어빌리티 레일 상태를 유지하기 위해 사용중
     *
     * @param message 프론트에 전달할 메시지 직접작성
     * @param isConditionFailed : 조건 사용 실패여부, 일반적으로 true
     */
    public MoveValidationException(String message, boolean isConditionFailed) {
        super(message);
        this.isConditionFailed = isConditionFailed;
    }

    public boolean isConditionFailed() {
        return isConditionFailed;
    }
}
