package com.gbf.granblue_simulator.battle.logic.actor.character;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusDto;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
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
    public ActorLogicResult emptyResult() {
        return ActorLogicResult.builder().move(Move.getTransientMove(MoveType.NONE)).build();
    }

    public ActorLogicResult fromDefaultResult(DefaultActorLogicResult defaultActorLogicResult) {
        return map(defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult(), defaultActorLogicResult.getSetStatusEffectResult(), defaultActorLogicResult.isExecuteChargeAttack(), defaultActorLogicResult.getExecuteAttackTargetType(), false);
    }

    /**
     * 데미지와 스테이터스 모두 발생하는 일반 결과 맵핑
     * 어빌리티, 차지어택, 서포트 어빌리티 <br>
     * 데미지 또는 스테이터스 각 안발생했을 경우 null 입력
     *
     * @param move
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResult(Move move, DamageLogicResult damageLogicResult, SetStatusEffectResult statusResult) {
        return map(move, damageLogicResult, statusResult, false, null, false);
    }

    /**
     * 합체소환 된 소환석 따로 매핑
     *
     * @param move
     * @param damageLogicResult
     * @param statusResult
     * @return
     */
    public ActorLogicResult toUnionSummonResult(Move move, DamageLogicResult damageLogicResult, SetStatusEffectResult statusResult) {
        return map(move, damageLogicResult, statusResult, true, null, true);
    }

    /**
     * 턴 진행 없이 공격 포함하는 결과
     *
     * @param move
     * @param damageLogicResult
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResultWithExecuteAttack(Move move,
                                                      DamageLogicResult damageLogicResult,
                                                      SetStatusEffectResult statusResult,
                                                      StatusEffectTargetType executeAttackTargetType) {
        return map(move, damageLogicResult, statusResult, false, executeAttackTargetType, false);
    }

    protected ActorLogicResult map(Move move,
                                   DamageLogicResult damageLogicResult,
                                   SetStatusEffectResult setStatusEffectResult,
                                   boolean executeChargeAttack,
                                   StatusEffectTargetType executeAttackTargetType,
                                   boolean isUnionSummon) {
        Actor mainActor = battleContext.getMainActor();
        if (mainActor == null) throw new IllegalArgumentException("[map] mainActor 없음 move = " + move.getName());
        Actor enemy = battleContext.getEnemy();
        Actor leaderCharacter = battleContext.getLeaderCharacter();

        // DamageResult
        damageLogicResult = damageLogicResult != null ? damageLogicResult : DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0) // 데미지가 0 초과하여 발생해야 히트수로 계산
                        .toList())
                .mapToInt(List::size)
                .sum();

        // StatusResult
        final SetStatusEffectResult statusResult = setStatusEffectResult != null ? setStatusEffectResult : SetStatusEffectResult.emptyResult();  // 빈 객체도 5칸 고정리스트 필요

        // 전조
        Enemy enemyConcrete = (Enemy) enemy;
        MoveType currentStandbyType = enemyConcrete.getCurrentStandbyType();
        OmenResult omenResult = currentStandbyType != null ? OmenResult.from(enemyConcrete) : null;

        // 스냅샷 : 스냅샷은 일단 프론트 멤버만 저장
        int fatalChainGauge = battleContext.getMember().getFatalChainGauge();
        List<Actor> allActors = battleContext.getCurrentFieldActors();
        Map<Long, ActorLogicResult.Snapshot> snapShots = allActors.stream()
                .collect(Collectors.toMap(
                                Actor::getId,
                                actor -> {
                                    SetStatusEffectResult.Result actorResult = statusResult.getResults().getOrDefault(actor.getId(), SetStatusEffectResult.Result.emptyResult());
                                    return ActorLogicResult.Snapshot.builder()
                                            .addedStatusEffects(actorResult.getAddedStatusEffects())
                                            .removedStatusEffects(actorResult.getRemovedStatusEffects())
                                            .levelDownedStatusEffects(actorResult.getLevelDownedStatusEffects())
                                            .heal(actorResult.getHealValue())
                                            .effectDamage(actorResult.getDamageValue())

                                            .actorId(actor.getId())
                                            .currentOrder(actor.getCurrentOrder())
                                            .hp(actor.getHp())
                                            .hpRate(actor.getHpRate())
                                            .chargeGauge(actor.getChargeGauge())
                                            .maxChargeGauge(actor.getMaxChargeGauge())
                                            .fatalChainGauge(fatalChainGauge)
                                            .currentStatusEffects(actor.getStatusEffects().stream()
                                                    .map(ResultStatusEffectDto::of)
                                                    .sorted(Comparator.comparing(ResultStatusEffectDto::getDisplayPriority).reversed().thenComparing(ResultStatusEffectDto::getCreatedAt))
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
        List<Integer> summonCooldowns = leaderCharacter != null ? leaderCharacter.getSummonCoolDowns() : new ArrayList<>(Collections.nCopies(5, 0));

        return ActorLogicResult.builder()

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

                // 전조
                .omenResult(omenResult)

                // from logic
                .executeChargeAttack(executeChargeAttack)
                .executeAttackTargetType(executeAttackTargetType)
                .isUnionSummon(isUnionSummon)

                .snapshots(snapShots)

                .build();
    }


}
