package com.gbf.granblue_simulator.domain.move.prop.omen;

import com.fasterxml.jackson.databind.KeyDeserializer;
import lombok.Getter;

@Getter
public enum OmenType {
    CHARGE_ATTACK("charge-attack"), // CT기
    INCANT_ATTACK("incant-attack"), // 영창기
    HP_TRIGGER("hp-trigger"), // HP트리거
    NONE("none"),

    ;

    private final String className;

    OmenType(String className) {
        this.className = className;
    }
}
