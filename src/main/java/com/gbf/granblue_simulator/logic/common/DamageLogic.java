package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
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
import java.util.stream.Stream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getStatusEffectMap;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final Map<ProcessType, BaseCap> baseCapMap = new HashMap<>();

    public DamageLogicResult processEnemy(BattleActor mainActor, List<BattleActor> targetActors, Move move) {
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
    public DamageLogicResult processEnemy(BattleActor mainActor, List<BattleActor> targetActors, Move move, Double modifiedDamageRate) {
        ProcessType processType = determineProcessType(move.getType());

        log.info("[processEnemy] move = {} move.getDamageRate = {} modifiedDamageRate = {}", move, move.getDamageRate(), modifiedDamageRate);
        ElementType elementType = move.getElementType();
        double damageRate = modifiedDamageRate == null ? move.getDamageRate() : modifiedDamageRate;
        int damageConstant = move.getDamageConstant();

        List<Integer> resultDamages = new ArrayList<>();
        List<List<Integer>> resultAdditionalDamages = new ArrayList<>();
        List<ElementType> damageElementTypes = new ArrayList<>();
        int attackMultiHitCount = 1;
        int index = 0;
        do {
            for (BattleActor targetActor : targetActors) {
                GetDamageResult getDamageResult = getEnemyDamage(mainActor, targetActor, processType, elementType, damageRate, damageConstant);
                attackMultiHitCount = getDamageResult.getAttackMultiHitCount();
                if (getDamageResult.getDamages().size() > 1)
                    throw new IllegalStateException("적의 공격 데미지가 1회 초과로 발생하였습니다. size = " + getDamageResult.getDamages().size());

                Integer targetHp = targetActor.getHp();
                Integer damage = getDamageResult.getDamages().getFirst();
                List<Integer> additionalDamages = getDamageResult.getAdditionalDamages().isEmpty() ?
                        Collections.emptyList() :
                        getDamageResult.getAdditionalDamages().getFirst();
                ElementType damageElementType = getDamageResult.getElementTypes().getFirst();

                targetHp = damage > 0 ? targetHp - damage : targetHp;
                for (Integer additionalDamage : additionalDamages) {
                    targetHp = damage > 0 ? targetHp - additionalDamage : targetHp;
                }
                targetActor.updateHp(Math.max(targetHp, 0));

                resultDamages.add(damage);
                resultAdditionalDamages.add(additionalDamages);
                damageElementTypes.add(damageElementType);
            }
            index++;
        } while (move.isAllTarget() && move.getType().getParentType() == MoveType.ATTACK && move.getHitCount() > index); // 전체공격이고, 일반공격일때 공격횟수만큼 반복

        return DamageLogicResult.builder()
                .damages(resultDamages)
                .additionalDamages(resultAdditionalDamages)
                .elementTypes(damageElementTypes)
                .attackMultiHitCount(attackMultiHitCount)
                .build();
    }

    /**
     * ProcessType 결정
     *
     * @param moveType
     * @return
     */
    private ProcessType determineProcessType(MoveType moveType) {
        ProcessType processType = null;
        MoveType parentType = moveType.getParentType();
        switch (parentType) {
            case ABILITY, SUPPORT_ABILITY -> processType = ProcessType.ABILITY;
            case ATTACK -> processType = ProcessType.ATTACK;
            case CHARGE_ATTACK -> processType = ProcessType.CHARGE_ATTACK;
            case SUMMON -> processType = ProcessType.SUMMON;
            case FATAL_CHAIN -> processType = ProcessType.FATAL_CHAIN;
            default ->
                    throw new IllegalArgumentException("[determineProcessType] MoveType = " + moveType + " is not supported");
        }
        return processType;
    }

    protected GetDamageResult getEnemyDamage(BattleActor mainActor, BattleActor target, ProcessType processType, ElementType moveElementType, double damageRate, int damageConstant) {
        log.info("========== [getEnemyDamage] start calc enemy damage mainActorName = {}, targetName = {}", mainActor.getName(), target.getName());

        // 무속성 고정 데미지 일경우 즉시반환
        if (moveElementType == ElementType.NONE && damageConstant > 0)
            return GetDamageResult.builder()
                    .elementTypes(List.of(moveElementType))
                    .damages(List.of(damageConstant))
                    .additionalDamages(Collections.emptyList())
                    .build();

        int hitCount = 1; // 적의 공격은 1회 1히트가 원칙
        int attackMultiHitCount = 1; // 현재 적은 난격효과를 적용하지 않음
        Map<StatusEffectType, List<StatusEffect>> targetStatusMap = getStatusEffectMap(target);
        Map<StatusEffectType, List<StatusEffect>> mainActorStatusMap = getStatusEffectMap(mainActor);

        // 전처리
        double damage = atkToDamage(mainActor.getAtk(), damageRate);
        damage = applyElementTypeAdjustment(moveElementType, target.getElementType(), damage);
        damage = applyCriticalRate(mainActor.getCriticalRate(), mainActor.getCriticalDamageRate(), damage);
        damage = applyDef(target, damage);
        damage = applyDamageCap(processType, mainActorStatusMap, damageRate, damage);

        List<Double> additionalDamages = applyAdditionalDamage(mainActorStatusMap, damage);

        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .build();

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(processType, mainActorStatusMap, targetStatusMap, damageDto);
        damageDto = applyDamageFix(targetStatusMap, damageDto);
        damageDto = applyDamageCut(targetStatusMap, damageDto);
        damageDto = applyExDamageCap(processType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, processType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("========== [getEnemyDamage] enemy damage calc finished, mainActorName = {}, targetName = {}, processType = {} damageDto = {}, moveElementType = {}", mainActor.getName(), target.getName(), processType, damageDto, moveElementType);
        return GetDamageResult.builder()
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .elementTypes(List.of(moveElementType))
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
    public DamageLogicResult process(BattleActor mainActor, BattleActor targetActor, Move move) {
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
    public DamageLogicResult process(BattleActor mainActor, BattleActor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount) {
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
    public DamageLogicResult process(BattleActor mainActor, BattleActor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount, int damageConstant) {
        ProcessType processType = determineProcessType(moveType);
        GetDamageResult getDamageResult = getDamage(mainActor, targetActor, processType, elementType, damageRate, hitCount, damageConstant);
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
                .elementTypes(damageElementTypes)
                .damages(damages)
                .additionalDamages(additionalDamagesList)
                .attackMultiHitCount(getDamageResult.getAttackMultiHitCount())
                .isEnemyHpZero(isEnemyHpZero)
                .build();
    }

    protected GetDamageResult getDamage(BattleActor mainActor, BattleActor target, ProcessType processType, ElementType moveElementType, double damageRate, int hitCount, int damageConstant) {
        log.info("========== [getDamage] start calc party damage mainActorName = {}, targetName = {}", mainActor.getName(), target.getName());

        // 고정데미지일 경우 즉시 반환
        if (damageConstant > 0) { // fatal chain, ...
            return GetDamageResult.builder()
                    .elementTypes(List.of(moveElementType))
                    .damages(Collections.nCopies(hitCount, damageConstant))
                    .build();
        }

        Map<StatusEffectType, List<StatusEffect>> targetStatusMap = getStatusEffectMap(target);
        Map<StatusEffectType, List<StatusEffect>> mainActorStatusMap = getStatusEffectMap(mainActor);
        int attackMultiHitCount = 1; // 난격 효과시 통상공격 갯수 (효과 있으면 2부터 시작)
        List<Double> additionalDamages = new ArrayList<>(); // 추격

        // 전처리
        double damage = atkToDamage(mainActor.getAtk(), damageRate);
        damage = applyElementTypeAdjustment(moveElementType, target.getElementType(), damage);
        damage = applyCriticalRate(mainActor.getCriticalRate(), mainActor.getCriticalDamageRate(), damage);
        damage = applyDef(target, damage);
        damage = applyDamageCap(processType, mainActorStatusMap, damageRate, damage);

        if (processType == ProcessType.ATTACK) {
            // 통상공격이면 난격, 추격 적용
            attackMultiHitCount = Math.max((int) getMax(mainActorStatusMap.get(StatusEffectType.ATTACK_MULTI_HIT)), 1);
            damage = applyAttackMultiHit(damage, attackMultiHitCount);
            additionalDamages = applyAdditionalDamage(mainActorStatusMap, damage);
        }

        DamageDto damageDto = DamageDto.builder()
                .damage(damage)
                .additionalDamages(additionalDamages)
                .build();

        // 후처리
        damageDto = applyAmplifyAndSupplementalDamage(processType, mainActorStatusMap, targetStatusMap, damageDto);
        damageDto = applyDamageFix(targetStatusMap, damageDto);
        damageDto = applyDamageCut(targetStatusMap, damageDto);
        damageDto = applyExDamageCap(processType, damageDto, hitCount);
        // 최종처리
        double accuracyRate = getAccuracyRate(mainActor, target, processType);
        damageDto = applyHitCountAndRandom(damageDto, hitCount, attackMultiHitCount, accuracyRate);

        log.info("==========[getDamage] party damage calc finished, mainActorName = {}, targetActorName = {}, processType = {} damageDto = {}, attackMultiHitCount = {} elementTypes = {}", mainActor.getName(), target.getName(), processType, damageDto, attackMultiHitCount, moveElementType);
        return GetDamageResult.builder()
                .elementTypes(List.of(moveElementType))
                .damages(damageDto.getResultDamages())
                .additionalDamages(damageDto.getResultAdditionalDamages())
                .attackMultiHitCount(attackMultiHitCount)
                .build();
    }

    /**
     * 전처리 - 공격력을 배율에 따라 데미지로 변환, 공격력 전처리와 데미지 처리를 나누는 기준
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
    protected double applyCriticalRate(double criticalRate, double criticalDamageRate, double damage) {
        double resultDamage = damage * (Math.random() < criticalRate ? 1 + criticalDamageRate : 1);
        log.info("[applyCritical] damage = {}, resultDamage = {},  criticalRate = {} criticalDamageRAte = {}", damage, resultDamage, criticalRate, criticalDamageRate);
        return resultDamage;
    }

    /**
     * 전처리 - 유리속성 보정
     */
    protected double applyElementTypeAdjustment(ElementType moveElementType, ElementType targetElementType, double damage) {
        double resultDamage = 0;
        if (moveElementType.isAdvantageTo(targetElementType)) {
            resultDamage = damage * 1.5;
        } else if (moveElementType.isDisadvantageTo(targetElementType)) {
            resultDamage = damage * 0.75;
        } else {
            resultDamage = damage; // 무상성시 1배율
        }
        log.info("[applyElementTypeAdjustment] damage = {}, resultDamage = {}, moveElementType = {}, targetElementType = {}", damage, resultDamage, moveElementType, targetElementType);
        return resultDamage;
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
        targetDef = target.isGuardOn() ? targetDef * 10 : targetDef;
        double resultDamage = damage / targetDef;
        log.info("[applyDef] damage = {}, resultDamage = {}, targetDef = {}, isGuardOn = {}", damage, resultDamage, targetDef, target.isGuardOn());
        return resultDamage;
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
        double resultDamage = 0;

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
            resultDamage = (damageSoftCap + (damage - damageSoftCap) * baseCap.getSoftCapApplyRate());
            if (resultDamage > damageHardCap) {
                resultDamage = (damageHardCap + (resultDamage - damageHardCap) * baseCap.getHardCapApplyRate());
            }
        } else {
            resultDamage = damage;
        }

        log.info("[applyDamageCap] damage = {}, resultDamage = {}, damageCapRate = {}, moveDamageCapRate = {}", damage, resultDamage, damageCapRate, moveDamageCapRate);
        return resultDamage;
    }

    /**
     * 난격 적용 (난격 효과에 따른 데미지 감소 적용. 히트수는 히트수 적용에서 적용함)
     *
     * @param damage
     * @return
     */
    protected double applyAttackMultiHit(double damage, int attackMultiHitCount) {
        double resultDamage = attackMultiHitCount > 1 ? damage / attackMultiHitCount : damage;
        log.info("[applyAttackMultiHit] damage = {}, resultDamage = {}, attackMultiHitCount = {}", damage, resultDamage, attackMultiHitCount);
        return resultDamage;
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
        double additionalARate = Math.min(getMax(additionalDamageA), 1.0); // 어빌리티항
        List<StatusEffect> additionalDamageS = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_S);
        double additionalSRate = Math.min(getMax(additionalDamageS), 1.0); // 서포트항
        List<StatusEffect> additionalDamageC = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_C);
        double additionalCRate = Math.min(getMax(additionalDamageC), 1.0); // 오의항
        List<StatusEffect> additionalDamageW = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_W);
        double additionalWRate = Math.min(getMax(additionalDamageW), 1.0); // 무기항
        List<StatusEffect> additionalDamageU = statusEffects.get(StatusEffectType.ADDITIONAL_DAMAGE_U);
        double additionalURate = Math.min(getMax(additionalDamageU), 1.0); // 별항

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
        double resultDamage = 0;
        List<Double> additionalDamages = damageDto.getAdditionalDamages();

        if (type == ProcessType.ATTACK) {
            //통상공격의 경우 공격력 업 적용 후 공격력 상승 적용
            resultDamage = damage * (1 + amplifyDamageRate) + supplementalDamage;
            additionalDamages = additionalDamages.stream().map(additionalDamage -> additionalDamage * (1 + amplifyDamageRate) + supplementalDamage).toList();
        } else {
            // 어빌리티와 오의의 경우 공격력 상승 적용 후 공격력 업 적용
            resultDamage = (damage + supplementalDamage) * (1 + amplifyDamageRate);
            // 추격 없음
        }

        // 요다메에 의한 데미지가 음수일경우 0으로 보정
        resultDamage = Math.max(resultDamage, 0);
        additionalDamages = additionalDamages.stream().map(additionalDamage -> Math.max(additionalDamage, 0)).toList();

        log.info("[applyAmplifyAndSupplementalDamage] damage = {}, resultDamage = {}, supplementalDamage = {}, amplifyDamageRate = {}", damage, resultDamage, supplementalDamage, amplifyDamageRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(additionalDamages)
                .build();
    }

    /**
     * 후처리 - 받는 데미지 고정 적용
     *
     * @param statusEffects
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageFix(Map<StatusEffectType, List<StatusEffect>> statusEffects, DamageDto damageDto) {
        List<StatusEffect> takenDamageFixEffects = statusEffects.get(StatusEffectType.TAKEN_DAMAGE_FIX);
        if (takenDamageFixEffects == null || takenDamageFixEffects.isEmpty())
            return damageDto; // 배율계산이 아니라 대입이기 때문에 효과없을시 바로 반환
        final double takenDamageFixPoint = Math.max(getSum(takenDamageFixEffects), 0); // 상한 x, 하한 0

        double resultDamage = Math.min(damageDto.getDamage(), takenDamageFixPoint);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> Math.min(additionalDamage, takenDamageFixPoint)).toList();

        log.info("[applyDamageFix] damage = {}, resultDamage = {}, damageFixPoint = {}", damageDto.getDamage(), resultDamage, takenDamageFixPoint);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .build();
    }

    /**
     * 후처리 - 데미지에 데미지컷 적용
     *
     * @param statusEffects
     * @param damageDto
     * @return
     */
    protected DamageDto applyDamageCut(Map<StatusEffectType, List<StatusEffect>> statusEffects, DamageDto damageDto) {
        List<StatusEffect> damageCutEffects = statusEffects.get(StatusEffectType.TAKEN_DAMAGE_CUT);
        final double damageCutRate = Math.min(getSum(damageCutEffects), 1.0); // 상한 100% 하한 X

        double resultDamage = damageDto.getDamage() * (1 - damageCutRate);
        List<Double> resultAdditionalDamages = damageDto.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage * (1 - damageCutRate)).toList();

        log.info("[applyDamageCut] damage = {}, resultDamage = {}, damageCutRate = {}", damageDto.getDamage(), resultDamage, damageCutRate);
        return DamageDto.builder()
                .damage(resultDamage)
                .additionalDamages(resultAdditionalDamages)
                .build();
    }

    /**
     * 후처리 - 대 데미지 감쇠 적용
     * 제일 마지막에 적용
     *
     * @return
     */
    protected DamageDto applyExDamageCap(ProcessType type, DamageDto damageDto, int hitCount) {
        double damage = damageDto.getDamage();
        double resultDamage = 0;
        List<Double> additionalDamages = damageDto.getAdditionalDamages();
        if (hitCount < 1)
            throw new IllegalArgumentException("[applyExDamageCap], hitCount less than 1 , hitCount = " + hitCount);
        BaseCap baseCap = baseCapMap.get(type);
        Integer exDamageCap = baseCap.getExDamageCap();
        double exDamageCapApplyRate = baseCap.getExDamageCapApplyRate();

        if (type == ProcessType.ABILITY) {
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

        log.info("[applyExDamageCap] damage = {}, hitCount = {}, resultDamage = {}, type = {}", damage, hitCount, resultDamage, type);
        return DamageDto.builder()
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
    protected double getAccuracyRate(BattleActor mainActor, BattleActor targetActor, ProcessType processType) {
        Double mainActorAccuracyRate = mainActor.getAccuracyRate(); // 기본값 1, 상한 하한 없음
        mainActorAccuracyRate = processType == ProcessType.CHARGE_ATTACK && !mainActor.isEnemy() ?
                mainActorAccuracyRate + 1 : mainActorAccuracyRate; // 아군 오의의 경우 기본 명중률을 2로
        Double targetActorDodgeRate = targetActor.getDodgeRate(); // 기본값 0.01 (또는 0.0), 상한 하한 없음
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

    /**
     * 주어진 항의 버프수치 중 최대치를 구함
     * 중복 적용이 불가능한 버프 중 일부는 고유버프의 효과로서 들어가는 경우가 있음. 그경우 중복적용 될수 있으므로 최대값만 구함
     * ex) 야치마의 '알파' 레벨에 있는 추격과 타 캐릭터의 고유버프에 같은 항 추격이 존재할 수 있다.
     *
     * @param statusEffects
     * @return
     */
    protected double getMax(List<StatusEffect> statusEffects) {
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.stream()
                        .map(StatusEffect::getCalcValue)
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

        private final StatusEffectType moveDamageCapRateCapEffectType; // 데미지상한 이펙트타입
        private final StatusEffectType moveSupplementalDamageCapEffectType; // 데미지상승 이펙트타입
        private final StatusEffectType moveAmplifyDamageRateCapEffectType; // 데미지업 이펙트타입

        @Builder.Default
        private final Double damageCapRateCap = 1.0; // 데미지 상한 상한 100% (어쌔신 고려)
        private final Double moveDamageCapRateCap; // [행동별] 데미지 상한 각
        @Builder.Default
        private final Integer supplementalDamageCap = 50000; // 공격 데미지 상승 상한 50000
        private final Integer moveSupplementalDamageCap; // [행동별] 공격 데미지 상승 상한 각
        @Builder.Default
        private final Double amplifyDamageRateCap = 0.5; // 공격 데미지 업 상한 50%
        private final Double moveAmplifyDamageRateCap; // [행동별] 공격 데미지 업 상한 각

        private final StatusEffectType moveTakenAmplifyDamageUpType;
        private final StatusEffectType moveTakenAmplifyDamageDownType;

        private final Integer takenSupplementalDamageCap = 10000; // 피격 데미지 상승 상한
        private final Double takenAmplifyDamageRateCap = 0.5; // 피격 데미지 업 상한 50%
        private final Double moveTakenAmplifyDamageRateCap = 1.0; // 행동별 피격 데미지 업 상한 100%
    }

    @PostConstruct
    protected void initBaseCapMap() {
        baseCapMap.put(ProcessType.ATTACK, BaseCap.builder()
                .baseSoftCap(40000)
                .baseHardCap(60000)
                .exDamageCap(660000)
                .moveDamageCapRateCapEffectType(StatusEffectType.ATTACK_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.5)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_ATTACK_DAMAGE_UP)
                .moveSupplementalDamageCap(20000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_ATTACK_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.3)
                .moveTakenAmplifyDamageUpType(StatusEffectType.TAKEN_ATTACK_AMPLIFY_DAMAGE_UP)
                .moveTakenAmplifyDamageDownType(StatusEffectType.TAKEN_ATTACK_AMPLIFY_DAMAGE_DOWN)
                .build());
        baseCapMap.put(ProcessType.ABILITY, BaseCap.builder()
                .baseSoftCap(30000)
                .baseHardCap(50000)
                .exDamageCap(1300000)
                .moveDamageCapRateCapEffectType(StatusEffectType.ABILITY_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.5)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_ABILITY_DAMAGE_UP)
                .moveSupplementalDamageCap(20000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_ABILITY_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.3)
                .moveTakenAmplifyDamageUpType(StatusEffectType.TAKEN_ABILITY_AMPLIFY_DAMAGE_UP)
                .moveTakenAmplifyDamageDownType(StatusEffectType.TAKEN_ABILITY_AMPLIFY_DAMAGE_DOWN)
                .build());
        baseCapMap.put(ProcessType.CHARGE_ATTACK, BaseCap.builder()
                .baseSoftCap(370000)
                .baseHardCap(480000)
                .exDamageCap(1300000)
                .moveDamageCapRateCapEffectType(StatusEffectType.CHARGE_ATTACK_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.75)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_CHARGE_ATTACK_DAMAGE_UP)
                .moveSupplementalDamageCap(50000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_CHARGE_ATTACK_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.5)
                .moveTakenAmplifyDamageUpType(StatusEffectType.TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_UP)
                .moveTakenAmplifyDamageDownType(StatusEffectType.TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_DOWN)
                .build());
        baseCapMap.put(ProcessType.SUMMON, BaseCap.builder() // 기본적으로 소환석은 어빌리티의 공식을 따름
                .baseSoftCap(30000)
                .baseHardCap(50000)
                .exDamageCap(1300000)
                .moveDamageCapRateCapEffectType(StatusEffectType.ABILITY_DAMAGE_CAP_UP)
                .moveDamageCapRateCap(0.5)
                .moveSupplementalDamageCapEffectType(StatusEffectType.SUPPLEMENTAL_ABILITY_DAMAGE_UP)
                .moveSupplementalDamageCap(20000)
                .moveAmplifyDamageRateCapEffectType(StatusEffectType.AMPLIFY_ABILITY_DAMAGE_UP)
                .moveAmplifyDamageRateCap(0.3)
                .build());
    }

    @Getter
    @Builder
    @ToString
    protected static class DamageDto {
        private double damage;
        @Builder.Default
        private List<Double> additionalDamages = new ArrayList<>();

        private List<Integer> resultDamages;
        private List<List<Integer>> resultAdditionalDamages;

        private ProcessType type;
    }

    protected enum ProcessType {
        ATTACK,
        ABILITY,
        CHARGE_ATTACK,
        SUMMON,
        FATAL_CHAIN;
    }


}
