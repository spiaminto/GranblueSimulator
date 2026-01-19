package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
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

    /**
     * 아군 캐릭터의 행동에 의한 데미지를 계산
     *
     * @param mainActor       행동한 캐릭터
     * @param target          데미지 발생 타겟 [일반적으로 적]
     * @param moveType        데미지가 발생하는 행동
     * @param moveElementType move.elementType, 6속성 만 허용
     * @param damageRate      데미지 배율
     * @param hitCount        히트수
     * @return
     */
    protected GetDamageResult getPartyDamage(Actor mainActor, Actor target, MoveType moveType, ElementType moveElementType, double damageRate, int hitCount) {

        MoveType processMoveType = moveType.getParentType(); // ATTACK || ABILITY || CHARGE_ATTACK
        if (processMoveType == MoveType.SUMMON || processMoveType == MoveType.FATAL_CHAIN || processMoveType == MoveType.SUPPORT_ABILITY)
            processMoveType = MoveType.ABILITY;

        int attackMultiHitCount = 1; // 난격 효과시 통상공격 갯수 (효과 있으면 2부터 시작)

        Status mainActorStatus = mainActor.getStatus();
        DamageStatusDetails mainActorDamageStatus = mainActor.getStatus().getSyncDamageStatus();
        Status targetStatus = target.getStatus();
        DamageStatusDetails targetDamageStatus = targetStatus.getSyncDamageStatus();

        double atk = applyElementTypeToAtk(moveElementType, mainActor); // 속변과 상관없이 자속성 공격력 증가는 적용
        ElementType damageElementType = applyElementSwitch(moveElementType, targetDamageStatus);
        double baseDamage = applyDamageRate(damageRate, atk);
        DamageDto damageDto = DamageDto.builder()
                .elementType(damageElementType)
                .moveDamageType(MoveDamageType.NORMAL)
                .damage(baseDamage)
                .build();

        damageDto = applyElementTypeAdjustment(target.getElementType(), targetDamageStatus, damageDto);

        if (damageDto.getMoveDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);

        damageDto = applyDef(target, damageDto);

        damageDto = applyDamageCap(processMoveType, mainActorDamageStatus, damageRate, damageDto);

        damageDto = applyElementalDamageDown(targetDamageStatus, damageDto);

        damageDto = applyDamageBlock(targetDamageStatus, damageDto);

        damageDto = applyDamageCut(targetDamageStatus, damageDto);

        if (processMoveType == MoveType.ATTACK) {
            // 통상공격이면 난격, 추격 적용
            attackMultiHitCount = mainActorStatus.getStatusDetails().getCalcedAttackMultiHitCount();
            damageDto = applyAttackMultiHit(damageDto, attackMultiHitCount);
            damageDto = applyAdditionalDamage(mainActor, damageDto);
        }

        damageDto = applyAmplifyAndSupplementalDamage(processMoveType, mainActorDamageStatus, targetDamageStatus, damageDto);

        damageDto = applyExDamageCap(processMoveType, damageDto, hitCount);

        double accuracyRate = getAccuracyRate(mainActor, target, processMoveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        damageDto = applyDamageFix(targetDamageStatus, damageDto);

        log.info("==========[getDamage] party damage calc finished, mainActorName = {}, targetActorName = {}, processType = {} damageDto = {}, attackMultiHitCount = {} elementTypes = {}", mainActor.getName(), target.getName(), processMoveType, damageDto, attackMultiHitCount, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .attackMultiHitCount(attackMultiHitCount)
                .elementTypes(List.of(damageDto.getElementType()))
                .damageType(damageDto.getMoveDamageType())
                .build();
    }

    /**
     * 적의 행동으로 인한 데미지 계산, 적은 히트수가 1로 고정됨
     *
     * @param mainActor
     * @param target
     * @param moveType
     * @param moveElementType 6속성만 허용
     * @param damageRate      데미지 배율
     * @return
     */
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


        double atk = applyElementTypeToAtk(moveElementType, mainActor);
        ElementType damageElementType = applyElementSwitch(moveElementType, targetDamageStatus);
        double baseDamage = applyDamageRate(damageRate, atk);
        DamageDto damageDto = DamageDto.builder()
                .elementType(damageElementType)
                .moveDamageType(MoveDamageType.NORMAL)
                .damage(baseDamage)
                .build();

        damageDto = applyElementTypeAdjustment(target.getElementType(), targetDamageStatus, damageDto);

        if (damageDto.getMoveDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);

        damageDto = applyDef(target, damageDto);

        damageDto = applyDamageCap(processMoveType, mainActorDamageStatus, damageRate, damageDto);

        damageDto = applyElementalDamageDown(targetDamageStatus, damageDto);

        damageDto = applyDamageBlock(targetDamageStatus, damageDto);

        damageDto = applyDamageCut(targetDamageStatus, damageDto);

        damageDto = applyAdditionalDamage(mainActor, damageDto); // 적은 난격이 없고, 특수기에도 추격이 붙음

        damageDto = applyAmplifyAndSupplementalDamage(processMoveType, mainActorDamageStatus, targetDamageStatus, damageDto);

        damageDto = applyExDamageCap(processMoveType, damageDto, hitCount);

        double accuracyRate = getAccuracyRate(mainActor, target, processMoveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        damageDto = applyDamageFix(targetDamageStatus, damageDto);

        log.info("========== [getEnemyDamage] enemy damage calc finished, mainActorName = {}, targetName = {}, processType = {} damageDto = {}, moveElementType = {}", mainActor.getName(), target.getName(), processMoveType, damageDto, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .elementTypes(List.of(damageDto.getElementType()))
                .damageType(damageDto.getMoveDamageType())
                .attackMultiHitCount(attackMultiHitCount)
                .build();
    }


    /**
     * 공격력을 배율에 따라 데미지로 변환,
     *
     * @param elementType
     * @param targetActor
     * @return
     */
    protected double applyElementTypeToAtk(ElementType elementType, Actor targetActor) {
        int hpRate = targetActor.getHpRate();
        int calcedAtk = targetActor.getStatus().getStatusDetails().getCalcedAtk(hpRate, elementType);// actor.getStatus().getDef() 사용하지 않기
        log.info("[applyElementTypeToAtk] elementType = {}, calcedAtk = {}", elementType, calcedAtk);
        return calcedAtk;
    }

    protected double applyDamageRate(double damageRate, double atk) {
        double resultDamage = atk * damageRate;
        log.info("[applyDamageRate] damageRate = {}, atk = {}, resultDamage = {}", damageRate, atk, resultDamage);
        return resultDamage;
    }

    /**
     * 피격 속성 변환 적용에 따른 속성 반환
     *
     * @param originalElementType
     * @param targetDamageStatus
     * @return
     */
    protected ElementType applyElementSwitch(ElementType originalElementType, DamageStatusDetails targetDamageStatus) {
        ElementType elementSwitchType = targetDamageStatus.getElementSwitchType();
        ElementType resultType = originalElementType.isElementalType() && elementSwitchType == ElementType.NONE
                ? originalElementType : elementSwitchType;
        log.info("[applyElementSwitch] beforeElementType = {}, elementSwitchType = {}", originalElementType, resultType);
        return resultType;
    }

    /**
     * 유리속성 보정
     */
    protected DamageDto applyElementTypeAdjustment(ElementType targetElementType, DamageStatusDetails targetDamageStatus, DamageDto damageDto) {
        double resultDamage = 0;
        double damage = damageDto.getDamage();
        MoveDamageType damageType = damageDto.getMoveDamageType();
        ElementType damageElementType = damageDto.getElementType();

        boolean isWeakenFor = targetDamageStatus.isWeakenFor(damageElementType);
        if (isWeakenFor) {
            // '약점속성 적용' 효과 적용시
            resultDamage = damage * 1.5;
            damageType = MoveDamageType.ADVANTAGED;
        } else if (damageElementType.isAdvantageTo(targetElementType)) {
            // 약점 속성일시
            resultDamage = damage * 1.5;
            damageType = MoveDamageType.ADVANTAGED;
        } else if (damageElementType.isDisadvantageTo(targetElementType)) {
            // 불리 속성일시
            resultDamage = damage * 0.75;
            damageType = MoveDamageType.DISADVANTAGED;
        } else {
            resultDamage = damage; // 무상성시 1배율
        }
        log.info("[applyElementTypeAdjustment] damage = {}, resultDamage = {}, isWeakenFor = {}, damageElementType = {}, targetElementType = {}", damage, resultDamage, isWeakenFor, damageElementType, targetElementType);
        return DamageDto.builder()
                .elementType(damageElementType)
                .moveDamageType(damageType)
                .damage(resultDamage)
                .build();
    }

    /**
     * 크리티컬 적용
     */
    protected DamageDto applyCriticalRate(double criticalRate, double criticalDamageRate, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        boolean isCritical = Math.random() < criticalRate;
        double resultDamage = damage * (isCritical ? 1 + criticalDamageRate : 1);
        MoveDamageType damageType = isCritical ? MoveDamageType.CRITICAL : damageDto.getMoveDamageType();
        log.info("[applyCritical] damage = {}, resultDamage = {},  criticalRate = {} isCritical = {}, criticalDamageRAte = {}", damage, resultDamage, criticalRate, isCritical, criticalDamageRate);
        return DamageDto.builder()
                .elementType(damageDto.getElementType())
                .moveDamageType(damageType)
                .damage(resultDamage)
                .build();
    }

    /**
     * 본처리 - 데미지에 방어력, 속성 방어력 적용
     *
     * @param target
     * @param damageDto
     * @return
     */
    protected DamageDto applyDef(Actor target, DamageDto damageDto) {
        double damage = damageDto.getDamage();
        ElementType damageElementType = damageDto.getElementType();
        double targetDef = target.getStatus().getStatusDetails().getCalcedDef(damageElementType);// target.getStatus().getDef() 사용하지 않도록 주의

        targetDef = target.isGuardOn() ? targetDef * 10 : targetDef;
        double resultDamage = damage / targetDef;
        log.info("[applyDef] damage = {}, resultDamage = {}, targetDef = {}, isGuardOn = {}", damage, resultDamage, targetDef, target.isGuardOn());
        return DamageDto.builder()
                .elementType(damageElementType)
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(damage)
                .additionalDamages(additionalDamages)
                .build();
    }


    /**
     * 공격 데미지 상승, 공격 데미지 업, 피격 데미지 상승, 피격 데미지 업 적용
     *
     * @return
     */
    protected DamageDto applyAmplifyAndSupplementalDamage(MoveType moveType, DamageStatusDetails damageStatus, DamageStatusDetails targetDamageStatus, DamageDto damageDto) {
        // CHECK 기본적으로 데미지컷에 의해 받는데미지가 0이 되는 경우 데미지 상승 관련 효과가 적용되지 않는다고 함.
        if (targetDamageStatus.getTakenDamageCutRate() + targetDamageStatus.getTakenElementDamageCutRate(damageDto.getElementType()) >= 1.0) return damageDto;

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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
                .additionalDamages(additionalDamages)
                .build();
    }

    protected DamageDto applyElementalDamageDown(DamageStatusDetails targetStatus, DamageDto damageDto) {
        double takenElementDamageDownRate = targetStatus.getTakenElementDamageDownRate(damageDto.getElementType());
        if (takenElementDamageDownRate <= 0) return damageDto;
        double resultDamage = damageDto.getDamage() * (1 - takenElementDamageDownRate);
        log.info("[applyElementalDamageDown] damage = {}, resultDamage = {}, elementDamageDownRate = {}", damageDto.getDamage(), resultDamage, takenElementDamageDownRate);
        return DamageDto.builder()
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageType)
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .build();
    }

    /**
     * 데미지에 데미지컷 적용, 블록 다음에 적용요망
     *
     * @param targetStatus
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageCut(DamageStatusDetails targetStatus, DamageDto damageDto) {
        double takenDamageCutRate = targetStatus.getTakenDamageCutRate();
        double takenElementDamageCutRate = targetStatus.getTakenElementDamageCutRate(damageDto.getElementType());
        double resultCutRate = Math.min(1.0, takenDamageCutRate + takenElementDamageCutRate); // 상한 100%
        if (resultCutRate <= 0) return damageDto;

        MoveDamageType damageType = damageDto.getMoveDamageType() == MoveDamageType.BLOCK ? MoveDamageType.BLOCK_CUT : MoveDamageType.CUT;
        double resultDamage = damageDto.getDamage() * (1 - resultCutRate);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage * (1 - resultCutRate)).toList();

        log.info("[applyDamageCut] damage = {}, resultDamage = {}, damageCutRate = {}", damageDto.getDamage(), resultDamage, resultCutRate);
        return DamageDto.builder()
                .elementType(damageDto.getElementType())
                .moveDamageType(damageType)
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .build();
    }

    /**
     * 대 데미지 감쇠 적용
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
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .damage(resultDamage)
                .additionalDamages(additionalDamages)
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
        List<Integer> damages = Collections.nCopies(hitCount * attackMultiHitCount, inputDamage)
                .stream()
                .map(damage ->
                        Math.random() < accuracyRate ? (int) (damage * (1 + (Math.random() * 0.1 - 0.05))) : -1) // 명중 및 난수처리
                .toList();

        List<List<Integer>> additionalDamagesList = new ArrayList<>();
        if (!damageDto.getAdditionalDamages().isEmpty()) {
            int totalHits = hitCount * attackMultiHitCount;
            additionalDamagesList = IntStream.range(0, totalHits)
                    .mapToObj(i -> damages.get(i) == -1 // 추격은 본 공격의 명중여부를 그대로 따라감
                            ? Collections.nCopies(damageDto.getAdditionalDamages().size(), -1)
                            : damageDto.getAdditionalDamages().stream().map(additionalDamage -> (int) (additionalDamage * (1 + (Math.random() * 0.1 - 0.05)))).toList())
                    .toList();
        }

        log.info("[applyHitCountAndRandom] hitCount = {}, attackMultiHitCount = {}", hitCount, attackMultiHitCount);
        return DamageDto.builder()
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .resultDamages(damages)
                .resultAdditionalDamages(additionalDamagesList)
                .build();
    }

    /**
     * 받는 데미지 고정 적용. 난수 적용 이후 적용 요망
     *
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageFix(DamageStatusDetails targetStatus, DamageDto damageDto) {
        final int takenDamageFixPoint = targetStatus.getTakenDamageFixPoint();
        if (takenDamageFixPoint <= 0) return damageDto; // 없으면 바로 반환

        List<Integer> resultDamages = damageDto.getResultDamages().stream().map(damage -> Math.min(damage, takenDamageFixPoint)).toList();
        List<List<Integer>> resultAdditionalDamages = damageDto.getResultAdditionalDamages().stream().map(additionalDamages -> additionalDamages.stream().map(additionalDamage -> Math.min(additionalDamage, takenDamageFixPoint)).toList()).toList();

        log.info("[applyDamageFix] takenDamageFixPoint = {}, damage = {}, resultDamages = {}, resultAdditionalDamages = {}", takenDamageFixPoint, damageDto.getDamage(), resultDamages, resultAdditionalDamages);
        return DamageDto.builder()
                .elementType(damageDto.getElementType())
                .moveDamageType(damageDto.getMoveDamageType())
                .resultDamages(resultDamages)
                .resultAdditionalDamages(resultAdditionalDamages)
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
        private ElementType elementType;
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
