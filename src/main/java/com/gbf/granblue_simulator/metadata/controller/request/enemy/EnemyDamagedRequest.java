package com.gbf.granblue_simulator.metadata.controller.request.enemy;
import lombok.Data;

@Data
public class EnemyDamagedRequest {

    private Long enemyId;
    private String type;
    private String damagedEffectVideoSrc;
}
