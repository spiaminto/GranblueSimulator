package com.gbf.granblue_simulator.domain.base.statuseffect;

import lombok.Getter;

@Getter
public enum StatusEffectType {
    BUFF(5),
    DEBUFF(15),
    
    PASSIVE(0), // 서포트 어빌리티를 통한 패시브 (표시하지 않음)

    ETC(999),
    ;
    
    final int processOrder; // 처리 우선순위

    StatusEffectType(int processOrder) {
        this.processOrder = processOrder;
    }

    public boolean isDebuff() {
        return this == DEBUFF;
    }
    public boolean isBuff() {
        return this == BUFF;
    }

    public boolean isPresentable() {return this != PASSIVE ;} // 외부에 보여줄 효과
}
