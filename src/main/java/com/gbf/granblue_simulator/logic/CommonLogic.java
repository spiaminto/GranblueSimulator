package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Component
@RequiredArgsConstructor
@Transactional
public class CommonLogic {

    //    public Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(BattleActor battleActor) {
//        return battleActor.getBattleStatuses().stream()
//                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().stream()
//                        .map(statusEffect -> statusEffect.setCurrentLevel(battleStatus.getLevel())).toList())
//                .flatMap(List::stream)
//                .collect(Collectors.groupingBy(
//                        StatusEffect::getType,
//                        mapping(Function.identity(), collectingAndThen(
//                                toList(), list -> list != null ? list : new ArrayList<>()
//                        )))
//                );
//    }

    public Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(BattleActor battleActor) {
        return this.getFlatStatusEffectStream(battleActor)
                .collect(Collectors.groupingBy(
                        StatusEffect::getType,
                        mapping(Function.identity(), collectingAndThen(
                                toList(), list -> list != null ? list : new ArrayList<>()
                        )))
                );
    }

    /**
     * 추격만 별도로 가져오는 메서드
     * @param battleActor
     * @return
     */
    public List<StatusEffect> getAdditionalDamageEffects(BattleActor battleActor) {
        return this.getFlatStatusEffectStream(battleActor)
                .filter(statusEffect ->
                        statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_A ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_C ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_E ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_W ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_S
                ).toList();
    }

