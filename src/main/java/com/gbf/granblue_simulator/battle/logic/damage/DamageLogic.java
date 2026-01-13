package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.exception.DamageValidationException;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final BattleContext battleContext;
    private final DamageCalcLogic damageCalcLogic;
    private final SetStatusLogic setStatusLogic;

    public DamageLogicResult processPartyDamage(Move move) {
        return processPartyDamage(battleContext.getEnemy(), move);
    }

    /**
     * Move 의 데미지 계산, Move 의 기본 damageRate 와 hitCount 사용
     *
     * @param targetActor
     * @param move
     * @return
     */
    public DamageLogicResult processPartyDamage(Actor targetActor, Move move) {
        return processPartyDamage(targetActor, move.getType(), move.getElementType(), move.getDamageRate(), move.getHitCount(), move.getDamageConstant());
    }

    /**
     * Move  의 데미지 계산, 가변 damageRate 또는 hitCount 일때 사용
     * Constant 데미지 수정은 지원 안함 process(...move) 이용
     *
     * @param moveType
     * @param elementType
     * @param damageRate
     * @param hitCount
     * @return
     */
    public DamageLogicResult processPartyDamage(MoveType moveType, ElementType elementType, double damageRate, int hitCount) {
        return processPartyDamage(battleContext.getEnemy(), moveType, elementType, damageRate, hitCount, 0);
    }

    /**
     * target 이 적이 아닌 경우 사용 예정 <br>
     *
     * @param targetActor
     * @param moveType
     * @param elementType
     * @param damageRate
     * @param hitCount
     * @return
     */
    public DamageLogicResult processPartyDamage(Actor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount) {
        return processPartyDamage(targetActor, moveType, elementType, damageRate, hitCount, 0);
    }

    /**
     * 아군 행동에 따른 데미지를 계산하고 데미지를 적 hp 에 반영한다
     *
     * @param targetActor
     * @param moveType
     * @param elementType
     * @param damageRate
     * @param hitCount
     * @return
     */
    public DamageLogicResult processPartyDamage(Actor targetActor, MoveType moveType, ElementType elementType, double damageRate, int hitCount, int damageConstant) {
        Actor mainActor = battleContext.getMainActor();
        log.info("[processPartyDamage] start calc party damage mainActorName = {}, targetName = {} \n moveType = {}, move={} ", mainActor.getName(), targetActor.getName(), moveType, mainActor.getMove(moveType));
        if (targetActor.isAlreadyDead())
            throw new DamageValidationException("타겟이 이미 사망함, target = " + targetActor.getName());
        if (hitCount <= 0)
            throw new DamageValidationException("히트수가 0 이하임 mainActor = " + mainActor.getName() + " moveType = " + moveType);

        // 데미지 계산
        GetDamageResult getDamageResult = damageConstant > 0 ?
                GetDamageResult.builder() // 주로 페이탈 체인, 무속성 고정 데미지
                        .elementTypes(List.of(elementType))
                        .damages(Collections.nCopies(hitCount, damageConstant))
                        .damageType(MoveDamageType.NORMAL)
                        .build()
                : damageCalcLogic.getPartyDamage(mainActor, targetActor, moveType, elementType, damageRate, hitCount);

        List<Integer> damages = getDamageResult.getDamages();
        List<List<Integer>> additionalDamagesList = getDamageResult.getAdditionalDamages();

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

        return DamageLogicResult.builder()
                .damages(damages)
                .additionalDamages(additionalDamagesList)
                .attackMultiHitCount(getDamageResult.getAttackMultiHitCount())
                .elementTypes(getDamageResult.getElementTypes())
                .damageTypes(List.of(getDamageResult.getDamageType()))
                .build();
    }

    public DamageLogicResult processEnemyDamage(List<Actor> targetActors, Move move) {
        return processEnemyDamage(targetActors, move, null);
    }

    /**
     * 적의 행동에 따른 데미지를 계산후 타겟 hp 에 반영 <br>
     * 적의 경우 아군과 달리 타겟을 설정한 뒤 모든 타겟에게 데미지가 발생하므로 별도 처리 <br>
     * 적의 공격은 배율변화 또는 히트수 변화 없음 <br>
     *
     * @param targetActors 일반적으로 partyMembers 또는 그의 중복을 포함하여 구성된 targets
     * @param move
     * @return DamageLogicResult: List<BattleActor> targetActors 와 동일순서, 1대1 대응하는 데미지결과
     */
    public DamageLogicResult processEnemyDamage(List<Actor> targetActors, Move move, Double modifiedDamageRate) {
        Actor mainActor = battleContext.getEnemy();
        log.info("[processEnemyDamage] move = {} move.getDamageRate = {} modifiedDamageRate = {}", move, move.getDamageRate(), modifiedDamageRate);

        if (targetActors.isEmpty()) {
            log.warn("[processEnemyDamage] targetActors is empty, move = " + move.getName());
            return DamageLogicResult.builder().build();
        }

        ElementType elementType = move.getElementType();
        double damageRate = modifiedDamageRate == null ? move.getDamageRate() : modifiedDamageRate;
        int damageConstant = move.getDamageConstant();

        List<Integer> resultDamages = new ArrayList<>();
        List<List<Integer>> resultAdditionalDamages = new ArrayList<>();
        List<ElementType> damageElementTypes = new ArrayList<>();
        List<MoveDamageType> damageTypes = new ArrayList<>();

        log.info("[DamageLogic] targetActors = {}", targetActors.stream().map(Actor::getName).toList());
        for (Actor targetActor : targetActors) {
            // 데미지 계산
            GetDamageResult getDamageResult = damageConstant > 0 ?
                    GetDamageResult.builder() // 주로 무속성 고정데미지
                            .elementTypes(List.of(elementType))
                            .damages(List.of(damageConstant))
                            .damageType(MoveDamageType.NORMAL)
                            .build()
                    : damageCalcLogic.getEnemyDamage(mainActor, targetActor, move.getType(), elementType, damageRate);

            if (getDamageResult.getDamages().size() > 1)
                throw new DamageValidationException("적의 데미지가 1회를 초과하여 발생 moveType " + move.getType() + " size = " + getDamageResult.getDamages().size());

            Integer damage = getDamageResult.getDamages().getFirst();
            List<Integer> additionalDamages = getDamageResult.getAdditionalDamages().isEmpty()
                    ? Collections.emptyList()
                    : getDamageResult.getAdditionalDamages().getFirst();
            ElementType damageElementType = getDamageResult.getElementTypes().getFirst();
            MoveDamageType damageType = getDamageResult.getDamageType();

            // 데미지 반영
            int targetHp = targetActor.getHp();

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

        // 데미지 후처리 (처리후 재루프)
        for (Actor targetActor : targetActors) {
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
                .attackMultiHitCount(1) // 적은 난격값 1 로 고정
                .elementTypes(damageElementTypes)
                .damageTypes(damageTypes)
                .build();
    }


}
