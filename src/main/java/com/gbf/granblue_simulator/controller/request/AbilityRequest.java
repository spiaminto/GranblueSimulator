package com.gbf.granblue_simulator.controller.request;

import lombok.Data;

@Data
public class AbilityRequest {
    private long characterId;
    private long characterOrder;
    private long abilityId;
    private String moveType;
    private long abilityOrder;
    private long memberId;
    private long roomId;
}
