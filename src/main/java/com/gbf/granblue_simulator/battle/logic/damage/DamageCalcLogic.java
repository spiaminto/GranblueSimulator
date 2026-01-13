package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.TransactionScoped;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
@TransactionScoped
public class DamageCalcLogic {

    private final Map<MoveType, BaseCap> baseCapMap = new HashMap<>();

    @PostConstruct
    protected void initBaseCapMap() {
        baseCapMap.put(MoveType.ATTACK, BaseCap.builder()
                .baseSoftCap(40000)
                .baseHardCap(60000)
                .exDamageCap(660000)
                .build());
        baseCapMap.put(MoveType.ABILITY, BaseCap.builder()
                .baseSoftCap(10000)
                .baseHardCap(20000)
                .exDamageCap(1300000)
                .build());
        baseCapMap.put(MoveType.CHARGE_ATTACK, BaseCap.builder()
                .baseSoftCap(480000)
                .baseHardCap(660000)
                .exDamageCap(1300000)
                .build());
    }

    protected GetDamageResult getPartyDamage(Actor mainActor, Actor target, MoveType moveType, ElementType moveElementType, double damageRate, int hitCount) {

        MoveType processMoveType = moveType.getParentType(); // ATTACK || ABILITY || CHARGE_ATTACK
        if (processMoveType == MoveType.SUMMON || processMoveType == MoveType.FATAL_CHAIN || processMoveType == MoveType.SUPPORT_ABILITY)
            processMoveType = MoveType.ABILITY;

        int attackMultiHitCount = 1; // 난격 효과시 통상공격 갯수 (효과 있으면 2부터 시작)
        List<Double> additionalDamages = new ArrayList<>(); // 추격

        Status mainActorStatus = mainActor.getStatus();
        DamageStatusDetails mainActorDamageStatus = mainActor.getStatus().getSyncDamageStatus();
        Status targetStatus = target.getStatus();
        DamageStatusDetails targetDamageStatus = targetStatus.getSyncDamageStatus();

        // 전처리
        double damage = atkToDamage(mainActorStatus.getAtk(), damageRate);
        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .moveDamageType(MoveDamageType.NORMAL)
                .build();
        damageDto = applyElementTypeAdjustment(moveElementType, target.getElementType(), damageDto);
        if (damageDto.getMoveDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);
        damageDto = applyDef(target, damageDto);
        damageDto = applyDamageCap(processMoveType, mainActorDamageStatus, damageRate, damageDto);

        if (processMoveType == MoveType.ATTACK) {
            // 통상공격이면 난격, 추격 적용
            attackMultiHitCount = mainActorStatus.getStatusDetails().getCalcedAttackMultiHitCount();
            damageDto = applyAttackMultiHit(damageDto, attackMultiHitCount);
            damageDto = applyAdditionalDamage(mainActor, damageDto);
        }

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(processMoveType, mainActorDamageStatus, targetDamageStatus, damageDto);
        damageDto = applyDamageFix(targetDamageStatus, damageDto);
        damageDto = applyDamageBlock(targetDamageStatus, damageDto);
        damageDto = applyDamageCut(targetDamageStatus, damageDto);
        damageDto = applyExDamageCap(processMoveType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, processMoveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("==========[getDamage] party damage calc finished, mainActorName = {}, targetActorName = {}, processType = {} damageDto = {}, attackMultiHitCount = {} elementTypes = {}", mainActor.getName(), target.getName(), processMoveType, damageDto, attackMultiHitCount, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .attackMultiHitCount(attackMultiHitCount)
                .elementTypes(List.of(moveElementType))
                .damageType(damageDto.getMoveDamageType())
                .build();
    }


    protected GetDamageResult getEnemyDamage(Actor mainActor, Actor target, MoveType moveType, ElementType moveElementType, double damageRate) {
        log.info("========== [getEnemyDamage] start calc enemy damage mainActorName = {}, targetName = {}", mainActor.getName(), target.getName());

        MoveType processMoveType = moveType.getParentType(); // ATTACK || ABILITY || CHARGE_ATTACK
        if (processMoveType == MoveType.SUMMON || processMoveType == MoveType.FATAL_CHAIN || processMoveType == MoveType.SUPPORT_ABILITY)
            processMoveType = MoveType.ABILITY;

        int hitCount = 1; // 적의 공격은 1회 1히트가 원칙
        int attackMultiHitCount = 1; // 현재 적은 난격효과를 적용하지 않음

        Status mainActorStatus = mainActor.getStatus();
        DamageStatusDetails mainActorDamageStatus = mainActorStatus.getSyncDamageStatus();
        Status targetActorStatus = target.getStatus();
        DamageStatusDetails targetDamageStatus = targetActorStatus.getSyncDamageStatus();

        // 전처리
        double damage = atkToDamage(mainActorStatus.getAtk(), damageRate);
        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .moveDamageType(MoveDamageType.NORMAL)
                .build();
        damageDto = applyElementTypeAdjustment(moveElementType, target.getElementType(), damageDto);
        if (damageDto.getMoveDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);
        damageDto = applyDef(target, damageDto);
        damageDto = applyDamageCap(processMoveType, mainActorDamageStatus, damageRate, damageDto);

        damageDto = applyAdditionalDamage(mainActor, damageDto);

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(processMoveType, mainActorDamageStatus, targetDamageStatus, damageDto);
        damageDto = applyDamageFix(targetDamageStatus, damageDto);
        damageDto = applyDamageBlock(targetDamageStatus, damageDto);
        damageDto = applyDamageCut(targetDamageStatus, damageDto);
        damageDto = applyExDamageCap(processMoveType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, processMoveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("========== [getEnemyDamage] enemy damage calc finished, mainActorName = {}, targetName = {}, processType = {} damageDto = {}, moveElementType = {}", mainActor.getName(), target.getName(), processMoveType, damageDto, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .elementTypes(List.of(moveElementType))
                .damageType(damageDto.getMoveDamageType())
                .attackMultiHitCount(attackMultiHitCount)
                .build();
    }


    /**
     * 전처리 - 공격력을 배율에 따라 데미지로 변환,
     *
     * @param atk
     * @return
     */
    protected double atkToDamage(int atk, double damageRate) {
        log.info("[atkToDamage] atk = {}, resultDamage = {},  damageRate = {}", atk, atk * damageRate, damageRate);
        return atk * damageRate;
    }

    /**
     * 전처리 - 크리티컬 적용
     */
    protected DamageDto applyCriticalRate(double criticalRate, double criticalDamageRate, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        boolean isCritical = Math.random() < criticalRate;
        double resultDamage = damage * (isCritical ? 1 + criticalDamageRate : 1);
        MoveDamageType damageType = isCritical ? MoveDamageType.CRITICAL : damageDto.getMoveDamageType();
        log.info("[applyCritical] damage = {}, resultDamage = {},  criticalRate = {} isCritical = {}, criticalDamageRAte = {}", damage, resultDamage, criticalRate, isCritical, criticalDamageRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .moveDamageType(damageType)
                .build();
    }

    /**
     * 전처리 - 유리속성 보정
     */
    protected DamageDto applyElementTypeAdjustment(ElementType moveElementType, ElementType targetElementType, DamageDto damageDto) {
        double resultDamage = 0;
        double damage = damageDto.getDamage();
        MoveDamageType damageType = damageDto.getMoveDamageType();
        if (moveElementType.isAdvantageTo(targetElementType)) {
            resultDamage = damage * 1.5;
            damageType = MoveDamageType.ADVANTAGED;
        } else if (moveElementType.isDisadvantageTo(targetElementType)) {
            resultDamage = damage * 0.75;
            damageType = MoveDamageType.DISADVANTAGED;
        } else {
            resultDamage = damage; // 무상성시 1배율
        }
        log.info("[applyElementTypeAdjustment] damage = {}, resultDamage = {}, moveElementType = {}, targetElementType = {}", damage, resultDamage, moveElementType, targetElementType);
        return DamageDto.builder()
                .damage(resultDamage)
                .moveDamageType(damageType)
                .build();
    }

    /**
     * 본처리 - 데미지에 방어력 적용
     *
     * @param target
     * @param damageDto
     * @return
     */
    protected DamageDto applyDef(Actor target, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        double targetDef = target.getStatus().getDef();
        targetDef = target.isGuardOn() ? targetDef * 10 : targetDef;
        double resultDamage = damage / targetDef;
        log.info("[applyDef] damage = {}, resultDamage = {}, targetDef = {}, isGuardOn = {}", damage, resultDamage, targetDef, target.isGuardOn());
        return DamageDto.builder()
                .damage(resultDamage)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 본처리 - 데미지 상한 적용
     *
     * @param damageStatus
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageCap(MoveType moveType, DamageStatusDetails damageStatus, double damageRate, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        BaseCap baseCap = baseCapMap.get(moveType);

        double resultDamage = 0;

        // 1. 데미지 상한 상승 효과 확인
        double damageCapUpRate = damageStatus.getDamageCapUpRate(); // 일반 데미지 상한 상승
        double moveDamageCapUpRate = damageStatus.getMoveDamageCapUpRate(moveType); // 행동별 데미지 상한 상승
        double totalDamageCapUpRate = damageCapUpRate + moveDamageCapUpRate; // 총 데미지 상한 상승

        // 2. 기본 데미지 상한에 데미지 상한 상승 적용
        double damageSoftCap = (baseCap.getBaseSoftCap() * (1 + totalDamageCapUpRate));
        double damageHardCap = (baseCap.getBaseHardCap() * (1 + totalDamageCapUpRate));
        if (moveType == MoveType.ABILITY) {
            // 어빌리티는 1타 당 루트배율 만큼 상한지정 (10000기준 5배 22360, 10배 31622, 15배 38729, 20배 44721)
            damageSoftCap = damageSoftCap * Math.sqrt(damageRate);
            damageHardCap = damageHardCap * Math.sqrt(damageRate);
        }

        // 3. 데미지에 데미지 상한 실적용
        if (damage > damageSoftCap) {
            resultDamage = damageSoftCap
                    + (damage - damageSoftCap) * baseCap.getSoftCapApplyRate(); // 초과분 감쇠
            if (resultDamage > damageHardCap) {
                resultDamage = damageHardCap
                        + (resultDamage - damageHardCap) * baseCap.getHardCapApplyRate(); // 초과분 감쇠
            }
        } else {
            resultDamage = damage;
        }

        log.info("[applyDamageCap] damage = {}, resultDamage = {}, damageCapRate = {}, moveDamageCapRate = {}", damage, resultDamage, damageCapUpRate, moveDamageCapUpRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 난격 적용 (난격 효과에 따른 데미지 감소 적용. 히트수는 히트수 적용에서 적용함)
     *
     * @param damageDto
     * @return
     */
    protected DamageDto applyAttackMultiHit(DamageDto damageDto, int attackMultiHitCount) {
        double damage = damageDto.getDamage();
        double resultDamage = attackMultiHitCount > 1 ? damage / attackMultiHitCount : damage;
        log.info("[applyAttackMultiHit] damage = {}, resultDamage = {}, attackMultiHitCount = {}", damage, resultDamage, attackMultiHitCount);
        return DamageDto.builder()
                .damage(resultDamage)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 본처리 - 추격 반환
     *
     * @param actor
     * @param damageDto
     * @return
     */
    protected DamageDto applyAdditionalDamage(Actor actor, DamageDto damageDto) {
        double damage = damageDto.getDamage();

        List<Double> additionalDamages = new ArrayList<>();
        actor.getStatus().getStatusDetails().getCalcedAdditionalDamageRateList().stream()
                .filter(rate -> rate > 0)
                .forEach(rate -> additionalDamages.add(damage * rate));
        return DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }


    /**
     * 후처리 - 공격 데미지 상승, 공격 데미지 업, 피격 데미지 상승, 피격 데미지 업 적용
     *
     * @return
     */
    protected DamageDto applyAmplifyAndSupplementalDamage(MoveType moveType, DamageStatusDetails damageStatus, DamageStatusDetails targetDamageStatus, DamageDto damageDto) {
        // 1. 공격 데미지 증가 및 피격데미지 증가, 감소 확인
        int supplementalDamage = damageStatus.getSupplementalDamage();
        int moveSupplementalDamage = damageStatus.getMoveSupplementalDamage(moveType);
        int takenSupplementalDamageUpPoint = targetDamageStatus.getTakenSupplementalDamageUpPoint();
        int takenSupplementalDamageDownPoint = targetDamageStatus.getTakenSupplementalDamageDownPoint();
        double totalSupplementalDamage = supplementalDamage + moveSupplementalDamage
                + takenSupplementalDamageUpPoint - takenSupplementalDamageDownPoint;

        // 2. 공격 데미지 상승 및 피격 데미지 상승, 감소 확인
        double amplifyDamageUpRate = damageStatus.getAmplifyDamageRate();
        double moveAmplifyDamageUpRate = damageStatus.getMoveAmplifyDamageRate(moveType);
        double takenAmplifyDamageUpRate = targetDamageStatus.getTakenAmplifyDamageUpRate();
        double takenAmplifyDamageDownRate = targetDamageStatus.getTakenAmplifyDamageDownRate();
        double moveTakenAmplifyDamageUpRate = targetDamageStatus.getMoveTakenDamageUpRate(moveType);
        double moveTakenAmplifyDamageDownRate = targetDamageStatus.getMoveTakenDamageDownRate(moveType);
        double totalAmplifyDamageRate =
                amplifyDamageUpRate + moveAmplifyDamageUpRate + takenAmplifyDamageUpRate + moveTakenAmplifyDamageUpRate
                        - takenAmplifyDamageDownRate - moveTakenAmplifyDamageDownRate;

        double damage = damageDto.getDamage();
        double resultDamage = 0;
        List<Double> additionalDamages = damageDto.getAdditionalDamages();

        if (moveType == MoveType.ATTACK) {
            //통상공격의 경우 공격력 업 적용 후 공격력 상승 적용
            resultDamage = damage * (1 + totalAmplifyDamageRate) + totalSupplementalDamage;
            additionalDamages = additionalDamages.stream().map(additionalDamage -> additionalDamage * (1 + totalAmplifyDamageRate) + totalSupplementalDamage).toList();
        } else {
            // 어빌리티와 오의의 경우 공격력 상승 적용 후 공격력 업 적용
            resultDamage = (damage + totalSupplementalDamage) * (1 + totalAmplifyDamageRate);
            // 추격 없음
        }

        // 요다메에 의한 데미지가 음수일경우 0으로 보정
        resultDamage = Math.max(resultDamage, 0);
        additionalDamages = additionalDamages.stream().map(additionalDamage -> Math.max(additionalDamage, 0)).toList();

        log.info("[applyAmplifyAndSupplementalDamage] damage = {}, resultDamage = {}, supplementalDamage = {}, amplifyDamageRate = {}", damage, resultDamage, totalSupplementalDamage, totalAmplifyDamageRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(additionalDamages)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 후처리 - 받는 데미지 고정 적용
     *
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageFix(DamageStatusDetails targetStatus, DamageDto damageDto) {
        final int takenDamageFixPoint = targetStatus.getTakenDamageFixPoint();
        if (takenDamageFixPoint <= 0) return damageDto; // 없으면 바로 반환

        double resultDamage = Math.min(damageDto.getDamage(), takenDamageFixPoint);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> Math.min(additionalDamage, takenDamageFixPoint)).toList();

        log.info("[applyDamageFix] damage = {}, resultDamage = {}, damageFixPoint = {}", damageDto.getDamage(), resultDamage, takenDamageFixPoint);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    protected DamageDto applyDamageBlock(DamageStatusDetails targetStatus, DamageDto damageDto) {
        double takenDamageBlockRate = targetStatus.getTakenDamageBlockRate();
        if (takenDamageBlockRate <= 0) return damageDto;

        boolean isBlocked = Math.random() >= 0.5; // 확률 50% 고정
        if (!isBlocked) return damageDto;

        MoveDamageType damageType = MoveDamageType.BLOCK;
        final double damageBlockRate = Math.min(takenDamageBlockRate, 1.0); // 상한 100% 하한 X
        double resultDamage = damageDto.getDamage() * (1 - damageBlockRate);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage * (1 - damageBlockRate)).toList();

        log.info("[applyDamageBlock] damage = {}, resultDamage = {}, damageBlockRate = {}", damageDto.getDamage(), resultDamage, damageBlockRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .moveDamageType(damageType)
                .build();
    }

    /**
     * 후처리 - 데미지에 데미지컷 적용
     *
     * @param targetStatus
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageCut(DamageStatusDetails targetStatus, DamageDto damageDto) {
        double takenDamageCutRate = targetStatus.getTakenDamageCutRate();
        if (takenDamageCutRate <= 0) return damageDto;

        MoveDamageType damageType = damageDto.getMoveDamageType() == MoveDamageType.BLOCK ? MoveDamageType.BLOCK_CUT : MoveDamageType.CUT;
        final double damageCutRate = Math.min(takenDamageCutRate, 1.0); // 상한 100% 하한 X
        double resultDamage = damageDto.getDamage() * (1 - damageCutRate);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage * (1 - damageCutRate)).toList();

        log.info("[applyDamageCut] damage = {}, resultDamage = {}, damageCutRate = {}", damageDto.getDamage(), resultDamage, damageCutRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .moveDamageType(damageType)
                .build();
    }

    /**
     * 후처리 - 대 데미지 감쇠 적용
     * 제일 마지막에 적용
     *
     * @return
     */
    protected DamageDto applyExDamageCap(MoveType moveType, DamageDto damageDto, int hitCount) {
        double damage = damageDto.getDamage();
        double resultDamage = 0;
        List<Double> additionalDamages = damageDto.getAdditionalDamages();
        if (hitCount < 1)
            throw new IllegalArgumentException("[applyExDamageCap], hitCount less than 1 , hitCount = " + hitCount);
        BaseCap baseCap = baseCapMap.get(moveType);
        Integer exDamageCap = baseCap.getExDamageCap();
        double exDamageCapApplyRate = baseCap.getExDamageCapApplyRate();

        if (moveType == MoveType.ABILITY) {
            //어빌리티의 경우 타수별 데미지 합산 후 대데미지 감쇠 후 1타 데미지 반환
            double totalDamage = damage * hitCount;
            if (totalDamage > exDamageCap) {
                resultDamage = (exDamageCap + (totalDamage - exDamageCap) * exDamageCapApplyRate) / hitCount;
            } else {
                resultDamage = damage;
            }
        } else {
            // 통상공격, 오의는 그냥 일괄 대데미지 감쇠적용
            if (damage > exDamageCap) {
                resultDamage = (exDamageCap + (damage - exDamageCap) * exDamageCapApplyRate);
            } else {
                resultDamage = damage;
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

        log.info("[applyExDamageCap] damage = {}, hitCount = {}, resultDamage = {}, moveType = {}", damage, hitCount, resultDamage, moveType);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(additionalDamages)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 타겟과 메인캐릭터의 명중/회피율 을 계산하여 최종 명중률을 반환
     *
     * @param mainActor
     * @param targetActor
     * @return
     */
    protected double getAccuracyRate(Actor mainActor, Actor targetActor, MoveType moveType) {
        Double mainActorAccuracyRate = mainActor.getStatus().getAccuracyRate(); // 기본값 1, 상한 하한 없음
        mainActorAccuracyRate = moveType == MoveType.CHARGE_ATTACK && !mainActor.isEnemy() ?
                mainActorAccuracyRate + 1 : mainActorAccuracyRate; // 아군 오의의 경우 기본 명중률을 2로
        Double targetActorDodgeRate = targetActor.getStatus().getDodgeRate(); // 기본값 0.01 (또는 0.0), 상한 하한 없음
        double resultAccuracyRate = Math.max(mainActorAccuracyRate - targetActorDodgeRate, 0.0); // 하한 0
        log.info("[getAccuracyRate] mainActorName = {}, targetActorName = {}, accuracyRate = {}", mainActor.getName(), targetActor.getName(), resultAccuracyRate);
        return resultAccuracyRate;
    }

    /**
     * 최종처리 - 공격 횟수에 따라 데미지 갯수를 늘리고, 명중 처리와 난수처리를 한 최종 데미지 반환
     * 회피시 데미지는 -1 로 반환
     * 난수에 의한 변화치는 -5% ~ +5%
     *
     * @param damageDto
     * @return
     */
    protected DamageDto applyHitCountAndRandom(DamageDto damageDto, int hitCount, int attackMultiHitCount, double accuracyRate) {
        double inputDamage = damageDto.getDamage();
        List<List<String>> damageTypes = new ArrayList<>();
        List<Integer> damages = Collections.nCopies(hitCount * attackMultiHitCount, inputDamage)
                .stream()
                .map(damage ->
                        Math.random() < accuracyRate ? (int) (damage * (1 + (Math.random() * 0.1 - 0.05))) : -1 // 명중 및 난수처리
                )
                .toList();

        List<List<Integer>> additionalDamagesList = new ArrayList<>();
        if (!damageDto.getAdditionalDamages().isEmpty()) {
            int totalHits = hitCount * attackMultiHitCount;
            additionalDamagesList = IntStream.range(0, totalHits)
                    .mapToObj(i -> {
                        // 난수처리
                        List<Integer> processed = damageDto.getAdditionalDamages()
                                .stream()
                                .map(additionalDamage -> (int) (additionalDamage * (1 + (Math.random() * 0.1 - 0.05))))
                                .toList();
                        // 명중처리 (본공격이 회피-1 할경우 회피)
                        return damages.get(i) == -1 ?
                                Collections.nCopies(processed.size(), -1) :
                                processed;
                    })
                    .toList();
        }

        log.info("[applyHitCountAndRandom] hitCount = {}, attackMultiHitCount = {}", hitCount, attackMultiHitCount);
        return DamageDto.builder()
                .resultDamages(damages)
                .resultAdditionalDamages(additionalDamagesList)
                .moveDamageType(damageDto.getMoveDamageType())
                .build();
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
    }

    @Getter
    @Builder
    @ToString
    protected static class DamageDto {
        private MoveDamageType moveDamageType;
        private double damage;
        @Builder.Default
        private List<Double> additionalDamages = new ArrayList<>();
        @Builder.Default
        private List<List<String>> damageType = new ArrayList<>(); // [ [damage], [additional 1-1, additional 1-2, ...] [additional 2-1, ...] ], 사용 안할때는 빈 배열로 유지

        private List<Integer> resultDamages;
        private List<List<Integer>> resultAdditionalDamages;
    }


}
