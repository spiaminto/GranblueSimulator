package com.gbf.granblue_simulator.battle.controller.dto.request;

import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.Data;

@Data
public class GuardRequest {
    private long characterId;
    private long characterOrder;
    private long memberId;
    private long roomId;
    private StatusEffectTargetType targetType;
}
