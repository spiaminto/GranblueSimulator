package com.gbf.granblue_simulator.metadata.domain.omen;

import com.fasterxml.jackson.databind.KeyDeserializer;
import lombok.Getter;

@Getter
public enum OmenType {
    INCANT_ATTACK("incant-attack", "영창기", 3), // 영창기
    HP_TRIGGER("hp-trigger", "HP트리거", 2), // HP트리거
    CHARGE_ATTACK("charge-attack", "CT기", 1), // CT기
    NONE("none", "none", 999),
    ;

    private final String className;
    private final String info;
    private final int displayOrder;

    OmenType(String className, String info, int displayOrder) {
        this.className = className;
        this.info = info;
        this.displayOrder = displayOrder;
    }
}
