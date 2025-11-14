package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifier;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.base.types.MoveDamageType;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.domain.battle.actor.prop.Status;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.GetDamageResult;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final Map<MoveType, BaseCap> baseCapMap = new HashMap<>();

    public DamageLogicResult processEnemy(Actor mainActor, List<Actor> targetActors, Move move) {
        return processEnemy(mainActor, targetActors, move, null);
    }

    /**
     * 적의 경우 아군과 달리 타겟을 설정한 뒤 모든 타겟에게 데미지가 발생하므로 별도 처리
     * 적의 공격은 배율변화 또는 히트수 변화 없음
     *
     * @param mainActor    일반적으로 enemy
     * @param targetActors 일반적으로 partyMembers 또는 그의 중복을 포함하여 구성된 targets
     * @param move
     * @return DamageLogicResult: List<BattleActor> targetActors 와 동일순서, 1대1 대응하는 데미지결과
     */
    public DamageLogicResult processEnemy(Actor mainActor, List<Actor> targetActors, Move move, Double modifiedDamageRate) {
        MoveType moveType = move.getType().getParentType();
        if (moveType == MoveType.SUMMON || moveType == MoveType.FATAL_CHAIN || moveType == MoveType.SUPPORT_ABILITY)
            moveType = MoveType.ABILITY;

        log.info("[processEnemy] move = {} move.getDamageRate = {} modifiedDamageRate = {}", move, move.getDamageRate(), modifiedDamageRate);
        ElementType elementType = move.getElementType();
        double damageRate = modifiedDamageRate == null ? move.getDamageRate() : modifiedDamageRate;
        int damageConstant = move.getDamageConstant();

        List<Integer> resultDamages = new ArrayList<>();
        List<List<Integer>> resultAdditionalDamages = new ArrayList<>();
        List<ElementType> damageElementTypes = new ArrayList<>();
        List<MoveDamageType> damageTypes = new ArrayList<>();
        int attackMultiHitCount = 1;
        int index = 0;
        do {
            for (Actor targetActor : targetActors) {
                GetDamageResult getDamageResult = getEnemyDamage(mainActor, targetActor, moveType, elementType, damageRate, damageConstant);
                attackMultiHitCount = getDamageResult.getAttackMultiHitCount();
                if (getDamageResult.getDamages().size() > 1)
                    throw new IllegalStateException("적의 공격 데미지가 1회 초과로 발생하였습니다. size = " + getDamageResult.getDamages().size());

                Integer targetHp = targetActor.getHp();
                Integer damage = getDamageResult.getDamages().getFirst();
                List<Integer> additionalDamages = getDamageResult.getAdditionalDamages().isEmpty() ?
                        Collections.emptyList() :
                        getDamageResult.getAdditionalDamages().getFirst();
                ElementType damageElementType = getDamageResult.getElementTypes().getFirst();
                MoveDamageType damageType = getDamageResult.getDamageType();

                targetHp = damage > 0 ? targetHp - damage : targetHp;
                for (Integer additionalDamage : additionalDamages) {
                    targetHp = damage > 0 ? targetHp - additionalDamage : targetHp;
                }
                targetActor.updateHp(Math.max(targetHp, 0));

                resultDamages.add(damage);
                resultAdditionalDamages.add(additionalDamages);
                damageElementTypes.add(damageElementType);
                damageTypes.add(damageType);
            }
            index++;
        } while (move.isAllTarget() && move.getType().getParentType() == MoveType.ATTACK && move.getHitCount() > index); // 전체공격이고, 일반공격일때 공격횟수만큼 반복

        return DamageLogicResult.builder()
                .damages(resultDamages)
                .additionalDamages(resultAdditionalDamages)
                .attackMultiHitCount(attackMultiHitCount)
                .elementTypes(damageElementTypes)
                .damageTypes(damageTypes)
                .build();
    }

    protected GetDamageResult getEnemyDamage(Actor mainActor, Actor target, MoveType moveType, ElementType moveElementType, double damageRate, int damageConstant) {
        log.info("========== [getEnemyDamage] start calc enemy damage mainActorName = {}, targetName = {}", mainActor.getName(), target.getName());

        // 무속성 고정 데미지 일경우 즉시반환
        if (moveElementType == ElementType.NONE && damageConstant > 0)
            return GetDamageResult.builder()
                    .elementTypes(List.of(moveElementType))
                    .damages(List.of(damageConstant))
                    .additionalDamages(Collections.emptyList())
                    .damageType(MoveDamageType.NORMAL)
                    .build();

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
                .damageType(MoveDamageType.NORMAL)
                .build();
        damageDto = applyElementTypeAdjustment(moveElementType, target.getElementType(), damageDto);
        if (damageDto.getDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);
        damageDto = applyDef(target, damageDto);
        damageDto = applyDamageCap(moveType, mainActorDamageStatus, damageRate, damageDto);

        damageDto = applyAdditionalDamage(mainActor, damageDto);

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(moveType, mainActorDamageStatus, targetDamageStatus, damageDto);
        damageDto = applyDamageFix(targetDamageStatus, damageDto);
        damageDto = applyDamageBlock(targetDamageStatus, damageDto);
        damageDto = applyDamageCut(targetDamageStatus, damageDto);
        damageDto = applyExDamageCap(moveType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, moveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("========== [getEnemyDamage] enemy damage calc finished, mainActorName = {}, targetName = {}, processType = {} damageDto = {}, moveElementType = {}", mainActor.getName(), target.getName(), moveType, damageDto, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .elementTypes(List.of(moveElementType))
                .damageType(damageDto.getDamageType())
                .attackMultiHitCount(attackMultiHitCount)
                .build();
    }


    /**
     * Move 의 데미지 계산, Move 의 기본 damageRate 와 hitCount 사용
     *
     * @param mainActor
     * @param targetActor
     * @param move
     * @return
     */
    public DamageLogicResult process(Actor mainActor, Actor targetActor, Move move) {
        return process(mainActor, targetActor, move.getType(), move.getElementType(), move.getDamageRate(), move.getHitCount(), move.getDamageConstant());
    }

    /**
     * Move  의 데미지 계산, 가변 damageRate 또는 hitCount 일때 사용
     * Constant 데미지 수정은 지원 안함 process(...move) 이용
     *
     * @param mainActor
     * @param targetActor
     * @param moveType
     * @param elementType
     * @param damageRate
     * @param hitCount
     * @return
     */
    public DamageLogicResult process(Actor mainActor, Actor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount) {
        return process(mainActor, targetActor, moveType, elementType, damageRate, hitCount, 0);
    }

    /**
     * Move 의 데미지 계산, 가변 damageRate 또는 hitCount 일때 사용
     *
     * @param mainActor
     * @param targetActor
     * @param moveType
     * @param elementType
     * @param damageRate
     * @param hitCount
     * @return
     */
    public DamageLogicResult process(Actor mainActor, Actor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount, int damageConstant) {
        moveType = moveType.getParentType();
        if (moveType == MoveType.SUMMON || moveType == MoveType.FATAL_CHAIN || moveType == MoveType.SUPPORT_ABILITY)
            moveType = MoveType.ABILITY;

        GetDamageResult getDamageResult = getDamage(mainActor, targetActor, moveType, elementType, damageRate, hitCount, damageConstant);
        List<Integer> damages = getDamageResult.getDamages();
        List<List<Integer>> additionalDamagesList = getDamageResult.getAdditionalDamages();
        List<ElementType> damageElementTypes = getDamageResult.getElementTypes();
        Integer enemyHp = targetActor.getHp();
        for (Integer damage : damages) {
            enemyHp = damage > 0 ? enemyHp - damage : enemyHp;
        }
        for (List<Integer> additionalDamages : additionalDamagesList) {
            for (Integer additionalDamage : additionalDamages) {
                enemyHp = additionalDamage > 0 ? enemyHp - additionalDamage : enemyHp;
            }
        }
        targetActor.updateHp(Math.max(enemyHp, 0));
        boolean isEnemyHpZero = targetActor.getHp() == 0;

        return DamageLogicResult.builder()
                .damages(damages)
                .additionalDamages(additionalDamagesList)
                .attackMultiHitCount(getDamageResult.getAttackMultiHitCount())
                .elementTypes(damageElementTypes)
                .damageTypes(List.of(getDamageResult.getDamageType()))
                .isEnemyHpZero(isEnemyHpZero)
                .build();
    }

    protected GetDamageResult getDamage(Actor mainActor, Actor target, MoveType moveType, ElementType moveElementType, double damageRate, int hitCount, int damageConstant) {
        log.info("========== [getDamage] start calc party damage mainActorName = {}, targetName = {}", mainActor.getName(), target.getName());

        // 고정데미지일 경우 즉시 반환
        if (damageConstant > 0) { // fatal chain, ...
            return GetDamageResult.builder()
                    .elementTypes(List.of(moveElementType))
                    .damages(Collections.nCopies(hitCount, damageConstant))
                    .damageType(MoveDamageType.NORMAL)
                    .build();
        }

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
                .damageType(MoveDamageType.NORMAL)
                .build();
        damageDto = applyElementTypeAdjustment(moveElementType, target.getElementType(), damageDto);
        if (damageDto.getDamageType() != MoveDamageType.DISADVANTAGED) // 약상성이 아닐시 크리티컬 가능
            damageDto = applyCriticalRate(mainActorStatus.getCriticalRate(), mainActorStatus.getCriticalDamageRate(), damageDto);
        damageDto = applyDef(target, damageDto);
        damageDto = applyDamageCap(moveType, mainActorDamageStatus, damageRate, damageDto);

        if (moveType == MoveType.ATTACK) {
            // 통상공격이면 난격, 추격 적용
            int multiHitCount = (int) StatusUtil.getEffectIsMaxValue(mainActor, StatusModifierType.ATTACK_MULTI_HIT);
            attackMultiHitCount = Math.max(multiHitCount, 1);
            damageDto = applyAttackMultiHit(damageDto, attackMultiHitCount);
            damageDto = applyAdditionalDamage(mainActor, damageDto);
        }

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(moveType, mainActorDamageStatus, targetDamageStatus, damageDto);
        damageDto = applyDamageFix(targetDamageStatus, damageDto);
        damageDto = applyDamageBlock(targetDamageStatus, damageDto);
        damageDto = applyDamageCut(targetDamageStatus, damageDto);
        damageDto = applyExDamageCap(moveType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, moveType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("==========[getDamage] party damage calc finished, mainActorName = {}, targetActorName = {}, processType = {} damageDto = {}, attackMultiHitCount = {} elementTypes = {}", mainActor.getName(), target.getName(), moveType, damageDto, attackMultiHitCount, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .attackMultiHitCount(attackMultiHitCount)
                .elementTypes(List.of(moveElementType))
                .damageType(damageDto.getDamageType())
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
        MoveDamageType damageType = isCritical ? MoveDamageType.CRITICAL : damageDto.getDamageType();
        log.info("[applyCritical] damage = {}, resultDamage = {},  criticalRate = {} isCritical = {}, criticalDamageRAte = {}", damage, resultDamage, criticalRate, isCritical, criticalDamageRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .damageType(damageType)
                .build();
    }

    /**
     * 전처리 - 유리속성 보정
     */
    protected DamageDto applyElementTypeAdjustment(ElementType moveElementType, ElementType targetElementType, DamageDto damageDto) {
        double resultDamage = 0;
        double damage = damageDto.getDamage();
        MoveDamageType damageType = damageDto.getDamageType();
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
                .damageType(damageType)
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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageDto.getDamageType())
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
        double totalSupplementalDamage = supplementalDamage + moveSupplementalDamage + takenSupplementalDamageUpPoint - takenSupplementalDamageDownPoint;

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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageType)
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

        MoveDamageType damageType = damageDto.getDamageType() == MoveDamageType.BLOCK ? MoveDamageType.BLOCK_CUT : MoveDamageType.CUT;
        final double damageCutRate = Math.min(takenDamageCutRate, 1.0); // 상한 100% 하한 X
        double resultDamage = damageDto.getDamage() * (1 - damageCutRate);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage * (1 - damageCutRate)).toList();

        log.info("[applyDamageCut] damage = {}, resultDamage = {}, damageCutRate = {}", damageDto.getDamage(), resultDamage, damageCutRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .damageType(damageType)
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
                .damageType(damageDto.getDamageType())
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
                .damageType(damageDto.getDamageType())
                .build();
    }

    /**
     * 주어진 항의 버프수치 합산을 구함
     *
     * @param statusModifiers 항 리스트
     * @return 합산수치, 없으면 0
     */
    protected double getSum(List<StatusModifier> statusModifiers) {
        return statusModifiers == null || statusModifiers.isEmpty() ?
                0 :
                statusModifiers.stream()
                        .map(StatusModifier::getCalcValue) // 레벨제 계산후 반환
                        .mapToDouble(Double::doubleValue)
                        .sum();
    }

    /**
     * 주어진 항의 버프수치 중 최대치를 구함
     * 중복 적용이 불가능한 버프 중 일부는 고유버프의 효과로서 들어가는 경우가 있음. 그경우 중복적용 될수 있으므로 최대값만 구함
     * ex) 야치마의 '알파' 레벨에 있는 추격과 타 캐릭터의 고유버프에 같은 항 추격이 존재할 수 있다.
     *
     * @param statusModifiers
     * @return
     */
    protected double getMax(List<StatusModifier> statusModifiers) {
        return statusModifiers == null || statusModifiers.isEmpty() ?
                0 :
                statusModifiers.stream()
                        .map(StatusModifier::getCalcValue)
                        .mapToDouble(Double::doubleValue)
                        .max().orElse(0);
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

    @Getter
    @Builder
    @ToString
    protected static class DamageDto {
        private MoveDamageType damageType;
        private double damage;
        @Builder.Default
        private List<Double> additionalDamages = new ArrayList<>();

        private List<Integer> resultDamages;
        private List<List<Integer>> resultAdditionalDamages;
    }
}
