package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Data
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

    private double atkUpRate;
    private double atkDownRate;
    private double strengthRate;
    private double jammedRate;
    private double uniqueUpRate;

    private double defUpRate;
    private double defDownRate;

    private double maxHpDownRate;

    private double doubleAttackUpRate;
    private double doubleAttackDownRate;

    private double tripleAttackUpRate;
    private double tripleAttackDownRate;

    private double deBuffResistUpRate;
    private double deBuffResistDownRate;

    private double deBuffSuccessUpRate;
    private double deBuffSuccessDownRate;

    private double accuracyUpRate;
    private double accuracyDownRate;

    private double dodgeUpRate;

    private double criticalUpRate;
    private double criticalDamageUpRate;

    private double chargeGaugeIncreaseUpRate;
    private double chargeGaugeIncreaseDownRate;

}
