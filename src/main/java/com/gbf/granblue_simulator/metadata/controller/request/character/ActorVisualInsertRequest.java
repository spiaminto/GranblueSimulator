package com.gbf.granblue_simulator.metadata.controller.request.character;

import lombok.Data;

@Data
public class ActorVisualInsertRequest {

    private String name;
    private String cjsName;
    private String gid;

    // nullable
    private String additionalCjsName;
    private String weaponId;

}
