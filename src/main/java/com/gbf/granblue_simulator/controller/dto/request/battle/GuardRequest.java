package com.gbf.granblue_simulator.controller.dto.request.battle;

import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
import lombok.Data;

@Data
public class GuardRequest {
    private long characterId;
    private long characterOrder;
    private long memberId;
    private long roomId;
    private StatusEffectTargetType targetType;
}
