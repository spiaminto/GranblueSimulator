package com.gbf.granblue_simulator.metadata.controller.character;

import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisualType;
import lombok.Data;

@Data
public class CharacterAssetInsertRequest {

    private Long characterId;
    private String rootCjsName;
    private String assetName;
    private EffectVisualType effectVisualType; // ACTOR, SPECIAL, FIRST_ABILITY, ...
    private String cjsName;
    private int chargeAttackStartFrame;

}
