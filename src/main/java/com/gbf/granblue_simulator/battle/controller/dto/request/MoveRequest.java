package com.gbf.granblue_simulator.battle.controller.dto.request;

import lombok.Data;

@Data
public class MoveRequest {
    private long characterId;
    private long moveId;
    private long memberId;
    private boolean doUnionSummon;
}
