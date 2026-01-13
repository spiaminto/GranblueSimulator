package com.gbf.granblue_simulator.battle.logic.actor.enemy;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EnemyLogicResultMapper {

    private final BattleContext battleContext;

    public EnemyLogicResultMapper(BattleContext battleContext) {
        this.battleContext = battleContext;
    }

    /**
     * 적 로직에서 아무것도 하지않았을때 리턴 (null X)
     *
     * @return
     */
    public ActorLogicResult emptyResult() {
        return ActorLogicResult.builder().move(Move.getTransientMove(MoveType.NONE)).build();
    }

    public ActorLogicResult fromDefaultResult(DefaultActorLogicResult defaultActorLogicResult) {
        return map(defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult(), defaultActorLogicResult.getEnemyAttackTargets(), defaultActorLogicResult.getSetStatusEffectResult(), defaultActorLogicResult.getResultOmen());
    }

    /**
     * 적의 경우 무브만 필요한때 사용
     * break
     *
     * @param move
     * @return
     */
    public ActorLogicResult toResult(Move move) {
        return map(move, null, null, null, null);
    }

    /**
     * 적의 Omen 결과를 같이 맵핑해야할때 사용
     * STANDBY 시작, 적의 피격으로인한 STANDBY 갱신(전조 갱신), BREAK 때 사용
     *
     * @param move
     * @param omen
     * @return
     */
    public ActorLogicResult toResultWithOmen(Move move, OmenResult omen) {
        return map(move, null, null, null, omen);
    }

    /**
     * 상태효과만 발생하는 결과 매핑
     *
     * @param move
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResult(Move move, SetStatusEffectResult statusResult) {
        return map(move, null, null, statusResult, null);
    }

    /**
     * 데미지와 스테이터스가 발생하는 기본결과 맵핑
     *
     * @param move
     * @return
     */
    public ActorLogicResult toResult(Move move, DamageLogicResult damageLogicResult, List<Actor> attackTargets, SetStatusEffectResult statusResult) {
        return map(move, damageLogicResult, attackTargets, statusResult, null);
    }

    protected ActorLogicResult map(Move move, DamageLogicResult damageLogicResult, List<Actor> attackTargets, SetStatusEffectResult setStatusEffectResult, OmenResult omen) {
        Actor mainActor = battleContext.getEnemy();
        if (mainActor == null) throw new IllegalArgumentException("[map] mainActor 없음 move = " + move.getName());
        Actor enemy = battleContext.getEnemy();
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        attackTargets = attackTargets != null ? attackTargets : Collections.emptyList();

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
                .damageElementTypes(damageLogicResult.getElementTypes())
                .damageTypes(damageLogicResult.getDamageTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())

                // 전조
                .omenResult(omenResult)

                // from logic
                .executeChargeAttack(false) // 적은 오의 재발동 없음
                .executeAttackTargetType(null) // 적은 턴 진행없이 일반공격 없음
                .enemyAttackTargets(attackTargets)
                .isUnionSummon(false)

                .snapshots(snapShots)

                .build();


    }


}
