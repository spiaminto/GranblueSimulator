package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class BattleResultMemberInfo {
    private String username;
    private int totalTurns;
    private String totalTime;
    private String enemyHp;
    private int totalDamage;
    private String formattedTotalDamage;
    private double totalDamageRate;
    private String totalHonor;
}
