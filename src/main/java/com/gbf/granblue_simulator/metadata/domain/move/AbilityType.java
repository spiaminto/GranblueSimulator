package com.gbf.granblue_simulator.metadata.domain.move;

import lombok.Getter;

public enum AbilityType {

    ATTACK("공격"),
    BUFF("강화"),
    DEBUFF("약화"),
    HEAL("회복"),

    // FIELD, [구현 예정 없음]

    ;

    @Getter
    private final String displayName;

    AbilityType(String displayName) {
        this.displayName = displayName;
    }

}
