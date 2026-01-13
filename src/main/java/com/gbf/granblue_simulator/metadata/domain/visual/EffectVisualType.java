package com.gbf.granblue_simulator.metadata.domain.visual;

import lombok.Getter;

@Getter
public enum EffectVisualType {
    ACTOR,  // main cjs

    ATTACK, // phit, ehit

    SPECIAL,    // sp, nsp, summons
    ADDITIONAL_SPECIAL,

    CHARGE_ATTACK, // nsp, esp
    ADDITIONAL_CHARGE_ATTACK, // esp

    FATAL_CHAIN_DEFAULT, // burst_

    ABILITY, // ab_, ef_,

    ;
}
