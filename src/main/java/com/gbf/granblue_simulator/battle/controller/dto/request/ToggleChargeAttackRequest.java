package com.gbf.granblue_simulator.battle.controller.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToggleChargeAttackRequest {

    private Long roomId;
    private boolean chargeAttackOn;

}
