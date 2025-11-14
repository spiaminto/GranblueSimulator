package com.gbf.granblue_simulator.controller.dto.request.battle;

import lombok.Data;

@Data
public class MoveRequest {
    private long characterId;
    private long moveId;
    private long memberId;
}
