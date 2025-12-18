package com.gbf.granblue_simulator.battle.controller.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToggleChargeAttackResponse {

    private boolean chargeAttackOn;

}
