package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.GetDamageResult;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final StatusUtil statusUtil;
    private Map<ProcessType, BaseCap> baseCapMap = new HashMap<>();

    public DamageLogicResult processAttack(BattleActor character, BattleActor enemy) {
        GetDamageResult attackDamage = getAttackDamage(character, enemy);
        List<Integer> damages = attackDamage.getDamages();
        List<List<Integer>> additionalDamages = attackDamage.getAdditionalDamages();
        Integer enemyHp = enemy.getHp();
        for (Integer damage : damages) {
            enemyHp -= damage;
        }
        for (List<Integer> additionalDamage : additionalDamages) {
            for (Integer damage : additionalDamage) {
                enemyHp -= damage;
            }
        }
        enemy.setHp(Math.max(enemyHp, 0));
        boolean isEnemyHpZero = enemy.getHp() == 0;

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(damages).additionalDamages(additionalDamages).isEnemyHpZero(isEnemyHpZero).build();
    }
    
    protected GetDamageResult getAttackDamage(BattleActor mainActor, BattleActor target) {
        double damageRate = 1.0; // 통상공격 배율 1.0
        Map<StatusEffectType, List<StatusEffect>> targetStatusMap = statusUtil.getStatusEffectMap(target);
        Map<StatusEffectType, List<StatusEffect>> mainActorStatusMap = statusUtil.getStatusEffectMap(mainActor);

        // 전처리
        int mainActorAtk = mainActor.getAtk();
        mainActorAtk = applyCriticalToAtk(mainActor.getCriticalRate(), mainActor.getCriticalDamageRate(), mainActorAtk);
        double damage = atkToDamage(mainActorAtk, damageRate);
        // 본 처리
        damage = applyDef(target, damage);
        damage = applyDamageCut(targetStatusMap, damage);
        damage = applyDamageCap(ProcessType.ATTACK, mainActorStatusMap, damageRate, damage);
        damage = applyDamageFix(targetStatusMap, damage);
        List<Double> additionalDamages = applyAdditionalDamage(mainActorStatusMap, damage);

        // 타수
        Double doubleAttackRate = mainActor.getDoubleAttackRate();
        Double tripleAttackRate = mainActor.getTripleAttackRate();
        int hitCount = Math.random() < doubleAttackRate ? 2 : 1;
        hitCount = Math.random() < tripleAttackRate ? 3 : hitCount;

        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .hitCount(hitCount)
                .build();

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(ProcessType.ATTACK, mainActorStatusMap, targetStatusMap, damageDto);
        damageDto = applyExDamageCap(ProcessType.ATTACK, damageDto);
        // 최종처리
        damageDto = applyHitCountAndRandom(damageDto);

        log.info("=========damage calc finished, damage = {}", damageDto);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .build();
    }

    /**
     * 오의 데미지를 계산
     *
     * @param character
     * @param enemy
     * @param damageRate 오의는 데미지 배율이 가변이므로 캐릭터 로직단계에서 전달
     * @return
     */
    public DamageLogicResult processChargeAttack(BattleActor character, BattleActor enemy, double damageRate) {
        GetDamageResult chargeAttackDamage = getChargeAttackDamage(character, enemy, damageRate);
        List<Integer> damages = chargeAttackDamage.getDamages();
        Integer enemyHp = enemy.getHp();
        for (Integer damage : damages) {
            enemyHp -= damage;
        }
        enemy.setHp(Math.max(enemyHp, 0));
        boolean isEnemyHpZero = enemy.getHp() == 0;

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(damages).isEnemyHpZero(isEnemyHpZero).build();
    }

    protected GetDamageResult getChargeAttackDamage(BattleActor mainActor, BattleActor target, double damageRate) {
        Map<StatusEffectType, List<StatusEffect>> targetStatusMap = statusUtil.getStatusEffectMap(target);
        Map<StatusEffectType, List<StatusEffect>> mainActorStatusMap = statusUtil.getStatusEffectMap(mainActor);

        // 전처리
        int mainActorAtk = mainActor.getAtk() + 50000; // 오의 고정 데미지 5만
        mainActorAtk = applyCriticalToAtk(mainActor.getCriticalRate(), mainActor.getCriticalDamageRate(), mainActorAtk);
        double damage = atkToDamage(mainActorAtk, damageRate);
        // 본 처리
        damage = applyDef(target, damage);
        damage = applyDamageCut(targetStatusMap, damage);
        damage = applyDamageCap(ProcessType.CHARGE_ATTACK, mainActorStatusMap, damageRate, damage);
        damage = applyDamageFix(targetStatusMap, damage);

        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .hitCount(1)
                .build();

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(ProcessType.CHARGE_ATTACK, mainActorStatusMap, targetStatusMap, damageDto);
        damageDto = applyExDamageCap(ProcessType.CHARGE_ATTACK, damageDto);
        // 최종처리
        damageDto = applyHitCountAndRandom(damageDto);

        log.info("=========damage calc finished, damage = {}", damageDto);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .build();
    }

    /**
     * 어빌리티 데미지를 계산
     *
     * @param character
     * @param enemy
     * @param damageRate 어빌리티는 데미지 배율이 가변이므로 로직 단계에서 넘겨줌
     * @param hitCount   어빌리티는 히트수가 가변이므로 로직단계에서 넘겨줌
     * @return
     */
    public DamageLogicResult processAbilityAttack(BattleActor character, BattleActor enemy, double damageRate, int hitCount) {
        GetDamageResult abilityDamage = getAbilityDamage(character, enemy, damageRate, hitCount);
        List<Integer> damages = abilityDamage.getDamages();
        Integer enemyHp = enemy.getHp();
        for (Integer damage : damages) {
            enemyHp -= damage;
        }
        enemy.setHp(Math.max(enemyHp, 0));
        boolean isEnemyHpZero = enemy.getHp() == 0;

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(damages).isEnemyHpZero(isEnemyHpZero).build();
    }

    protected GetDamageResult getAbilityDamage(BattleActor mainActor, BattleActor target, double damageRate, int hitCount) {
        Map<StatusEffectType, List<StatusEffect>> targetStatusMap = statusUtil.getStatusEffectMap(target);
        Map<StatusEffectType, List<StatusEffect>> mainActorStatusMap = statusUtil.getStatusEffectMap(mainActor);

        // 전처리
        int mainActorAtk = mainActor.getAtk();
        mainActorAtk = applyCriticalToAtk(mainActor.getCriticalRate(), mainActor.getCriticalDamageRate(), mainActorAtk);
        double damage = atkToDamage(mainActorAtk, damageRate);
        // 본 처리
        damage = applyDef(target, damage);
        damage = applyDamageCut(targetStatusMap, damage);
        damage = applyDamageCap(ProcessType.ABILITY, mainActorStatusMap, damageRate, damage);
        damage = applyDamageFix(targetStatusMap, damage);

        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .hitCount(hitCount)
                .build();

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(ProcessType.ABILITY, mainActorStatusMap, targetStatusMap, damageDto);
        damageDto = applyExDamageCap(ProcessType.ABILITY, damageDto);
        // 최종처리
        damageDto = applyHitCountAndRandom(damageDto);

        log.info("=========damage calc finished, damage = {}", damageDto);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .build();
    }

    /**
     * 전처리 - 크리티컬 적용
     */
    protected int applyCriticalToAtk(double criticalRate, double criticalDamageRate, int atk) {
        int result = (int) (atk * (Math.random() < criticalRate ? 1 + criticalDamageRate : 1));

        log.info("[applyCritical] criticalRate = {} criticalDamageRAte = {}, atk = {}", criticalRate, criticalDamageRate, atk);
        return result;
    }

    /**
     * 전처리 - 공격력을 배율에 따라 데미지로 변환, 공격력 전처리와 데미지 처리를 나누는 기준
     *
     * @param atk
     * @return
     */
    protected double atkToDamage(int atk, double damageRate) {
        return atk * damageRate;
    }

    /**
     * 본처리 - 데미지에 방어력 적용
     *
     * @param target
     * @param damage
     * @return
     */
    protected double applyDef(BattleActor target, double damage) {
        Integer targetDef = target.getDef();
        return damage / targetDef;
    }

    /**
     * 본처리 - 데미지에 데미지컷 적용
     *
     * @param statusEffects
     * @param damage
     * @return
     */
    protected double applyDamageCut(Map<StatusEffectType, List<StatusEffect>> statusEffects, double damage) {
        List<StatusEffect> damageCutEffects = statusEffects.get(StatusEffectType.TAKEN_DAMAGE_CUT);
        double damageCutRate = getSum(damageCutEffects);
        // 상한 하한 처리
        damageCutRate = Math.min(damageCutRate, 1.0); // 상한 100% 하한 X
        return damage * (1 - damageCutRate);
    }

    /**
     * 본처리 - 데미지 상한 적용
     *
     * @param statusEffects
     * @param damage
     * @return
     */
    protected double applyDamageCap(ProcessType type, Map<StatusEffectType, List<StatusEffect>> statusEffects, double damageRate, double damage) {
        BaseCap baseCap = baseCapMap.get(type);

        // 데미지 상한 상승, 행동별 데미지 상한 상승 버프 수치 합산
        List<StatusEffect> damageCapEffects = statusEffects.get(StatusEffectType.DAMAGE_CAP_UP);
        List<StatusEffect> moveDamageCapEffects = statusEffects.get(baseCap.getMoveDamageCapRateCapEffectType());
        double damageCapRate = getSum(damageCapEffects); // 일반 데미지 상한
        double moveDamageCapRate = getSum(moveDamageCapEffects); // 행동별 데미지 상한
        damageCapRate = Math.min(damageCapRate, baseCap.getDamageCapRateCap());
        moveDamageCapRate = Math.min(moveDamageCapRate, baseCap.getMoveDamageCapRateCap());
        double totalDamageCapRate = damageCapRate + moveDamageCapRate;

        // 데미지 상한 상승 적용 
        double damageSoftCap = (baseCap.getBaseSoftCap() * (1 + totalDamageCapRate));
        damageSoftCap = type == ProcessType.ABILITY ? damageSoftCap * damageRate : damageSoftCap; // 어빌리티는 배율 곱해서 상한 계산
        double damageHardCap = (baseCap.getBaseHardCap() * (1 + totalDamageCapRate));
        damageHardCap = type == ProcessType.ABILITY ? damageHardCap * damageRate : damageHardCap;

        if (damage > damageSoftCap) {
            damage = (damageSoftCap + (damage - damageSoftCap) * baseCap.getSoftCapApplyRate());
            if (damage > damageHardCap) {
                damage = (damageHardCap + (damage - damageHardCap) * baseCap.getHardCapApplyRate());
            }
        }

        log.info("[applyDamageCap] damageCapRate = {}, moveDamageCapRate = {}, damage = {}", damageCapRate, moveDamageCapRate, damage);
        return damage;
    }

    /**
     * 본처리 - 받는 데미지 고정 적용
     * 공격력 상승, 공격력 업 보다 이전에 처리.
     * @param statusEffects
     * @param damage
     * @return
     */
    protected double applyDamageFix(Map<StatusEffectType, List<StatusEffect>> statusEffects, double damage) {
        List<StatusEffect> takenDamageFixEffects = statusEffects.get(StatusEffectType.TAKEN_DAMAGE_FIX);
        // 배율계산이 아니라 대입이기 때문에 효과없을시 바로 반환
        if (takenDamageFixEffects == null || takenDamageFixEffects.isEmpty()) return damage;

        double takenDamageFixPoint = getSum(takenDamageFixEffects);
        takenDamageFixPoint = Math.max(takenDamageFixPoint, 1); // 상한 x, 하한 1
        damage = takenDamageFixPoint;
        return damage;
    }

    /**
     * 본처리 - 추격 반환
     *
     * @param statusEffects
     * @param damage
     * @return
     */
    protected List<Double> applyAdditionalDamage(Map<StatusEffectType, List<StatusEffect>> statusEffects, double damage) {
        // 모든 추격의 상한은 100%
        List<StatusEffect> additionalDamageA = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_A);
        double additionalARate = Math.min(getSum(additionalDamageA), 1.0); // 어빌리티항
        List<StatusEffect> additionalDamageS = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_S);
        double additionalSRate = Math.min(getSum(additionalDamageS), 1.0); // 서포트항
        List<StatusEffect> additionalDamageC = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_C);
        double additionalCRate = Math.min(getSum(additionalDamageC), 1.0); // 오의항
        List<StatusEffect> additionalDamageW = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_W);
        double additionalWRate = Math.min(getSum(additionalDamageW), 1.0); // 무기항
        List<StatusEffect> additionalDamageU = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_U);
        double additionalURate = Math.min(getSum(additionalDamageU), 1.0); // 별항

        List<Double> additionalDamages = new ArrayList<>();
        Stream.of(additionalURate, additionalWRate, additionalCRate, additionalSRate, additionalARate)
                .filter(rate -> rate > 0)
                .forEach(rate -> additionalDamages.add(damage * rate));
        return additionalDamages;
    }

    /**
     * 후처리 - 공격 데미지 상승, 공격 데미지 업, 피격 데미지 상승, 피격 데미지 업 적용
     *
     * @return
     */
    protected DamageDto applyAmplifyAndSupplementalDamage(ProcessType type, Map<StatusEffectType, List<StatusEffect>> mainActorEffects, Map<StatusEffectType, List<StatusEffect>> targetEffects, DamageDto damageDto) {
        BaseCap baseCap = baseCapMap.get(type);
        // 데미지 상승 합산 (일반 + 행동별)
        double supplementalDamageUpPoint = getSum(mainActorEffects.get(StatusEffectType.SUPPLEMENTAL_DAMAGE_UP));
        double moveSupplementalDamageUpPoint = getSum(mainActorEffects.get(baseCap.getMoveSupplementalDamageCapEffectType()));
        double takenSupplementalDamageUpPoint = getSum(targetEffects.get(StatusEffectType.TAKEN_SUPPLEMENTAL_DAMAGE_UP));
        double takenSupplementalDamageDownPoint = getSum(targetEffects.get(StatusEffectType.TAKEN_SUPPLEMENTAL_DAMAGE_DOWN));
        double totalSupplementalDamagePoint = supplementalDamageUpPoint + takenSupplementalDamageUpPoint - takenSupplementalDamageDownPoint;
        totalSupplementalDamagePoint = Math.min(totalSupplementalDamagePoint, baseCap.getSupplementalDamageCap()); // 상한처리
        double totalMoveSupplementalDamagePoint = moveSupplementalDamageUpPoint;
        totalMoveSupplementalDamagePoint = Math.min(totalMoveSupplementalDamagePoint, baseCap.getMoveSupplementalDamageCap()); // 상한처리
        double supplementalDamage = totalSupplementalDamagePoint + totalMoveSupplementalDamagePoint;

        // 데미지 업 합산 (일반 + 행동별)
        double amplifyDamageUpRate = getSum(mainActorEffects.get(StatusEffectType.AMPLIFY_DAMAGE_UP));
        double moveAmplifyDamageUpRate = getSum(mainActorEffects.get(baseCap.getMoveAmplifyDamageRateCapEffectType()));
        double takenAmplifyDamageUpRate = getSum(targetEffects.get(StatusEffectType.TAKEN_AMPLIFY_DAMAGE_UP));
        double takenAmplifyDamageDownRate = getSum(targetEffects.get(StatusEffectType.TAKEN_AMPLIFY_DAMAGE_DOWN));
        double moveTakenAmplifyDamageUpRate = getSum(targetEffects.get(baseCap.getMoveTakenAmplifyDamageUpType()));
        double moveTakenAmplifyDamageDownRate = getSum(targetEffects.get(baseCap.getMoveTakenAmplifyDamageDownType()));
        double totalAmplifyDamageRate = amplifyDamageUpRate + moveAmplifyDamageUpRate + takenAmplifyDamageUpRate - takenAmplifyDamageDownRate;
        totalAmplifyDamageRate = Math.min(totalAmplifyDamageRate, baseCap.getAmplifyDamageRateCap()); // 상한처리
        double totalMoveAmplifyDamageRate = moveTakenAmplifyDamageUpRate - moveTakenAmplifyDamageDownRate;
        totalMoveAmplifyDamageRate = Math.min(totalMoveAmplifyDamageRate, baseCap.getMoveAmplifyDamageRateCap()); // 상한처리
        double amplifyDamageRate = totalAmplifyDamageRate + totalMoveAmplifyDamageRate;

        double damage = damageDto.getDamage();
        List<Double> additionalDamages = damageDto.getAdditionalDamages();

        if (type == ProcessType.ATTACK) {
            //통상공격의 경우 공격력 업 적용 후 공격력 상승 적용
            damage = damage * (1 + amplifyDamageRate) + supplementalDamage;
            additionalDamages = additionalDamages.stream().map(additionalDamage -> additionalDamage * (1 + amplifyDamageRate) + supplementalDamage).toList();
        } else {
            // 어빌리티와 오의의 경우 공격력 상승 적용 후 공격력 업 적용
            damage = (damage + supplementalDamage) * (1 + amplifyDamageRate);
            // 추격 없음
        }

        log.info("[applyAmplifyAndSupplementalDamage] damage = {}, supplementalDamage = {}, amplifyDamageRate = {}", damage, supplementalDamage, amplifyDamageRate);
        return DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .hitCount(damageDto.getHitCount())
                .build();
    }

    /**
     * 후처리 - 대 데미지 감쇠 적용
     * 제일 마지막에 적용
     * @return
     */
    protected DamageDto applyExDamageCap(ProcessType type, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        List<Double> additionalDamages = damageDto.getAdditionalDamages();
        Integer hitCount = damageDto.getHitCount();
        if (hitCount < 1) throw new IllegalArgumentException("[applyExDamageCap], hitCount = " + hitCount);
        BaseCap baseCap = baseCapMap.get(type);
        Integer exDamageCap = baseCap.getExDamageCap();
        double exDamageCapApplyRate = baseCap.getExDamageCapApplyRate();

        if (type == ProcessType.ABILITY) {
            //어빌리티의 경우 타수별 데미지 합산 후 대데미지 감쇠 후 1타 데미지 반환
            double totalDamage = damage * hitCount;
            if (totalDamage > exDamageCap) {
                damage = (exDamageCap + (totalDamage - exDamageCap) * exDamageCapApplyRate) / hitCount;
            }
        } else {
            // 통상공격, 오의는 그냥 일괄 대데미지 감쇠적용
            if (damage > exDamageCap) {
                damage = (exDamageCap + (damage - exDamageCap) * exDamageCapApplyRate);
            }
            // 추격
            additionalDamages = additionalDamages.stream().map(additionalDamage -> {
                if (additionalDamage > exDamageCap) {
                    return exDamageCap + (additionalDamage - exDamageCap) * exDamageCapApplyRate;
                } else {
                    return additionalDamage;
                }
            }).toList();
        }

        return DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .hitCount(hitCount)
                .build();
    }

    /**
     * 최종처리 - 공격 횟수에 따라 데미지 갯수를 늘리고, 난수를 곱해 최종 resultDamage 완성
     * @param damageDto
     * @return
     */
    protected DamageDto applyHitCountAndRandom(DamageDto damageDto) {
        Integer hitCount = damageDto.getHitCount();
        double inputDamage = damageDto.getDamage();
        List<Integer> damages = new ArrayList<>(Collections.nCopies(hitCount, inputDamage)).stream()
                .map(damage -> (int) (damage * (1 - Math.random() / 100)))
                .toList();

        List<List<Integer>> additionalDamages = new ArrayList<>();
        if (!damageDto.getAdditionalDamages().isEmpty()) {
            for (double additionalDamage : damageDto.getAdditionalDamages()) {
                additionalDamages.add(new ArrayList<>(Collections.nCopies(hitCount, additionalDamage)).stream()
                        .map(addDamage -> (int) (addDamage * (1 - Math.random() / 100)))
                        .toList());
            }
        }

        return DamageDto.builder()
                .resultDamages(damages)
                .resultAdditionalDamages(additionalDamages)
                .hitCount(hitCount)
                .build();
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

    // 데미지 캡
    @Getter
    @Builder
    protected static class BaseCap {
        private final Integer baseSoftCap; // 어빌리티 데미지의 경우 어빌리티의 배율을 곱해서 상한 계산
        private final Integer baseHardCap;
        private final Integer exDamageCap; // 어빌리티 데미지의 경우 대 데미지 제한은 타수 합산값에 적용됨
        @Builder.Default
        private final Double softCapApplyRate = 0.25;
        @Builder.Default
        private final Double hardCapApplyRate = 0.1;
        @Builder.Default
        private final Double exDamageCapApplyRate = 0.01;

        private final StatusEffectType moveDamageCapRateCapEffectType; // 데미지상한 이펙트타입
        private final StatusEffectType moveSupplementalDamageCapEffectType; // 데미지상승 이펙트타입
        private final StatusEffectType moveAmplifyDamageRateCapEffectType; // 데미지업 이펙트타입

        @Builder.Default
        private final Double damageCapRateCap = 0.2; // 데미지 상한 상한
        private final Double moveDamageCapRateCap; // [행동] 데미지 상한
        @Builder.Default
        private final Integer supplementalDamageCap = 10000; // 공격 데미지 상승 상한
        private final Integer moveSupplementalDamageCap; // [행동] 공격 데미지 상승 상한
        @Builder.Default
        private final Double amplifyDamageRateCap = 0.2; // 공격 데미지 업 상한
        private final Double moveAmplifyDamageRateCap; // [행동] 공격 데미지 업 상한

        private final StatusEffectType moveTakenAmplifyDamageUpType;
        private final StatusEffectType moveTakenAmplifyDamageDownType;
        private final StatusEffectType moveTakenAttackAmplifyDamageUpType;
        private final StatusEffectType moveTakenAttackAmplifyDamageDownType;

        private final Integer takenSupplementalDamageCap = 10000; // 피격 데미지 상승 상한
        private final Double takenAmplifyDamageRateCap = 0.2; // 피격 데미지 업 상한
        private final Double moveTakenAmplifyDamageRateCap = 0.5; // 행동별 피격 데미지 업 상한
    }

    @PostConstruct
    protected void initBaseCapMap() {
        baseCapMap.put(ProcessType.ATTACK, BaseCap.builder()
                .baseSoftCap(40000)
                .baseHardCap(60000)
                .exDamageCap(660000)
                .moveDamageCapRateCapEffectType(StatusEffectType.ATTACK_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.2)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_ATTACK_DAMAGE_UP)
                .moveSupplementalDamageCap(10000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_ATTACK_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.2)
                .build());
        baseCapMap.put(ProcessType.ABILITY, BaseCap.builder()
                .baseSoftCap(30000)
                .baseHardCap(50000)
                .exDamageCap(1300000)
                .moveDamageCapRateCapEffectType(StatusEffectType.ABILITY_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.5)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_ABILITY_DAMAGE_UP)
                .moveSupplementalDamageCap(10000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_ABILITY_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.3)
                .build());
        baseCapMap.put(ProcessType.CHARGE_ATTACK, BaseCap.builder()
                .baseSoftCap(370000)
                .baseHardCap(480000)
                .exDamageCap(1300000)
                .moveDamageCapRateCapEffectType(StatusEffectType.CHARGE_ATTACK_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.75)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_CHARGE_ATTACK_DAMAGE_UP)
                .moveSupplementalDamageCap(20000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_CHARGE_ATTACK_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.5)
                .build());
    }

    @Getter
    @Builder @ToString
    protected static class DamageDto {
        private double damage;
        @Builder.Default
        private List<Double> additionalDamages = new ArrayList<>();
        private List<Integer> resultDamages;
        private List<List<Integer>> resultAdditionalDamages;
        private Integer hitCount;
        private ProcessType type;
    }

    protected enum ProcessType {
        ATTACK,
        ABILITY,
        CHARGE_ATTACK;
    }


}
