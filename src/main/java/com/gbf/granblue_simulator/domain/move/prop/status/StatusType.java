package com.gbf.granblue_simulator.domain.move.prop.status;

public enum StatusType {
    BUFF,
    BUFF_FOR_ALL, // 참전자 전체 버프
    DEBUFF,
    DEBUFF_FOR_ALL, // 참전자 전체 디버프
    
    HEAL, // 힐,

    DISPEL, // 디스펠
    DISPEL_GUARD, // 디스펠 가드

    CLEAR, // 클리어
    CLEAR_FOR_ALL, // 참전자 클리어

    ETC,
    UNIQUE // 고유버프, name 으로 필드 구분
    ;

    public boolean isDebuff() {
        return this == DEBUFF || this == DEBUFF_FOR_ALL;
    }
}
