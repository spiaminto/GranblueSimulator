package com.gbf.granblue_simulator.controller.request.insert.enemy;

import lombok.Data;

@Data
public class EnemyAttackRequest {

    private Long enemyId;

    // move - attack
    private String attackSeAudioSrc;
    private String attackEffectVideoSrc;
}
