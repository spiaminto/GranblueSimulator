package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class BattleResultInfo {
    private String enemyName;
    private String enemyPortraitSrc;
    private String endedAt;
    private int enterUserCount;
}
