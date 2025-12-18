package com.gbf.granblue_simulator.battle.logic.actor.dto;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultStatusDto {

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

    public static ResultStatusDto of(Status status) {
        return ResultStatusDto.builder()
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
