package com.gbf.granblue_simulator.logic;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CharacterLogicResult {

    private AfterProcessType afterProcessType;
    private AfterProcessTarget afterProcessTarget;


    public enum AfterProcessType {
        NONE,
        ATTACK,
        FIRST_ABILITY,
        SECOND_ABILITY,
        THIRD_ABILITY,
        CHARGE_ATTACK
    }

    public enum AfterProcessTarget {
        SELF,
        PARTY_MEMBER,
    }

}
