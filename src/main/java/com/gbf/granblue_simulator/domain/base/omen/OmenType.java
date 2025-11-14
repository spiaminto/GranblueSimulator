package com.gbf.granblue_simulator.domain.base.omen;

import com.fasterxml.jackson.databind.KeyDeserializer;
import lombok.Getter;

@Getter
public enum OmenType {
    INCANT_ATTACK("incant-attack", "영창기"), // 영창기
    HP_TRIGGER("hp-trigger", "HP트리거"), // HP트리거
    CHARGE_ATTACK("charge-attack", "CT기"), // CT기
    NONE("none", "none"),
    // CHECK ordinal 정렬 사용중
    ;

    private final String className;
    private final String info;

    OmenType(String className, String info) {
        this.className = className;
        this.info = info;
    }
}
