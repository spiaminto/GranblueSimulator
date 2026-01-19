package com.gbf.granblue_simulator.metadata.controller.request.character;

import lombok.Data;

@Data
public class EffectVisualInsertRequest {

    private String type;
    private Long actorVisualId;
    private String cjsName;

    private int chargeAttackStartFrame;
    private String isTargetedEnemy;

}
