package com.gbf.granblue_simulator.domain.asset;

import lombok.Getter;

@Getter
public enum AssetType {
    ACTOR,  // main cjs
    ATTACK, // phit, ehit
    ABILITY, // ability
    SPECIAL,    // sp, nsp, summons
}
