package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ActorLogicResultMapper {
    public ActorLogicResult toResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        return toResult(mainActor, enemy, partyMembers, move, null);
    }

    public ActorLogicResult toResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult) {
        return toResult(mainActor, enemy, partyMembers, move, damageLogicResult, NextMoveRequest.of(false, null, null));
    }

    public ActorLogicResult toResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, NextMoveRequest nextMoveRequest) {
        if (damageLogicResult == null) damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        if (nextMoveRequest == null) nextMoveRequest = NextMoveRequest.of(false, null, null); // 후행동이 없을경우 기본객체 생성
        int hitCount = damageLogicResult.getDamages().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream().mapToInt(List::size).sum();
        // 오의게이지
        List<Integer> chargeGauges = partyMembers.stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).map(BattleActor::getChargeGauge).toList();
        // 차지턴
        Integer enemyChargeGauge = enemy.getChargeGauge();
        return ActorLogicResult.builder()
                .mainBattleActorId(mainActor.getId())

                .moveType(move.getType())
                .statusList(move.getStatuses())
                .chargeGauges(chargeGauges)
                .enemyChargeGauge(enemyChargeGauge)

                .totalHitCount(totalHitCount)
                .damages(damageLogicResult.getDamages())
                .additionalDamages(damageLogicResult.getAdditionalDamages())

                .hasNextMove(nextMoveRequest.hasNextMove())
                .nextMoveType(nextMoveRequest.getNextMoveType())
                .nextMoveTarget(nextMoveRequest.getNextMoveTarget())
                .build();
    }



}
