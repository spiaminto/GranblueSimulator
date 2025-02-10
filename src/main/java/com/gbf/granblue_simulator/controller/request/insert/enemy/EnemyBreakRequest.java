package com.gbf.granblue_simulator.controller.request.insert.enemy;

import lombok.Data;

@Data
public class EnemyBreakRequest {

    private Long enemyId;
    private String type;
    private String breakEffectVideoSrc;
    private String breakSeAudioSrc;

}
