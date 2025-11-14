package com.gbf.granblue_simulator.controller.dto.request.insert.enemy;

import lombok.Data;

@Data
public class EnemyDamagedRequest {

    private Long enemyId;
    private String type;
    private String damagedEffectVideoSrc;
}
