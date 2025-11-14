package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyncDamageStatusDto {

    /* 데미지 상한 상승 */
    private double damageCapUpRate;
    private double attackDamageCapUpRate;
    private double abilityDamageCapUpRate;
    private double chargeAttackDamageCapUpRate;

    /* 데미지 증가 */
    private int supplementalDamage;
    private int supplementalAttackDamage;
    private int supplementalAbilityDamage;
    private int supplementalChargeAttackDamage;

    /* 데미지 상승 */
    private double amplifyDamageRate;
    private double amplifyAttackDamageRate;
    private double amplifyAbilityDamageRate;
    private double amplifyChargeAttackDamageRate;

    /* 받는 데미지 증가, 감소 */
    private int takenSupplementalDamageUpPoint;
    private int takenSupplementalDamageDownPoint;

    /* 받는 데미지 상승, 경감 */
    private double takenAmplifyDamageUpRate;
    private double takenAmplifyDamageDownRate;

    /* 받는 데미지 행동별 상승, 경감 */
    private double takenAttackDamageUpRate;
    private double takenAttackDamageDownRate;
    private double takenAbilityDamageUpRate;
    private double takenAbilityDamageDownRate;
    private double takenChargeDamageUpRate;
    private double takenChargeDamageDownRate;

    /* 데미지 컷, 고정 */
    private double takenDamageCut;
    private int takenDamageFixPoint;
    

}
