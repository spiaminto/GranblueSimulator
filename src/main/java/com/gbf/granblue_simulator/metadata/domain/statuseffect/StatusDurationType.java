package com.gbf.granblue_simulator.metadata.domain.statuseffect;

public enum StatusDurationType {

    TURN, // 턴제 [턴]
    TURN_INFINITE, // 영속, 턴제
    // N 회 피격시 삭제 -> 턴제 영속으로 설정후 피격시마다 캐릭터 로직에서 레벨 낮춰 삭제

    TIME, // 시간제 [초]
    // TIME_INFINITE, // 영속, 시간제 -> 구분에 의미가 없는듯?
    ;

    public boolean isInfinite() {
        return this == TURN_INFINITE;
    }

    public boolean isTurnBased() {
        return this == TURN || this == TURN_INFINITE;
    }

    public boolean isTimeBased() {
        return this == TIME;
    }

}
