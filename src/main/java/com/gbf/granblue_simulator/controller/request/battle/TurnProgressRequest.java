package com.gbf.granblue_simulator.controller.request.battle;

import lombok.Data;

@Data
public class TurnProgressRequest {

    private Long memberId;
    private Long roomId;

}
