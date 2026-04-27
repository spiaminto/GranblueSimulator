package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.DamageValidationException;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final BattleContext battleContext;
    private final DamageCalcLogic damageCalcLogic;
    private final SetStatusLogic setStatusLogic;

    public DamageLogicResult processPartyDamage(Move move) {
        return this.processPartyDamage(move, move.getBaseMove().getDamageRate(), move.getBaseMove().getHitCount());
    }

    /**
     * 아군 행동에 따른 데미지를 계산하고 데미지를 적 hp 에 반영한다
     *
     * @param damageRate 최종 데미지 배율
     * @param hitCount   최종 히트수
     * @return
     */
    public DamageLogicResult processPartyDamage(Move move, double damageRate, int hitCount) {
        Actor mainActor = move.getActor();
        MoveType moveType = move.getType();
        ElementType moveElementType = move.getBaseMove().getElementType();
        int damageConstant = move.getBaseMove().getDamageConstant();
        int normalAttackHitCount = move.getType() == MoveType.NORMAL_ATTACK ? hitCount : 0;
        Actor targetActor = battleContext.getEnemy();

        log.info("[processPartyDamage] start calc party damage mainActorName = {}, targetName = {} \n moveType = {}, move={} ", mainActor.getName(), targetActor.getName(), moveType, move.getBaseMove());
        if (targetActor.isAlreadyDead()) {
            log.warn("[processPartyDamage] 타겟이 이미 사망함, targetName = {} \n target = {} \n move = {}", targetActor.getName(), targetActor, move);
            return DamageLogicResult.builder().build();
        }
        if (hitCount <= 0)
            throw new DamageValidationException("히트수가 0 이하임 mainActor = " + mainActor.getName() + " moveType = " + moveType);

        // 데미지 계산
        GetDamageResult getDamageResult;
        if (damageConstant > 0 || moveElementType == ElementType.PLAIN) {
            // 고정데미지 || 무속성 데미지
            getDamageResult = GetDamageResult.builder()
                    .elementTypes(List.of(moveElementType))
                    .damages(Collections.nCopies(hitCount, damageConstant))
                    .damageType(MoveDamageType.NORMAL)
                    .build();
        } else {
            List<ElementType> elementTypes = determineElementTypes(moveElementType, hitCount);
            ElementType elementType = elementTypes.getFirst(); // CHECK 현재 캐릭터는 랜덤 속성 데미지를 지원하지 않음
            getDamageResult = damageCalcLogic.getPartyDamage(mainActor, targetActor, moveType, elementType, damageRate, hitCount);
        }

        List<Integer> damages = getDamageResult.getDamages();
        List<List<Integer>> additionalDamagesList = getDamageResult.getAdditionalDamages();

        // 적의 무적효과
        Optional<StatusEffect> ineffectiveUntilEffectOptional = getEffectByModifierType(targetActor, StatusModifierType.TAKEN_DAMAGE_INEFFECTIVE_FROM);
        if (ineffectiveUntilEffectOptional.isEmpty()
                || targetActor.getHpRateInt() > ineffectiveUntilEffectOptional.get().getModifierValue(StatusModifierType.TAKEN_DAMAGE_INEFFECTIVE_FROM)) {
            // CHECK 무적효과가 있을때, 데미지 자체는 반환하나 실제 체력에 반영 하지않음
            // 데미지 반영
            int enemyHp = targetActor.getHp();
            for (Integer damage : damages) {
                enemyHp = damage > 0 ? enemyHp - damage : enemyHp;
            }
            for (List<Integer> additionalDamages : additionalDamagesList) {
                for (Integer additionalDamage : additionalDamages) {
                    enemyHp = additionalDamage > 0 ? enemyHp - additionalDamage : enemyHp;
                }
            }
            targetActor.updateHp(Math.max(enemyHp, 0));
        }


        return DamageLogicResult.builder()
                .damages(damages)
                .additionalDamages(additionalDamagesList)
                .attackMultiHitCount(getDamageResult.getAttackMultiHitCount())
                .elementTypes(getDamageResult.getElementTypes())
                .damageTypes(List.of(getDamageResult.getDamageType()))
                .normalAttackCount(normalAttackHitCount)
                .build();
    }

    /**
     * 적의 행동에 따른 데미지를 계산후 타겟 hp 에 반영 <br>
     * 적의 경우 아군과 달리 타겟을 설정한 뒤 모든 타겟에게 데미지가 발생하므로 별도 처리 <br>
     *
     * @param move
     * @param damageRate 최종 데미지 배율
     * @param hitCount   Move.hitCount (일반공격에 한해, normalAttackCount [1 ~ 3])
     * @return DamageLogicResult: List<BattleActor> targetActors 와 동일순서, 1대1 대응하는 데미지결과
     */
    public DamageLogicResult processEnemyDamage(Move move, double damageRate, int hitCount) {
        Actor mainActor = battleContext.getEnemy();
        BaseMove baseMove = move.getBaseMove();
        log.info("[processEnemyDamage] move = {} move.getDamageRate = {} damageRate = {} hitCount = {}", baseMove, baseMove.getDamageRate(), damageRate, hitCount);
        if (hitCount <= 0)
            throw new DamageValidationException("히트수가 0 이하임 mainActor = " + mainActor.getName() + " moveName = " + baseMove.getName() + " hitCount = " + hitCount);

        // 일반공격 횟수 확정 
        final int normalAttackCount = move.getType() == MoveType.NORMAL_ATTACK ? hitCount : 0;
        int multiHitCount = 1;
        // 일반공격 히트수 증가 반영
        if (move.getType() == MoveType.NORMAL_ATTACK) {
            // move.hitCount 반영 (기본 일반공격 히트수) : normalAttackCount 로 넘어오므로, 반영해야함
            int normalAttackMoveHitCount = move.getBaseMove().getHitCount(); // 전체공격이면 보통 1, 랜덤타겟이면 1 ~ 로 설정됨 (최솟값 1)
            // 히트수 증가 상태효과 반영
            int attackMultiHitCount = mainActor.getStatusDetails().getCalcedAttackMultiHitCount();
            multiHitCount = normalAttackMoveHitCount + (attackMultiHitCount - 1); // 랜덤타겟 3히트 일반공격에 히트수 1 증가시 총 4히트함.
            hitCount = normalAttackCount * multiHitCount;
        }

        List<Actor> targetActors = getEnemyAttackTargets(move, hitCount);

        List<ElementType> elementTypes = determineElementTypes(move.getBaseMove().getElementType(), targetActors.size());
        int damageConstant = baseMove.getDamageConstant();

        List<Integer> resultDamages = new ArrayList<>();
        List<List<Integer>> resultAdditionalDamages = new ArrayList<>();
        List<ElementType> damageElementTypes = new ArrayList<>();
        List<MoveDamageType> damageTypes = new ArrayList<>();

        log.info("[DamageLogic] targetActors = {}", targetActors.stream().map(Actor::getName).toList());
        for (int i = 0; i < targetActors.size(); i++) {
            Actor targetActor = targetActors.get(i);
            if (targetActor.isAlreadyDead()) {
                log.warn("[processEnemyDamage] 타겟이 이미 사망함, targetName = {} \n target = {} \n move = {}", targetActor.getName(), targetActor, move);
                return DamageLogicResult.builder().build();
            }

            ElementType elementType = elementTypes.get(i);

            // 데미지 계산
            GetDamageResult getDamageResult;
            boolean isConstantDamage = damageConstant > 0 || elementType == ElementType.PLAIN; // 고정데미지 또는 무속성데미지는 고정
            if (isConstantDamage) {
                // 계산 스킵
                getDamageResult = GetDamageResult.builder()
                        .elementTypes(List.of(elementType))
                        .damages(Collections.nCopies(hitCount, damageConstant))
                        .damageType(MoveDamageType.NORMAL)
                        .build();
            } else {
                getDamageResult = damageCalcLogic.getEnemyDamage(mainActor, targetActor, move.getType(), elementType, damageRate);
            }

            if (getDamageResult.getDamages().size() > 1)
                throw new DamageValidationException("적의 데미지가 1회를 초과하여 발생 moveType " + move.getType() + " size = " + getDamageResult.getDamages().size());

            Integer damage = getDamageResult.getDamages().getFirst();
            List<Integer> additionalDamages = getDamageResult.getAdditionalDamages().isEmpty()
                    ? Collections.emptyList()
                    : getDamageResult.getAdditionalDamages().getFirst();
            ElementType damageElementType = getDamageResult.getElementTypes().getFirst();
            MoveDamageType damageType = getDamageResult.getDamageType();
            int totalDamage = damage + additionalDamages.stream().mapToInt(Integer::intValue).sum();

            // 효과 반영 (고정데미지 제외)
            if (!isConstantDamage) {

                //  효과: 피데미지 무효
                Optional<StatusEffect> takenDamageIneffectiveEffectOptional = getEffectByModifierType(targetActor, StatusModifierType.TAKEN_DAMAGE_INEFFECTIVE);
                if (takenDamageIneffectiveEffectOptional.isPresent()) {
                    StatusEffect takenDamageIneffectiveEffect = takenDamageIneffectiveEffectOptional.get();
                    if (takenDamageIneffectiveEffect.isRefillable()) {
                        // 횟수제면 레벨 감소
                        setStatusLogic.subtractStatusEffectLevel(targetActor, 1, takenDamageIneffectiveEffect);
                    }
                    totalDamage = 0;
                    // 피데미지 무효시 데미지에 반영 및 타입변경
                    damage = 0;
                    damageType = MoveDamageType.INEFFECTIVE;
                    additionalDamages = additionalDamages.stream().map(d -> 0).toList();
                }

                // 효과: 베리어
                if (totalDamage > 0) {
                    Optional<StatusEffect> barrierEffectOptional = getEffectByModifierType(targetActor, StatusModifierType.BARRIER);
                    if (barrierEffectOptional.isPresent()) {
                        int barrier = targetActor.getStatus().getBarrier();

                        if (totalDamage >= barrier) {
                            totalDamage -= barrier;
                            setStatusLogic.removeStatusEffect(targetActor, barrierEffectOptional.get());
                        } else {
                            targetActor.getStatus().updateBarrier(barrier - totalDamage);
                            totalDamage = 0;
                            // 베리어는 데미지 자체는 유지.
                        }
                    }
                }
            }

            // 최종 반영
            int targetHp = targetActor.getHp() - totalDamage;
            targetActor.updateHp(Math.max(targetHp, 0));

            resultDamages.add(damage);
            resultAdditionalDamages.add(additionalDamages);
            damageElementTypes.add(damageElementType);
            damageTypes.add(damageType);
        }

        // 데미지 후처리 (처리후 재루프)
        for (Actor targetActor : targetActors.stream().distinct().toList()) {
            if (targetActor.isNowDead()) {
                // 불사 처리
                StatusUtil.getEffectByModifierType(targetActor, StatusModifierType.IMMORTAL)
                        .ifPresent(immortalEffect -> {
                            targetActor.updateHp(1);
                            setStatusLogic.removeStatusEffect(targetActor, immortalEffect);
                        });
            }
        }

        return DamageLogicResult.builder()
                .damages(resultDamages)
                .additionalDamages(resultAdditionalDamages)
                .attackMultiHitCount(multiHitCount)
                .elementTypes(damageElementTypes)
                .damageTypes(damageTypes)
                .enemyAttackTargets(targetActors)
                .normalAttackCount(normalAttackCount)
                .build();
    }

    /**
     * 보스의 공격 타겟 결정후 반환 (전체공격의 경우 partyMembers 그대로 사용하면 됨), 데미지 발생전에 지정되므로 오버킬 가능. <br>
     * 적의 공격유형은 <br>
     * 전체공격(모든 파티원 대상으로 hitCount 만큼 발생, 적대심 무시) <br>
     * 랜덤타겟공격(hitCount 만큼 데미지 발생) <br>
     * 이 존재
     *
     * @param hitCount 요청 최종 히트수 (데미지 발생 수)
     * @return Unmodifiable List (여기서 정해진 타겟은 수정 X)
     */
    protected List<Actor> getEnemyAttackTargets(Move move, int hitCount) {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        boolean isAllTarget = move.getBaseMove().isAllTarget();
        boolean isConstantDamage = move.getBaseMove().getDamageConstant() > 0; // 고정데미지 : 감싸기, 적대심 효과등 모든 효과 무시 지정타겟

        // 감싸기 효과 적용: 적의 전체공격, 랜덤타겟 공격의 모든 데미지가 감싸기 효과중인 캐릭터에게 발생,
        Optional<Actor> substituteActor = partyMembers.stream()
                .filter(m -> m.getStatusDetails().getCalcedSubstituteAppliedTime() != null)
                .max(Comparator.comparing(m -> m.getStatusDetails().getCalcedSubstituteAppliedTime()));
        if (substituteActor.isPresent() && !isConstantDamage) {
            int count = isAllTarget ? partyMembers.size() * hitCount : hitCount; // 전체공격: 파티원 수 만큼, 단일타겟: 히트수 만큼
            return Collections.nCopies(count, substituteActor.get());
        }

        // 전체공격
        if (isAllTarget) {
            return IntStream.range(0, hitCount).mapToObj(i -> partyMembers).flatMap(Collection::stream).toList();
        }

        // 랜덤타겟공격: 적대심 효과 적용
        final double baseHostilityPoint = 100.0; // 기본 적대심 100
        List<Double> hostilityPoints = isConstantDamage
                ? Collections.nCopies(partyMembers.size(), baseHostilityPoint)
                : partyMembers.stream()
                  .map(m -> baseHostilityPoint + m.getStatusDetails().getCalcedHostilityPoint())
                  .toList();
        double totalHostilityPoint = hostilityPoints.stream().mapToDouble(Double::doubleValue).sum();
        log.info("[getEnemyAttackTargets] hostilityPoints = {}, totalHostilityPoint = {}", hostilityPoints, totalHostilityPoint);

        // 적대심에 따른 타겟 매핑
        return IntStream.range(0, hitCount)
                .mapToObj(i -> {
                    double targetHostility = Math.random() * totalHostilityPoint; //  0 ~ 적대심 합 에서 랜덤
                    double accumulated = 0.0;
                    for (int j = 0; j < partyMembers.size(); j++) {
                        accumulated += hostilityPoints.get(j);
                        if (targetHostility <= accumulated) { // 누적 범위에서 결정
                            return partyMembers.get(j);
                        }
                    }
                    throw new MoveProcessingException("랜덤 타겟 공격 적대심 처리중 문제발생, 적대심: " + hostilityPoints + ", 타겟 값 = " + targetHostility + ", 합계 = " + totalHostilityPoint);
                })
                .toList();
    }

    /**
     * elementType 을 히트수에 맞게 매칭하고, 6속성이 아닌 논리적 속성들을 처리
     *
     * @param hitCount 최종 히트수
     */
    protected List<ElementType> determineElementTypes(ElementType elementType, int hitCount) {
        List<ElementType> elementTypes;
        if (elementType.isElementalType()) { // 6속성
            elementTypes = Collections.nCopies(hitCount, elementType);
        } else {
            elementTypes = switch (elementType) { // 무속성 + 논리속성
                case PLAIN -> Collections.nCopies(hitCount, ElementType.PLAIN);
                case ACTOR -> Collections.nCopies(hitCount, battleContext.getMainActor().getElementType());
                case RANDOM -> IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType()).toList();
                case FIRE_WIND ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.FIRE, ElementType.WIND)).toList();
                case FIRE_LIGHT ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.FIRE, ElementType.LIGHT)).toList();
                case WATER_EARTH ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.EARTH, ElementType.WATER)).toList();
                case WATER_DARK ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.DARK, ElementType.WATER)).toList();
                case LIGHT_DARK ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.DARK, ElementType.LIGHT)).toList();
                case WIND_EARTH ->
                        IntStream.range(0, hitCount).mapToObj(i -> ElementType.getRandomElementType(ElementType.EARTH, ElementType.WIND)).toList();
                default -> throw new DamageValidationException("지원하지 않는 속성, elementType = " + elementType);
            };
        }
        return elementTypes;
    }


}
