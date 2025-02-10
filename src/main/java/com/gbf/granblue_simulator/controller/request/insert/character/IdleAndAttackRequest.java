package com.gbf.granblue_simulator.controller.request.insert.character;

import lombok.Data;

@Data
public class IdleAndAttackRequest {

    private Long characterId;

    // move - idle
    private String idleEffectVideoSrc;

    // move - attack
    private String singleAttackSeAudioSrc;
    private String singleAttackEffectVideoSrc;
    private String doubleAttackSeAudioSrc;
    private String doubleAttackEffectVideoSrc;
    private String tripleAttackSeAudioSrc;
    private String tripleAttackEffectVideoSrc;
}
