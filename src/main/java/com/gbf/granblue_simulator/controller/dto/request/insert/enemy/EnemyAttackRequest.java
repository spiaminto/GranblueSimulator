package com.gbf.granblue_simulator.controller.dto.request.insert.enemy;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import lombok.Data;

@Data
public class EnemyAttackRequest {

    private Long enemyId;
    private ElementType elementType;
    private boolean isAllTarget;

    // move - attack
    private String singleAttackSeAudioSrc;
    private String singleAttackEffectVideoSrc;

    private String doubleAttackSeAudioSrc;
    private String doubleAttackEffectVideoSrc;

    private String tripleAttackSeAudioSrc;
    private String tripleAttackEffectVideoSrc;
}
