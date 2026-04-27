package com.gbf.granblue_simulator.battle.domain;

import lombok.Getter;

@Getter
public enum ChatStamp {

    OK("ok"),
    THANKS("thanks"),
    ;

    private final String filename;

    ChatStamp(String filename) {
        this.filename = filename;
    }
}
