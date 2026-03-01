package com.gbf.granblue_simulator.metadata.domain.statuseffect;

public enum StatusDurationType {

    TURN, // 턴제 [턴]
    TURN_INFINITE, // 영속, 턴제
    
    LEVEL_INFINITE, // 레벨이 0이 되면 소거, 기본 영속

    TIME, // 시간제 [초]
    // TIME_INFINITE, // 영속, 시간제 -> 구분에 의미가 없는듯?
    ;

    public boolean isInfinite() {
        return this == TURN_INFINITE || this == LEVEL_INFINITE;
    }

    public boolean isTurnBased() {
        return this == TURN || this == TURN_INFINITE;
    }

    public boolean isTimeBased() {
        return this == TIME;
    }

}
