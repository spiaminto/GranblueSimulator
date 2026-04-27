package com.gbf.granblue_simulator.battle.domain;

import lombok.Getter;

@Getter
public enum RoomStatus {
    ACTIVE("전투중"),
    TUTORIAL("튜토리얼"),

    CLEARED("클리어"),

    TEST("테스트"),

    FAILED_TIMEOUT("실패 (시간 초과)"),
    FAILED_EMPTY("실패 (전원 퇴장)"),
    ;
    
    private final String displayName;


    RoomStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isFinished() {
        return this == CLEARED || this == FAILED_TIMEOUT || this == FAILED_EMPTY;
    }

    public boolean isHidden() {return this == TUTORIAL || this == TEST;}
}
