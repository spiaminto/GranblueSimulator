package com.gbf.granblue_simulator.metadata.domain.move;

public enum AbilityType {

    ATTACK("공격 어빌리티"),
    BUFF("강화 어빌리티"),
    DEBUFF("약화 어빌리티"),
    HEAL("회복 어빌리티"),

    // FIELD, [구현 예정 없음]

    ;

    private final String info;
    AbilityType(String info) {
        this.info = info;
    }

}
