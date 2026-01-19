package com.gbf.granblue_simulator.metadata.controller.request.enemy;
import lombok.Data;

@Data
public class EnemyIdleRequest {

    private Long enemyId;
    private String type;
    private String idleEffectVideoSrc;

}
