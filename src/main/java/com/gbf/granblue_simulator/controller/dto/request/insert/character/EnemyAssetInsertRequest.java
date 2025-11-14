package com.gbf.granblue_simulator.controller.dto.request.insert.character;

import com.gbf.granblue_simulator.domain.base.asset.AssetType;
import lombok.Data;

@Data
public class EnemyAssetInsertRequest {

    private Long actorId;
    private String rootCjsName;
    private String assetName;
    private AssetType assetType; // ACTOR, SPECIAL, FIRST_ABILITY, ...
    private String cjsName;

}
