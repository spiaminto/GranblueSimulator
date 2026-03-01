package com.gbf.granblue_simulator.battle.controller.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ToggleChargeAttackResponse {

    private boolean chargeAttackOn;
    private List<Boolean> canChargeAttacks;

}
