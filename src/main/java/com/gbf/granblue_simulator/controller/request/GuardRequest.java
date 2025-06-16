package com.gbf.granblue_simulator.controller.request;

import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Data;

@Data
public class GuardRequest {
    private long characterId;
    private long characterOrder;
    private long memberId;
    private long roomId;
    private StatusTargetType targetType;
}
