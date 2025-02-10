package com.gbf.granblue_simulator.domain.move.prop.status;

public enum StatusType {
    BUFF,
    BUFF_FOR_ALL, // 참전자 전체 버프
    DEBUFF,
    DEBUFF_FOR_ALL, // 참전자 전체 디버프
    ETC,
    HEAL, // 힐
    
    UNIQUE // 고유버프, name 으로 필드 구분
}
