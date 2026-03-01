package com.gbf.granblue_simulator.battle.logic.move.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForMemberAbilityInfo {

    private String sourceUsername;
    private String moveName;

    private String cjsName;
    private Boolean isTargetedEnemy;

}
