package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class BattleResultMemberInfo {
    private String username;
    private int totalTurns;
    private String totalTime;
    private String enemyHp;
    private String totalDamage;
    private double totalDamageRate;
    private String totalHonor;
}
