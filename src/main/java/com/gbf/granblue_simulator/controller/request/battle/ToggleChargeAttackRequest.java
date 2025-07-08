package com.gbf.granblue_simulator.controller.request.battle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToggleChargeAttackRequest {

    private Long roomId;
    private boolean chargeAttackOn;

}
