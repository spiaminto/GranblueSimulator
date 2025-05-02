package com.gbf.granblue_simulator.domain.move.prop.omen;

import com.fasterxml.jackson.databind.KeyDeserializer;
import lombok.Getter;

@Getter
public enum OmenType {
    CHARGE_ATTACK("charge-attack", "CT기"), // CT기
    INCANT_ATTACK("incant-attack", "영창기"), // 영창기
    HP_TRIGGER("hp-trigger", "HP트리거"), // HP트리거
    NONE("none", "none"),

    ;

    private final String className;
    private final String info;

    OmenType(String className, String info) {
        this.className = className;
        this.info = info;
    }
}
