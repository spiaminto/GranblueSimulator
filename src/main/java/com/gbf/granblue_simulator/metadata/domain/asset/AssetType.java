package com.gbf.granblue_simulator.metadata.domain.asset;

import lombok.Getter;

@Getter
public enum AssetType {
    ACTOR,  // main cjs
    ATTACK, // phit, ehit

    SPECIAL,    // sp, nsp, summons
    ADDITIONAL_SPECIAL,

    FATAL_CHAIN_DEFAULT,

    FIRST_ABILITY,
    SECOND_ABILITY,
    THIRD_ABILITY,
    FOURTH_ABILITY,

    FIRST_SUPPORT_ABILITY,
    SECOND_SUPPORT_ABILITY,
    THIRD_SUPPORT_ABILITY,
    FOURTH_SUPPORT_ABILITY,
    FIFTH_SUPPORT_ABILITY,
    SIXTH_SUPPORT_ABILITY,
    SEVENTH_SUPPORT_ABILITY,
    EIGHTH_SUPPORT_ABILITY,
    NINTH_SUPPORT_ABILITY,
    TENTH_SUPPORT_ABILITY,
    ;

    public boolean isAbility() {
        return this == FIRST_ABILITY || this == SECOND_ABILITY || this == THIRD_ABILITY || this == FOURTH_ABILITY
                || this == FIRST_SUPPORT_ABILITY || this == SECOND_SUPPORT_ABILITY || this == THIRD_SUPPORT_ABILITY
                || this == FOURTH_SUPPORT_ABILITY || this == FIFTH_SUPPORT_ABILITY || this == SIXTH_SUPPORT_ABILITY
                || this == SEVENTH_SUPPORT_ABILITY || this == EIGHTH_SUPPORT_ABILITY || this == NINTH_SUPPORT_ABILITY
                || this == TENTH_SUPPORT_ABILITY;
    }
}
