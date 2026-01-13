package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class MemberInfo {

    private Long id;
    private Boolean isChargeAttackOn;
    private Integer currentTurn;

}
