package com.gbf.granblue_simulator.battle.exception;

import lombok.Getter;

/**
 * Move 처리중 실패시 반환 (기존의 IllegalArgumentException 을 대체할 예정)
 */
public class MoveProcessingException extends RuntimeException {

    @Getter
    private final String code;

    public MoveProcessingException(String message) {
        super(message);
        this.code = "MOVE_PROCESSING_ERROR";
    }

    public MoveProcessingException(String message, String code) {
        super(message);
        this.code = code;
    }

}
