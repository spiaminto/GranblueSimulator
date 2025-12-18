package com.gbf.granblue_simulator.metadata.controller.enemy;

import lombok.Data;

@Data
public class EnemyDamagedRequest {

    private Long enemyId;
    private String type;
    private String damagedEffectVideoSrc;
}
