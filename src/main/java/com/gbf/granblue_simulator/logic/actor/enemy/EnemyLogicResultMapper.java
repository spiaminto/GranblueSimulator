package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class EnemyLogicResultMapper {

    public ActorLogicResult toResultWithOmenValue(BattleActor mainActor, List<BattleActor> targetActors, Move move, Integer omenValue) {
        return toResult(mainActor, targetActors, move, null, null, NextMoveRequest.of(false, null, null), omenValue);
    }

    public ActorLogicResult toResultWithStatus(BattleActor mainActor, List<BattleActor> targetActors, Move move, List<Status> statuses) {
        return toResult(mainActor, targetActors, move, statuses, null, NextMoveRequest.of(false, null, null), null);
    }

    public ActorLogicResult toResult(BattleActor mainActor, List<BattleActor> targetActors, Move move) {
        return toResult(mainActor, targetActors, move, null);
    }

    public ActorLogicResult toResult(BattleActor mainActor, List<BattleActor> targetActors, Move move, DamageLogicResult damageLogicResult) {
        return toResult(mainActor, targetActors, move, null, damageLogicResult, NextMoveRequest.of(false, null, null), null);
    }

    public ActorLogicResult toResult(BattleActor mainActor, List<BattleActor> targetActors, Move move, List<Status> statuses, DamageLogicResult damageLogicResult, NextMoveRequest nextMoveRequest, Integer omenValue) {
        if (damageLogicResult == null) damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        if (nextMoveRequest == null) nextMoveRequest = NextMoveRequest.of(false, null, null); // 후행동이 없을경우 기본객체 생성
        if (statuses == null) statuses = move.getStatuses(); // 적용 스테이터스에 변동사항이 들어오지 않으면 원래 move 의 스테이터스 전부 사용
        if (omenValue == null) omenValue = -1; // 전조 값이 없을땐 -1
        int hitCount = damageLogicResult.getDamages().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream().mapToInt(List::size).sum();
        // 오의게이지 -> 적의 공격에 의한 오의게이지 변화는 현재 미구현
//        List<Integer> chargeGauges = partyMembers.stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).map(BattleActor::getChargeGauge).toList();
        // 차지턴
        Integer enemyChargeGauge = mainActor.getChargeGauge();
        return ActorLogicResult.builder()
                .mainBattleActorId(mainActor.getId())

                .moveType(move.getType())
                .statusList(statuses)
                .enemyChargeGauge(enemyChargeGauge)
                .omenValue(omenValue)

                .totalHitCount(totalHitCount)
                .damages(damageLogicResult.getDamages())
                .additionalDamages(damageLogicResult.getAdditionalDamages())

                .enemyChargeAttackTargetIds(targetActors.stream().map(BattleActor::getId).toList())
                .enemyChargeAttackTargetNames(targetActors.stream().map(BattleActor::getName).toList())

                .hasNextMove(nextMoveRequest.hasNextMove())
                .nextMoveType(nextMoveRequest.getNextMoveType())
                .nextMoveTarget(nextMoveRequest.getNextMoveTarget())
                .build();
    }



}
