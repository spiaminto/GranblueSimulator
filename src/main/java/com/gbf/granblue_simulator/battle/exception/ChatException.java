package com.gbf.granblue_simulator.battle.exception;

import lombok.Getter;

public class ChatException extends RuntimeException {
    @Getter
    private final String code;

    public ChatException(String message) {
        super(message);
        this.code = "CHAT_FAILED";
    }
    public ChatException(String message, String code) {
        super(message);
        this.code = code;
    }
}
