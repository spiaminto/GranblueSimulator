package com.gbf.granblue_simulator.metadata.controller.enemy;

import lombok.Data;

@Data
public class EnemyBreakRequest {

    private Long enemyId;
    private String type;
    private String breakEffectVideoSrc;
    private String breakSeAudioSrc;

}
