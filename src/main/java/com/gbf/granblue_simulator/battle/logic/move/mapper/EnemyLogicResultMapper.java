package com.gbf.granblue_simulator.battle.logic.move.mapper;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnemyLogicResultMapper {

    private final BattleContext battleContext;

    /**
     * 적 로직에서 아무것도 하지않았을때 리턴 (null X)
     *
     * @return
     */
    public MoveLogicResult emptyResult() {
        return MoveLogicResult.builder().move(Move.getTransientMove(battleContext.getEnemy(), MoveType.NONE)).build();
    }

    public MoveLogicResult fromDefaultResult(DefaultMoveLogicResult defaultResult) {
        return toResult(ResultMapperRequest.builder()
                .move(defaultResult.getResultMove())
                .damageLogicResult(defaultResult.getDamageLogicResult())
                .setStatusEffectResult(defaultResult.getSetStatusEffectResult())
                .executeOptions(ResultMapperRequest.ExecuteOptions.empty())
                .build());
    }

    public MoveLogicResult toResult(ResultMapperRequest request) {
        Move move = request.getMove();
        BaseMove baseMove = move.getBaseMove();
        DamageLogicResult damageLogicResult = request.getDamageLogicResult() != null ? request.getDamageLogicResult() : DamageLogicResult.builder().build();
        SetStatusEffectResult setStatusEffectResult = request.getSetStatusEffectResult() != null ? request.getSetStatusEffectResult() : SetStatusEffectResult.emptyResult();
        ResultMapperRequest.ExecuteOptions executeOptions = request.getExecuteOptions() != null ? request.getExecuteOptions() : ResultMapperRequest.ExecuteOptions.empty();

        Actor mainActor = battleContext.getEnemy();
        if (mainActor == null)
            throw new IllegalArgumentException("[map] mainActor 없음 baseMove = " + baseMove.getName());
        Actor enemy = battleContext.getEnemy();
        Actor leaderCharacter = battleContext.getLeaderCharacter();

        // DamageResult
        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0) // 데미지가 0 초과하여 발생해야 히트수로 계산
                        .toList())
                .mapToInt(List::size)
                .sum();
        List<Actor> attackTargets = damageLogicResult.getEnemyAttackTargets();

        // StatusResult
        // 전조 -> 적이 특수기 사용시 자신의 전조가 초기화됨
        Enemy enemyConcrete = (Enemy) enemy;
        OmenResult omenResult = null;
        if (enemyConcrete.getOmen() != null) {
            omenResult = OmenResult.from(enemyConcrete);
        }

        // 스냅샷 : 스냅샷은 일단 프론트 멤버만 저장
        int fatalChainGauge = battleContext.getMember().getFatalChainGauge();
        List<Actor> allActors = battleContext.getCurrentFieldActors();
        Map<Long, MoveLogicResult.Snapshot> snapShots = allActors.stream()
                .collect(Collectors.toMap(
                                Actor::getId,
                                actor -> {
                                    SetStatusEffectResult.Result actorResult = setStatusEffectResult.getResults().getOrDefault(actor.getId(), SetStatusEffectResult.Result.emptyResult());
                                    return MoveLogicResult.Snapshot.builder()
                                            .addedStatusEffects(actorResult.getAddedStatusEffects())
                                            .removedStatusEffects(actorResult.getRemovedStatusEffects())
                                            .levelDownedStatusEffects(actorResult.getLevelDownedStatusEffects())
                                            .heal(actorResult.getHealValue())
                                            .effectDamage(actorResult.getDamageValue())

                                            .actorId(actor.getId())
                                            .currentOrder(actor.getCurrentOrder())
                                            .hp(actor.getHp())
                                            .hpRate(actor.getHpRateInt())
                                            .barrier(actor.getStatus().getBarrier())
                                            .chargeGauge(actor.getChargeGauge())
                                            .maxChargeGauge(actor.getMaxChargeGauge())
                                            .canChargeAttack(actor.canCharacterChargeAttack())
                                            .fatalChainGauge(fatalChainGauge)
                                            .currentStatusEffects(actor.getStatusEffects().stream()
                                                    .map(StatusEffectDto::of)
                                                    .sorted(Comparator.comparing(StatusEffectDto::getDisplayPriority).reversed().thenComparing(StatusEffectDto::getCreatedAt))
                                                    .toList())
                                            .abilityCooldowns(new ArrayList<>(actor.getAbilityCooldowns()))
                                            .abilityUseCounts(new ArrayList<>(actor.getAbilityUseCounts()))
                                            .abilitySealeds(new ArrayList<>(actor.getAbilitySealeds()))
                                            .status(ResultStatusDto.of(actor.getStatus()))
                                            .statusDetails(actor.getStatus().getStatusDetails().clone())
                                            .damageStatusDetails(actor.getStatus().getDamageStatusDetails().clone())
                                            .build();
                                }
                        )
                );

        // 솬석 쿨다운
        List<Integer> summonCooldowns = leaderCharacter == null
                ? new ArrayList<>()
                : leaderCharacter.getSummonCooldowns();

        return MoveLogicResult.builder()

                .mainActor(mainActor)
                .move(move)
                .currentTurn(battleContext.getCurrentTurn())

                .summonCooldowns(summonCooldowns)

                // from damageResult
                .totalHitCount(totalHitCount)
                .damages(damageLogicResult.getDamages())
                .damageElementTypes(damageLogicResult.getElementTypes())
                .damageTypes(damageLogicResult.getDamageTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .normalAttackCount(damageLogicResult.getNormalAttackCount())
                .enemyAttackTargets(attackTargets)

                // 전조
                .omenResult(omenResult)

                // from logic
                .executeChargeAttack(false) // 적은 오의 재발동 없음
                .executeAttackTargetType(null) // 적은 턴 진행없이 일반공격 없음
                .isUnionSummon(false)
                .isEnemyFormChange(executeOptions.isEnemyFormChange())

                .snapshots(snapShots)

                .build();


    }


}
