package com.gbf.granblue_simulator.battle.domain;

import lombok.Getter;

@Getter
public enum RoomStatus {
    ACTIVE("전투중"),
    TUTORIAL("튜토리얼"),

    CLEARED("퀘스트 클리어"),

    FAILED_TIMEOUT("퀘스트 실패 (시간 초과)"),
    FAILED_EMPTY("퀘스트 실패 (전원 퇴장)"),
    ;
    
    private final String displayName;


    RoomStatus(String displayName) {
        this.displayName = displayName;
    }
}
