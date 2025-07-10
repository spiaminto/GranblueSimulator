package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.logic.common.dto.SyncStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getStatusEffectMap;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CalcStatusLogic {

    /**
     * 전투 시작시 스탯 갱신
     *
     * @param battleActor
     */
    public void initStatus(BattleActor battleActor) {
        battleActor.initStatus();
        if (battleActor.isEnemy()) {
            BattleEnemy enemy = (BattleEnemy) battleActor;
            enemy.setLatestTriggeredHp(100);
            enemy.setCurrentForm(1);
        }

        syncStatus(battleActor);
    }

    /**
     * 스테이터스에 따른 BattleActor 의 스텟을 다시 계산 후 설정
     *
     * @param battleActor 대상
     */
    public void syncStatus(BattleActor battleActor) {
        Map<StatusEffectType, List<StatusEffect>> statusEffects = getStatusEffectMap(battleActor);

        // hp, maxHp 는 미리 set
        int maxHp = getMaxHp(battleActor, statusEffects);
        battleActor.updateMaxHp(maxHp);
        int hp = getHp(battleActor, statusEffects);
        battleActor.updateHp(hp); // 특히 hp 는 혼신 / 배수 등에서 쓰이므로 먼저 set 해야함

        int atk = getAtk(battleActor, statusEffects);
        int def = getDef(battleActor, statusEffects);
        double criticalRate = getCriticalRate(battleActor, statusEffects);
        double criticalDamageRate = getCriticalDamageRate(battleActor, statusEffects);
        double doubleAttackRate = getDoubleAttackRate(battleActor, statusEffects);
        double tripleAttackRate = getTripleAttackRate(battleActor, statusEffects);
        double deBuffResistRate = getDeBuffResistRate(battleActor, statusEffects);
        double deBuffSuccessRate = getDeBuffSuccessRate(battleActor, statusEffects);
        double accuracy = getAccuracy(battleActor, statusEffects);
        double dodgeRate = getDodgeRate(battleActor, statusEffects);
        double chargeGaugeIncreaseRate = getChargeGaugeIncreaseRate(battleActor, statusEffects);

        battleActor.syncStatus(SyncStatusDto.builder()
                .atk(atk)
                .def(def)
                .criticalRate(criticalRate)
                .criticalDamageRate(criticalDamageRate)
                .doubleAttackRate(doubleAttackRate)
                .tripleAttackRate(tripleAttackRate)
                .deBuffResistRate(deBuffResistRate)
                .deBuffSuccessRate(deBuffSuccessRate)
                .accuracyRate(accuracy)
                .dodgeRate(dodgeRate)
                .chargeGaugeIncreaseRate(chargeGaugeIncreaseRate)
                .build());
    }

    /**
     * 공인, 장비, 혼신, 배수, 별항
     * 공격 데미지 계열은 데미지 로직에서 연산
     *
     * @param battleActor
     * @param statusEffects
     * @return
     */
    protected int getAtk(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double atk = 0;
        double currentHpRate = (double) battleActor.getHp() / battleActor.getMaxHp(); // 혼신, 배수용 현재 체력 비율
        // 공인항
        double atkUpRate = getSum(statusEffects.get(StatusEffectType.ATK_UP));
        double atkDownRate = getSum(statusEffects.get(StatusEffectType.ATK_DOWN));
        // 장비항
        double weaponAtkUpRate = battleActor.getAtkWeaponRate();
        // 혼신항
        double maxStrengthRate = getSum(statusEffects.get(StatusEffectType.STRENGTH));
        double strengthRate = currentHpRate > 0.5 ? maxStrengthRate * currentHpRate : maxStrengthRate * 0.5; // 아군의 체력이 50% 미만이면 최소배율 (50%) 적용
        // 배수항
        double maxJammedRate = getSum(statusEffects.get(StatusEffectType.JAMMED));
        double jammedRate = currentHpRate < 0.5 ? maxJammedRate * (1 - currentHpRate) : maxJammedRate * 0.5; // 아군의 체력이 50% 초과면 최소배율 (50%) 적용
        // 별항
        double uniqueUpRate = getSum(statusEffects.get(StatusEffectType.ATK_UP_UNIQUE));

        // 상한 하한처리
        double atkRate = Math.max(atkUpRate - atkDownRate, -0.99); // 공격력 상승 X 공격력 감소 99%
        // 장비항 상승 x 하한 x
        // 혼신항 상승 x 하한 x
        // 배수항 상승 x 하한 x
        // 별항 상승 x 하한 x

        atk = (double) battleActor.getActor().getBaseAttackPoint()
                * (1 + weaponAtkUpRate)
                * (1 + atkRate)
                * (1 + strengthRate)
                * (1 + jammedRate)
                * (1 + uniqueUpRate)
        ;

        return (int) atk;
    }

    /**
     * 방어업
     * 피격데미지 계열은 데미지 로직에서 연산
     *
     * @param battleActor
     * @param statusEffects
     * @return
     */
    protected int getDef(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double def = 0;
        // 방어인항
        double defUpRate = getSum(statusEffects.get(StatusEffectType.DEF_UP));
        double defDownRate = getSum(statusEffects.get(StatusEffectType.DEF_DOWN));

        // 상한 하한 처리
        double defRate = Math.max(defUpRate - defDownRate, -0.5); // 방어령 상승 X, 하한 -50%

        // 데미지컷, 데미지고정, 피격데미지 감소는 DamageLogic 에서, 감싸기는 적 로직에서 연산

        def = (double) battleActor.getActor().getBaseDefencePoint()
                * (1 + defRate)
        ;

        return (int) def;
    }

    // 최대체력, 수호항 등 패시브 항 만 사용
    protected int getMaxHp(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        // 수호항
        double hpWeaponRate = battleActor.getHpWeaponRate();
        // 최대 체력 감소
        double maxHpDownRate = getSum(statusEffects.get(StatusEffectType.MAX_HP_DOWN));
        //상한 하한처리
        maxHpDownRate = Math.max(maxHpDownRate, -0.99); // 상한 x 하한 -99%

        double maxHp = (double) battleActor.getActor().getBaseHitPoint()
                * (1 + hpWeaponRate)
                * (1 + maxHpDownRate);

        return (int) maxHp;
    }

    protected int getHp(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double hp = battleActor.getHp();
        double maxHp = battleActor.getMaxHp();
        hp = Math.min(hp, maxHp); // maxHp 가 줄어든경우 hp 도 따라감

        // hp 의 경우 DamageLogic 에서 데미지를 적용함, 현재 체력이 늘어나는 구현은 없음
        return (int) hp;
    }

    protected double getDoubleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseDoubleAttackRate = battleActor.getActor().getBaseDoubleAttackRate();
        double doubleAttackUpRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_UP));
        double doubleAttackDownRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_DOWN));

        // 상한 하한 처리
        double doubleAttackRate = Math.max(baseDoubleAttackRate + doubleAttackUpRate - doubleAttackDownRate, 0); // 상한 x 하한 0

        return doubleAttackRate;
    }

    protected double getTripleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseTripleAttackRate = battleActor.getActor().getBaseTripleAttackRate();
        double tripleAttackUpRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_UP));
        double tripleAttackDownRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_DOWN));

        // 상한 하한 처리
        double tripleAttackRate = Math.max(baseTripleAttackRate + tripleAttackUpRate - tripleAttackDownRate, 0); // 상한 x 하한 0

        return tripleAttackRate;
    }

    protected double getDeBuffResistRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseDeBuffResistRate = battleActor.getActor().getBaseDeBuffResistRate();
        double deBuffResistUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_UP));
        double deBuffResistDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_DOWN));

        // 상한 하한 처리
        double deBuffResistRate = Math.clamp(baseDeBuffResistRate + deBuffResistUpRate - deBuffResistDownRate, -0.99, 1.0); // 상한 1.0, 하한 - 99% 저항이 음수일경우 성공률에 양으로 곱해짐

        return deBuffResistRate;
    }

    protected double getDeBuffSuccessRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseDeBuffSuccessRate = battleActor.getActor().getBaseDeBuffSuccessRate();
        double deBuffSuccessUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_UP));
        double deBuffSuccessDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_DOWN));

        // 상한 하한 처리
        double deBuffSuccessRate = Math.max(baseDeBuffSuccessRate + deBuffSuccessUpRate - deBuffSuccessDownRate, 0); // 상한 x 하한 0 필중처리를 위해 상한 없음. (필중의 경우 100 이상 값 필수, 999예정)

        return deBuffSuccessRate;
    }

    protected double getCriticalRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseCriticalRate = battleActor.getActor().getBaseCriticalRate();
        double criticalUpRate = getSum(statusEffects.get(StatusEffectType.CRITICAL_RATE_UP));
        double criticalRate = Math.min(baseCriticalRate + criticalUpRate, 1.0); // 상한 100%, 하한 X

        return criticalRate;
    }

    protected double getCriticalDamageRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseCriticalDamageRate = battleActor.getActor().getBaseCriticalDamageRate();
        double criticalDamageUpRate = getSum(statusEffects.get(StatusEffectType.CRITICAL_DAMAGE_UP));
        double criticalDamageRate = Math.min(baseCriticalDamageRate + criticalDamageUpRate, 1.0); // 상한 100%, 하한 X

        return criticalDamageRate;
    }

    protected double getChargeGaugeIncreaseRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseChargeGaugeIncreaseRate = battleActor.getActor().getBaseChargeGaugeIncreaseRate();
        double chargeGaugeIncreaseUpRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_UP));
        double chargeGaugeIncreaseDownRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_DOWN));

        // 상한 하한처리
        double chargeGaugeIncreaseRate = Math.clamp(baseChargeGaugeIncreaseRate + chargeGaugeIncreaseUpRate - chargeGaugeIncreaseDownRate, -1, 1); // 상한 100% 하한 -100%

        return chargeGaugeIncreaseRate;
    }

    protected double getAccuracy(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseAccuracy = battleActor.getActor().getBaseAccuracy();
        double hitAccuracyUpRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_UP));
        double hitAccuracyDownRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_DOWN));

        // 상한 하한 처리
        double accuracyRate = baseAccuracy + hitAccuracyUpRate - hitAccuracyDownRate; // 상 하한은 최종 데미지 계산시 적용 (반드시 빗나감 회피 등)

        return accuracyRate;
    }

    protected Double getDodgeRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double baseDodgeRate = battleActor.getActor().getBaseDodgeRate();
        double dodgeUpRate = getSum(statusEffects.get(StatusEffectType.DODGE_RATE_UP));
        double dodgeRate = Math.min(baseDodgeRate + dodgeUpRate, 1.0); // 상 하한은 최종 데미지 계산시 적용 (반드시 회피 효과 등)

        return dodgeRate;
    }

    /**
     * 주어진 항의 버프수치 합산을 구함
     *
     * @param statusEffects 항 리스트
     * @return 합산수치, 없으면 0
     */
    protected double getSum(List<StatusEffect> statusEffects) {
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.stream()
                        .map(StatusEffect::getCalcValue) // 레벨제 계산후 반환
                        .mapToDouble(Double::doubleValue)
                        .sum();
    }

    /**
     * 주어진 항의 첫 버프값을 가져옴
     * 이 메서드는 계산이 아닌 boolean 을 위함. ( ex substitute 등 )
     *
     * @param statusEffects
     * @return
     */
    protected double getValue(List<StatusEffect> statusEffects) {
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.getFirst().getValue();
    }

}
