package com.gbf.granblue_simulator.controller.response.battle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToggleChargeAttackResponse {

    private boolean chargeAttackOn;

}
