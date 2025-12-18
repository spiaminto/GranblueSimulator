package com.gbf.granblue_simulator.metadata.domain.statuseffect;

public enum StatusDurationType {

    TURN, // 턴제 [턴]
    TURN_INFINITE, // 영속, 턴제

    LEVEL_INFINITE, // 영속, 레벨 0 시 해제

    TIME, // 시간제 [초]
    TIME_INFINITE, // 영속, 시간제
    ;

    public boolean isInfinite() {
        return this == TURN_INFINITE || this == LEVEL_INFINITE || this == TIME_INFINITE;
    }

    public boolean isTurnBased() {
        return this == TURN || this == TURN_INFINITE;
    }

    public boolean isTimeBased() {
        return this == TIME || this == TIME_INFINITE;
    }

    public boolean isLevelBased() {
        return this == LEVEL_INFINITE;
    }
}
