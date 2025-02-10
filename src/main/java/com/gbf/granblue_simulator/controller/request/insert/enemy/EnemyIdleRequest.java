package com.gbf.granblue_simulator.controller.request.insert.enemy;

import lombok.Data;

@Data
public class EnemyIdleRequest {

    private Long enemyId;
    private String type;
    private String idleEffectVideoSrc;

}
