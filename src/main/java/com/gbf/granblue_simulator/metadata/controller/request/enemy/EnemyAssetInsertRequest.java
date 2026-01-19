package com.gbf.granblue_simulator.metadata.controller.request.enemy;

import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisualType;
import lombok.Data;

@Data
public class EnemyAssetInsertRequest {

    private Long actorId;
    private String rootCjsName;
    private String assetName;
    private EffectVisualType effectVisualType; // ACTOR, SPECIAL, FIRST_ABILITY, ...
    private String cjsName;

}
