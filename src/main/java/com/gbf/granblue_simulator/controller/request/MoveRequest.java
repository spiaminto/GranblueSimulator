package com.gbf.granblue_simulator.controller.request;

import lombok.Data;

@Data
public class MoveRequest {
    private long characterId;
    private long moveId;
    private long memberId;
}
