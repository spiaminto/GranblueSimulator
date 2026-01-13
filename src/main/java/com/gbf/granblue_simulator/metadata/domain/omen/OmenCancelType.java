package com.gbf.granblue_simulator.metadata.domain.omen;

import lombok.Getter;

@Getter
public enum OmenCancelType {

    DAMAGE("damage"), // 데미지 량
    HIT_COUNT("damage"), // 히트수

    DEBUFF_COUNT("statusEffect"), // 디버프카운트


    // 나중에 아마 쓸것
    DISPEL_COUNT("statusEffect"),

    ABILITY_USE_COUNT("default"),

    // 해제불가
    IMPOSSIBLE("damage"),
    ;

    private final String updateTiming;  // 'damage', 'statusEffect', 'default

    OmenCancelType(String updateTiming) {
        this.updateTiming = updateTiming;
    }

}
