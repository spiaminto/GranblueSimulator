package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SyncStatusDto {
    private Integer atk;
    private Integer def;
    private Double doubleAttackRate;
    private Double tripleAttackRate;
    private Double deBuffResistRate;
    private Double deBuffSuccessRate;
    private Double accuracyRate;
    private Double dodgeRate;
    private Double criticalRate;
    private Double criticalDamageRate;
    private Double chargeGaugeIncreaseRate;
}
