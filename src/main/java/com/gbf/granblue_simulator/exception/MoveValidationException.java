package com.gbf.granblue_simulator.exception;

/**
 * 행동을 위한 검증에 실패한 경우 발생
 */
public class MoveValidationException extends RuntimeException{
    public MoveValidationException(String message) {
        super(message);
    }
}
