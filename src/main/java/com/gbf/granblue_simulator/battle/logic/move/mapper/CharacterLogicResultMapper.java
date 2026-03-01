package com.gbf.granblue_simulator.battle.logic.move.mapper;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CharacterLogicResultMapper {

    private final BattleContext battleContext;

    /**
     * 캐릭터 로직에서 아무것도 발생하지 않았을때 리턴 (null X)
     *
     * @return
     */
    public MoveLogicResult emptyResult() {
        return MoveLogicResult.builder().move(Move.getTransientMove(null, MoveType.NONE)).build();
    }

    public MoveLogicResult fromDefaultResult(DefaultMoveLogicResult defaultResult) {
        return toResult(ResultMapperRequest.builder()
                .move(defaultResult.getResultMove())
                .damageLogicResult(defaultResult.getDamageLogicResult())
                .setStatusEffectResult(defaultResult.getSetStatusEffectResult())
                .executeOptions(ResultMapperRequest.ExecuteOptions.builder()
                        .executeChargeAttack(defaultResult.isExecuteChargeAttack())
                        .executeAttackTargetType(defaultResult.getExecuteAttackTargetType())
                        .build())
                .build());
    }

    public MoveLogicResult toResult(ResultMapperRequest request) {
        Move move = request.getMove();
        DamageLogicResult damageLogicResult = request.getDamageLogicResult() != null ? request.getDamageLogicResult() : DamageLogicResult.builder().build();
        SetStatusEffectResult setStatusEffectResult = request.getSetStatusEffectResult() != null ? request.getSetStatusEffectResult() : SetStatusEffectResult.emptyResult();
        ResultMapperRequest.ExecuteOptions executeOptions = request.getExecuteOptions() != null ? request.getExecuteOptions() : ResultMapperRequest.ExecuteOptions.empty();
        Actor mainActor = battleContext.getMainActor();
        if (mainActor == null)
            throw new IllegalArgumentException("[map] mainActor 없음 move = " + move.getBaseMove().getName());
        Actor enemy = battleContext.getEnemy();
        Actor leaderCharacter = battleContext.getLeaderCharacter();

        // ExecuteOptions (nonnull)
        boolean executeChargeAttack = executeOptions.isExecuteChargeAttack();
        StatusEffectTargetType executeAttackTargetType = executeOptions.getExecuteAttackTargetType();
        boolean isUnionSummon = executeOptions.isUnionSummon();


        // DamageResult
        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0) // 데미지가 0 초과하여 발생해야 히트수로 계산
                        .toList())
                .mapToInt(List::size)
                .sum();

        // 전조 -> CharacterDefaultMoveLogic.processMove() 처리시 오버라이드 됨
        Enemy enemyConcrete = (Enemy) enemy;
        OmenResult omenResult = null;
        if (enemyConcrete.getOmen() != null) {
            omenResult = OmenResult.from(enemyConcrete);
        }

        // StatusResult
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
                .damageTypes(damageLogicResult.getDamageTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .damageElementTypes(damageLogicResult.getElementTypes())
                .normalAttackCount(damageLogicResult.getNormalAttackCount())

                // 전조
                .omenResult(omenResult)

                // from logic
                .executeChargeAttack(executeChargeAttack)
                .executeAttackTargetType(executeAttackTargetType)
                .isUnionSummon(isUnionSummon)

                .snapshots(snapShots)

                .forMemberAbilityInfo(request.getForMemberAbilityInfo())

                .build();
    }


}