    private Stream<StatusEffect> getFlatStatusEffectStream(BattleActor battleActor) {
        return battleActor.getBattleStatuses().stream()
                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().stream()
                        .map(statusEffect -> statusEffect.setCurrentLevel(battleStatus.getLevel())).toList())
                .flatMap(List::stream);
    }

    public boolean hasUniqueStatus(List<BattleStatus> battleStatuses, String name) {
        return battleStatuses.stream()
                .anyMatch(battleStatus -> name.equals(battleStatus.getStatus().getName()));
    }

    public int getUniqueStatusLevel(List<BattleStatus> battleStatuses, String name) {
        Optional<BattleStatus> matchedBattleStatus = battleStatuses.stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.map(BattleStatus::getLevel).orElse(0);
    }

    public boolean isUniqueStatusReachedLevel(List<BattleStatus> battleStatuses, String name, int level) {
        Optional<BattleStatus> matchedBattleStatus = battleStatuses.stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.filter(battleStatus -> battleStatus.getLevel() >= level).isPresent();
    }

    /**
     * BattleStatus 리스트를 받아 names 로 들어온 이름의 스테이터스 레벨 level 만큼 증가
     *
     * @param battleStatuses 대상이 변동될수 있으므로 대상(들의) 모든 BattleStatus 받아와 연산
     * @param names
     * @param level          올릴 레벨 -1, 레벨제 스테이터스는 1부터 시작함에 주의
     */
    public void addUniqueStatusLevel(List<BattleStatus> battleStatuses, List<String> names, int level) {
        Optional<BattleStatus> matchedBattleStatus = battleStatuses.stream()
                .filter(battleStatus -> names.stream()
                        .anyMatch(name -> name.equals(battleStatus.getStatus().getName()))
                ).findFirst();
        matchedBattleStatus.ifPresent(battleStatus -> battleStatus.addLevel(level));
    }

    public void syncStatus(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        setAtk(battleActor, statusEffects);
        setDef(battleActor, statusEffects);
        setHp(battleActor, statusEffects);

        setCriticalRate(battleActor, statusEffects);

        setDoubleAttackRate(battleActor, statusEffects);
        setTripleAttackRate(battleActor, statusEffects);

        setDeBuffResistRate(battleActor, statusEffects);
        setDeBuffSuccessRate(battleActor, statusEffects);

        setHitAccuracy(battleActor, statusEffects);
        setDodgeRate(battleActor, statusEffects);

        setChargeGaugeIncreaseRate(battleActor, statusEffects);
    }

    /**
     * 공인, 혼신, 배수, 별항
     *
     * @param battleActor
     * @param statusEffects
     * @return
     */
    public Integer setAtk(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double atk = 0;
        // 공인항
        double atkUpRate = getSum(statusEffects.get(StatusEffectType.ATK_UP));
        double atkDownRate = getSum(statusEffects.get(StatusEffectType.ATK_DOWN));
        double atkRate = Math.max(atkUpRate - atkDownRate, -0.99); // 공격력 감소는 99% 까지 적용
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

    /**
     * 방어업, 방어다운, 데미지컷, 피격데미지감소, 베리어, 감싸기
     *
     * @param battleActor
     * @param statusEffects
     * @return
     */
    public Integer setDef(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double def = 0;
        // 방어인항
        double defUpRate = getSum(statusEffects.get(StatusEffectType.DEF_UP));
        double defDownRate = getSum(statusEffects.get(StatusEffectType.DEF_DOWN));
        double defRate = Math.max(defUpRate - defDownRate, 0);
        // 데미지컷 (상한 1.0 (100%))
        double takenDamageCut = getSum(statusEffects.get(StatusEffectType.TAKEN_DAMAGE_CUT));
        takenDamageCut = Math.min(takenDamageCut, 1.0);
        // 피격 데미지 감소
        int takenDamageFixedDown = (int) getSum(statusEffects.get(StatusEffectType.TAKEN_DAMAGE_FIXED_DOWN));
        // 베리어
        int barrier = (int) getSum(statusEffects.get(StatusEffectType.BARRIER));
        // 감싸기 (1, 2 가 들어오며 2가 우선순위 더 높음)
        double substitute = getValue(statusEffects.get(StatusEffectType.SUBSTITUTE));

        def = (double) battleActor.getActor().getBaseDefencePoint() // 10
                * (1 + defRate)
        ;

        battleActor.setDefUpRate(defUpRate);
        battleActor.setDefDownRate(defDownRate);
        battleActor.setTakenDamageCut(takenDamageCut);
        battleActor.setTakenDamageFixedDown(takenDamageFixedDown);
        battleActor.setBarrier(barrier);
        battleActor.setDef((int) def);
        return 0;
    }

    public Integer setHp(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
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

    public Integer setDoubleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double doubleAttackUpRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_UP));
        double doubleAttackDownRate = getSum(statusEffects.get(StatusEffectType.DOUBLE_ATTACK_RATE_DOWN));

        battleActor.setDoubleAttackUpRate(doubleAttackUpRate);
        battleActor.setDoubleAttackDownRate(doubleAttackDownRate);
        return 0;
    }

    public Integer setTripleAttackRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double tripleAttackUpRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_UP));
        double tripleAttackDownRate = getSum(statusEffects.get(StatusEffectType.TRIPLE_ATTACK_RATE_DOWN));

        battleActor.setTripleAttackUpRate(tripleAttackUpRate);
        battleActor.setTripleAttackDownRate(tripleAttackDownRate);
        return 0;
    }

    public Integer setDeBuffResistRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double deBuffResistUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_UP));
        double deBuffResistDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_RESIST_DOWN));

        battleActor.setDeBuffResistUpRate(deBuffResistUpRate);
        battleActor.setDeBuffResistDownRate(deBuffResistDownRate);
        return 0;
    }

    public Integer setDeBuffSuccessRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double deBuffSuccessUpRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_UP));
        double deBuffSuccessDownRate = getSum(statusEffects.get(StatusEffectType.DEBUFF_SUCCESS_DOWN));

        battleActor.setDeBuffSuccessUpRate(deBuffSuccessUpRate);
        battleActor.setDeBuffSuccessDownRate(deBuffSuccessDownRate);
        return 0;
    }

    public Integer setCriticalRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double criticalUpRate = getSum(statusEffects.get(StatusEffectType.CRITICAL_RATE_UP));
        // 크리티컬은 down 없음
        battleActor.setCriticalRate(criticalUpRate);
        return 0;
    }
    // 크리티컬 데미지증가율은 고정값 (50%)

    // 오의게이지는 일단 초기화 없이. 초기화값은 0
    public Integer setChargeGaugeIncreaseRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double chargeGaugeIncreaseUpRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_UP));
        double chargeGaugeIncreaseDownRate = getSum(statusEffects.get(StatusEffectType.CHARGE_GAUGE_INCREASE_DOWN));

        battleActor.setChargeGaugeIncreaseUpRate(chargeGaugeIncreaseUpRate);
        battleActor.setChargeGaugeIncreaseDownRate(chargeGaugeIncreaseDownRate);
        return 0;
    }

    public Integer setHitAccuracy(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double hitAccuracyUpRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_UP));
        double hitAccuracyDownRate = getSum(statusEffects.get(StatusEffectType.HIT_ACCURACY_DOWN));

        battleActor.setAccuracyUpRate(hitAccuracyUpRate);
        battleActor.setAccuracyDownRate(hitAccuracyDownRate);
        return 0;
    }

    public Integer setDodgeRate(BattleActor battleActor, Map<StatusEffectType, List<StatusEffect>> statusEffects) {
        double dodgeUpRate = getSum(statusEffects.get(StatusEffectType.DODGE_RATE_UP));
        // 회피는 down 없음
        battleActor.setDodgeUpRate(dodgeUpRate);
        return 0;
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
    private double getValue(List<StatusEffect> statusEffects) {
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.getFirst().getValue();
    }

}
