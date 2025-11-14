package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.battle.actor.prop.Status;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatusDto {

    private Long actorId;
    private String actorName;

    private int atk;
    private double def; // .1f
    private int maxHp;
    private int hp;

    private double doubleAttackRate;
    private double tripleAttackRate;

    private double debuffSuccessRate;
    private double debuffResistRate;

    private double accuracyRate;
    private double dodgeRate;

    private double criticalRate;
    private double criticalDamageRate;

    private double chargeGaugeIncreaseRate;

    private int chargeGauge;
    private int maxChargeGauge;
    private int fatalChainGauge;

    public static StatusDto of(Status status) {
        return StatusDto.builder()
                .actorId(status.getActor().getId())
                .actorName(status.getActor().getName())
                .atk(status.getAtk())
                .def(status.getDef())
                .maxHp(status.getMaxHp())
                .hp(status.getHp())
                .doubleAttackRate(status.getDoubleAttackRate())
                .tripleAttackRate(status.getTripleAttackRate())
                .debuffSuccessRate(status.getDebuffSuccessRate())
                .debuffResistRate(status.getDebuffResistRate())
                .accuracyRate(status.getAccuracyRate())
                .dodgeRate(status.getDodgeRate())
                .criticalRate(status.getCriticalRate())
                .criticalDamageRate(status.getCriticalDamageRate())
                .chargeGaugeIncreaseRate(status.getChargeGaugeIncreaseRate())
                .chargeGauge(status.getChargeGauge())
                .maxChargeGauge(status.getMaxChargeGauge())
                .fatalChainGauge(status.getFatalChainGauge())
                .build();
    }
}
