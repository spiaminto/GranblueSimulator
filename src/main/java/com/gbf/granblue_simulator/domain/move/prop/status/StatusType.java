package com.gbf.granblue_simulator.domain.move.prop.status;

import lombok.Getter;

@Getter
public enum StatusType {
    BUFF(5),
    BUFF_FOR_ALL(5), // 참전자 전체 버프
    DEBUFF(15),
    DEBUFF_FOR_ALL(15), // 참전자 전체 디버프
    
    HEAL(35), // 힐,
    HEAL_FOR_ALL(35),// 참전자 힐

    DISPEL(25), // 디스펠
    DISPEL_GUARD(25), // 디스펠 가드

    CLEAR(-5), // 클리어
    CLEAR_FOR_ALL(-5), // 참전자 클리어
    
    PASSIVE(0), // 서포트 어빌리티를 통한 패시브


    ETC(999),
    ;
    
    final int processOrder; // 처리 우선순위

    StatusType(int processOrder) {
        this.processOrder = processOrder;
    }

    public boolean isDebuff() {
        return this == DEBUFF || this == DEBUFF_FOR_ALL;
    }
    public boolean isBuff() {
        return this == BUFF || this == BUFF_FOR_ALL;
    }

    public boolean isPresentable() {return this != PASSIVE ;} // 외부에 보여줄 효과
    public boolean isForAllStatus() {return this == BUFF_FOR_ALL || this == DEBUFF_FOR_ALL || this == CLEAR_FOR_ALL;}
}
