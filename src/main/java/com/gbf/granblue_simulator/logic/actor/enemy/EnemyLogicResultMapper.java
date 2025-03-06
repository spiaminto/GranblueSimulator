package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EnemyLogicResultMapper {

    /**
     * 적의 경우 무브만 필요한때 사용
     * break
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult toResultMoveOnly(BattleActor mainActor, List<BattleActor> partyMembers, Move move) {
        return map(mainActor, partyMembers, move, null, null, null, NextMoveRequest.of(false, null, null), null);
    }

    /**
     * 적의 Omen 결과를 같이 맵핑해야할때 사용
     * STANDBY 시작, 적의 피격으로인한 STANDBY 갱신(전조 갱신)때 사용
     * @param mainActor
     * @param partyMembers
     * @param move
     * @param omen
     * @return
     */
    public ActorLogicResult toResultWithOmen(BattleActor mainActor, List<BattleActor> partyMembers, Move move, Omen omen) {
        return map(mainActor, partyMembers, move, null, null, null, NextMoveRequest.of(false, null, null), omen);
    }

    /**
     * 데미지만 발생하는 일반공격 결과
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult attackToResult(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, null, NextMoveRequest.of(false, null, null), null);
    }

    /**
     * 데미지와 스테이터스가 발생하는 기본결과 맵핑
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult toResult(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders, SetStatusResult statusResult) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, statusResult, NextMoveRequest.of(false, null, null), null);
    }

    /**
     * 기본 결과 맵핑 + 후행동
     * @param mainActor
     * @param partyMembers
     * @param move
     * @param damageLogicResult
     * @param damageTargetOrders
     * @param setStatusResult
     * @param nextMoveRequest
     * @return
     */
    public ActorLogicResult toResultWithNextMove(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders, SetStatusResult setStatusResult, NextMoveRequest nextMoveRequest) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, setStatusResult, NextMoveRequest.of(false, null, null), null);
    }

    protected ActorLogicResult map(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> targetOrders, SetStatusResult setStatusResult, NextMoveRequest nextMoveRequest, Omen omen) {
        if (setStatusResult == null) setStatusResult = SetStatusResult.builder().build(); // 스테이터스 효과가 발생하지 않은 경우 빈객체
        if (damageLogicResult == null) damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        if (nextMoveRequest == null) nextMoveRequest = NextMoveRequest.of(false, null, null); // 후행동이 없을경우 기본객체 생성

        int hitCount = damageLogicResult.getDamages().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream().mapToInt(List::size).sum();

        // 체력
        List<Integer> hpList = new ArrayList<>();
        hpList.add(mainActor.getHp());
        List<Integer> partyMemberHpList = partyMembers.stream().map(BattleActor::getHp).toList();
        hpList.addAll(partyMemberHpList);

        // 오의게이지
        List<Integer> chargeGauges = new ArrayList<>();
        chargeGauges.add(mainActor.getChargeGauge());
        List<Integer> partyMemberChargeGauges = partyMembers.stream().map(BattleActor::getChargeGauge).toList();
        chargeGauges.addAll(partyMemberChargeGauges);

        // 추가된 스테이터스
        List<BattleStatus> enemyAddedStatus = setStatusResult.getEnemyAddedStatuses();
        List<List<BattleStatus>> partyMemberAddedStatus = setStatusResult.getPartyMemberAddedStatuses();
        List<List<BattleStatus>> resultStatusList = new ArrayList<>();
        resultStatusList.add(enemyAddedStatus);
        resultStatusList.addAll(partyMemberAddedStatus);

        // 삭제된 스테이터스
        List<BattleStatus> enemyRemovedStatuses = setStatusResult.getEnemyRemovedStatuses();
        List<List<BattleStatus>> partyMemberRemovedStatuses = setStatusResult.getPartyMemberRemovedStatuses();
        List<List<BattleStatus>> removedStatusList = new ArrayList<>();
        removedStatusList.add(enemyRemovedStatuses);
        removedStatusList.addAll(partyMemberRemovedStatuses);

        // 쿨다운
        List<List<Integer>> cooldownList = new ArrayList<>();
        cooldownList.add(new ArrayList<>());
        List<List<Integer>> partyMemberCooldowns = partyMembers.stream().map(actor -> List.of(actor.getFirstAbilityCoolDown(), actor.getSecondAbilityCoolDown(), actor.getThirdAbilityCoolDown())).toList();
        cooldownList.addAll(partyMemberCooldowns);

        // 전조 있을때 처리
        BattleEnemy enemy = (BattleEnemy) mainActor;
        OmenType omenType = omen != null ? move.getOmen().getOmenType() : null; // omenValue 가 존재하면 omenType 지정 (omenValue 가 없을때 omenType 을 null 로 놔둠)
        String omenCancelCondInfo = omen != null ? omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo() : null; // 보여줄 텍스트만
        Integer omenValue = omen != null ? enemy.getOmenValue() : null;

        return ActorLogicResult.builder()
                .mainBattleActorId(mainActor.getId())
                .mainBattleActorOrder(mainActor.getCurrentOrder())

                .moveType(move.getType())
                .hpList(hpList)
                .chargeGauges(chargeGauges)
                .addedBattleStatusesList(resultStatusList)
                .removedBattleStatusesList(removedStatusList)

                .omenType(omenType)
                .omenCancelCondInfo(omenCancelCondInfo)
                .omenValue(omenValue)

                .enemyAttackTargetOrders(targetOrders)
                .isAllTarget(move.isAllTarget())

                .totalHitCount(totalHitCount)
                .damages(damageLogicResult.getDamages())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .abilityCooldowns(cooldownList)

                .enemyDispelled(setStatusResult.isEnemyDispelled())
                .partyMemberDispelled(setStatusResult.isPartyMemberDispelled())

                .hasNextMove(nextMoveRequest.hasNextMove())
                .nextMoveType(nextMoveRequest.getNextMoveType())
                .nextMoveTarget(nextMoveRequest.getNextMoveTarget())
                .build();
    }



}
