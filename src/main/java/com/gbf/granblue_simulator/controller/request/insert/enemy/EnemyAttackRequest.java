package com.gbf.granblue_simulator.controller.request.insert.enemy;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Data;

@Data
public class EnemyAttackRequest {

    private Long enemyId;
    private ElementType elementType;

    // move - attack
    private String singleAttackSeAudioSrc;
    private String singleAttackEffectVideoSrc;

    private String doubleAttackSeAudioSrc;
    private String doubleAttackEffectVideoSrc;

    private String tripleAttackSeAudioSrc;
    private String tripleAttackEffectVideoSrc;
}
