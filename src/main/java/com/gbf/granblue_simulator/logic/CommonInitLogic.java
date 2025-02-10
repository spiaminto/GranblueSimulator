package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Transactional
public class CommonInitLogic {

    public void initBattleCharacter(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        initAtk(battleActor, statusEffects);
        initDef(battleActor, statusEffects);
        initHp(battleActor, statusEffects);

        initCriticalRate(battleActor, statusEffects);

        initDoubleAttackRate(battleActor, statusEffects);
        initTripleAttackRate(battleActor, statusEffects);

        initDeBuffResistRate(battleActor, statusEffects);
        initDeBuffSuccessRate(battleActor, statusEffects);

        initHitAccuracy(battleActor, statusEffects);
        initDodgeRate(battleActor, statusEffects);

        initChargeGaugeIncreaseRate(battleActor, statusEffects);

        initEtc(battleActor);
    }

    /**
     * 캐릭터의 공격력과 항 정보를 초기화
     * @param battleActor
     * @param statusEffects
     * @return
     */
    public Integer initAtk(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double atk = 0;
        // 공인항
        double atkUpRate = getSum(statusEffects.get(StatusEffectType.ATK_UP));
        double atkDownRate = getSum(statusEffects.get(StatusEffectType.ATK_DOWN));
        double atkRate = Math.max(atkUpRate - atkDownRate, -0.99); // 공격력 감소는 -99% 까지 적용
        // 장비항
        double weaponAtkUpRate = battleActor.getWeaponAtkUpRate();
        // 혼신항
        double strengthRate = getSum(statusEffects.get(StatusEffectType.STRENGTH));
        // 배수항
        double jammedRate = getSum(statusEffects.get(StatusEffectType.JAMMED));
        // 별항
        double uniqueRate = getSum(statusEffects.get(StatusEffectType.ATK_UP_UNIQUE));

        atk = (double) battleActor.getActor().getBaseAttackPoint() // 1000
                * (1 + weaponAtkUpRate)
                * (1 + atkRate)
                * (1 + strengthRate)
                * (1 + jammedRate)
                * (1 + uniqueRate)
        ;

        battleActor.setAtkUpRate(atkUpRate);
        battleActor.setAtkDownRate(atkDownRate);
        battleActor.setStrengthRate(strengthRate);
        battleActor.setJammedRate(jammedRate);
        battleActor.setAtkUpUniqueRate(uniqueRate);
        battleActor.setAtk((int) atk);
        return 0;
    }

    public Integer initDef(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double def = 0;
        // 방어인항
        double defUpRate = getSum(statusEffects.get(StatusEffectType.DEF_UP));
        double defDownRate = getSum(statusEffects.get(StatusEffectType.DEF_DOWN));
        double defRate = Math.max(defUpRate - defDownRate, 0);
        // 데미지컷
        double takenDamageCut = getSum(statusEffects.get(StatusEffectType.TAKEN_DAMAGE_CUT));
        // 피격 데미지 감소
        int takenDamageFixedDown = (int) getSum(statusEffects.get(StatusEffectType.TAKEN_DAMAGE_FIXED_DOWN));
        // 베리어
        int barrier = (int) getSum(statusEffects.get(StatusEffectType.BARRIER));

        def = (double) battleActor.getActor().getBaseDefencePoint() // 10
                * (1 + defRate)
        ;

        battleActor.setDefUpRate(defUpRate);
        battleActor.setDefDownRate(defDownRate);
        battleActor.setTakenDamageCut(takenDamageCut);
        battleActor.setTakenDamageFixedDown(takenDamageFixedDown);
        battleActor.setBarrier(barrier);
        battleActor.setDef((int)def);
        return 0;
    }

    public Integer initHp(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double hp = 0;
        // 수호항 (고정값)
        double hpAmplifier = battleActor.getHpUpRate();
        // 최대 체력 감소
        double maxHpDownRate = getSum(statusEffects.get(StatusEffectType.MAX_HP_DOWN));

        hp = (double) battleActor.getActor().getBaseHitPoint() // 1000
                * (1 + hpAmplifier)
                * (1 + maxHpDownRate)
        ;

        battleActor.setMaxHpDownRate(maxHpDownRate);
        battleActor.setHp((int) hp);
        return 0;
    }

    public Integer initDoubleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double doubleAttackUpRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_UP));
        double doubleAttackDownRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_DOWN));

        battleActor.setDoubleAttackUpRate(doubleAttackUpRate);
        battleActor.setDoubleAttackDownRate(doubleAttackDownRate);
        return 0;
    }

    public Integer initTripleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double tripleAttackUpRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_UP));
        double tripleAttackDownRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_DOWN));

        battleActor.setTripleAttackUpRate(tripleAttackUpRate);
        battleActor.setTripleAttackDownRate(tripleAttackDownRate);
        return 0;
    }

    public Integer initDeBuffResistRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double deBuffResistUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_UP));
        double deBuffResistDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_DOWN));

        battleActor.setDeBuffResistUpRate(deBuffResistUpRate);
        battleActor.setDeBuffResistDownRate(deBuffResistDownRate);
        return 0;
    }

    public Integer initDeBuffSuccessRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double deBuffSuccessUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_UP));
        double deBuffSuccessDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_DOWN));

        battleActor.setDeBuffSuccessUpRate(deBuffSuccessUpRate);
        battleActor.setDeBuffSuccessDownRate(deBuffSuccessDownRate);
        return 0;
    }

    public Integer initCriticalRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double criticalUpRate = getSum(statusEffects.get(StatusEffectType.CRITICAL_RATE_UP));
        // 크리티컬은 down 없음
        battleActor.setCriticalRate(criticalUpRate);
        return 0;
    }
    // 크리티컬 데미지증가율은 고정값 (50%)

    // 오의게이지는 일단 초기화 없이. 초기화값은 0
    public Integer initChargeGaugeIncreaseRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double chargeGaugeIncreaseUpRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_UP));
        double chargeGaugeIncreaseDownRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_DOWN));

        battleActor.setChargeGaugeIncreaseUpRate(chargeGaugeIncreaseUpRate);
        battleActor.setChargeGaugeIncreaseDownRate(chargeGaugeIncreaseDownRate);
        return 0;
    }

    public Integer initHitAccuracy (BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double hitAccuracyUpRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_UP));
        double hitAccuracyDownRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_DOWN));

        battleActor.setAccuracyUpRate(hitAccuracyUpRate);
        battleActor.setAccuracyDownRate(hitAccuracyDownRate);
        return 0;
    }

    public Integer initDodgeRate (BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double dodgeUpRate = getSum(statusEffects.get(StatusEffectType.DODGE_RATE_UP));
        // 회피는 down 없음
        battleActor.setDodgeUpRate(dodgeUpRate);
        return 0;
    }

    public Integer initEtc(BattleActor battleActor) {
        battleActor.setMaxChargeGauge(battleActor.getActor().getMaxChargeGauge());

        battleActor.setFirstAbilityCoolDown(0);
        battleActor.setSecondAbilityCoolDown(0);
        battleActor.setThirdAbilityCoolDown(0);
        battleActor.setFirstAbilityUseCount(0);
        battleActor.setSecondAbilityUseCount(0);
        battleActor.setThirdAbilityUseCount(0);
        return 1;
    }


    /**
     * 주어진 항의 버프수치 합산을 구함
     *
     * @param statusEffects 항 리스트
     * @return 합산수치, 없으면 0
     */
    private double getSum(List<StatusEffect> statusEffects) {
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.stream()
                        .map(StatusEffect::getValue)
                        .mapToDouble(Double::doubleValue)
                        .sum();
    }

}
