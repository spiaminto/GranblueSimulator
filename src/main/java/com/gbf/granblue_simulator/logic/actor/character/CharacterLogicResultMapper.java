package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.NextMoveRequest;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CharacterLogicResultMapper {

    /**
     * 캐릭터 로직에서 아무것도 발생하지 않았을때 리턴 (null X)
     *
     * @return
     */
    public ActorLogicResult emptyResult() {
        return ActorLogicResult.builder().moveType(MoveType.NONE).build();
    }

    /**
     * 데미지만 발생하는 기본공격 결과맵핑
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult attackToResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, null, null);
    }

    /**
     * 데미지와 스테이터스 모두 발생하는 일반 결과 맵핑
     * 어빌리티, 차지어택, 서포트 어빌리티
     * 데미지 또는 스테이터스 각 안발생했을 경우 null 입력
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult statusResult) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, statusResult, null);
    }

    /**
     * 일반 결과 맵핑에 후 행동이 있을 경우 후행동과 함께 결과 맵핑
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @param damageLogicResult
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResultWithNextMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult statusResult, NextMoveRequest nextMoveRequest) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, statusResult, nextMoveRequest);
    }

    protected ActorLogicResult map(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult setStatusResult, NextMoveRequest nextMoveRequest) {
        if (setStatusResult == null) setStatusResult = SetStatusResult.builder().build(); // 스테이터스 효과가 발생하지 않은 경우 빈객체
        if (damageLogicResult == null)
            damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        if (nextMoveRequest == null) nextMoveRequest = NextMoveRequest.of(false, null, null); // 후행동이 없을경우 기본객체 생성
        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0)
                        .toList())
                .mapToInt(List::size)
                .sum();

        // 체력
        List<Integer> hps = new ArrayList<>();
        List<Integer> hpRates = new ArrayList<>();
        hps.add(enemy.getHp());
        hpRates.add(enemy.calcHpRate());
        List<Integer> partyMemberHpList = partyMembers.stream().map(BattleActor::getHp).toList();
        hps.addAll(partyMemberHpList);
        List<Integer> partyMemberHpRateList = partyMembers.stream().map(BattleActor::calcHpRate).toList();
        hpRates.addAll(partyMemberHpRateList);

        // 오의게이지
        List<Integer> chargeGauges = new ArrayList<>();
        chargeGauges.add(enemy.getChargeGauge());
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

        return ActorLogicResult.builder()
                .mainBattleActorId(mainActor.getId())
                .mainActorId(mainActor.getActor().getId())
                .mainBattleActorOrder(mainActor.getCurrentOrder())
                .targetActorId(enemy.getActor().getId())
                .moveType(move.getType())

                .currentTurn(mainActor.getMember().getCurrentTurn())

                .hps(hps)
                .hpRates(hpRates)
                .chargeGauges(chargeGauges)
                .addedBattleStatusesList(resultStatusList)
                .removedBattleStatusesList(removedStatusList)
                .heals(setStatusResult.getHealValues())

                .totalHitCount(totalHitCount)
                .attackMultiHitCount(damageLogicResult.getAttackMultiHitCount())
                .damages(damageLogicResult.getDamages())
                .damageElementTypes(damageLogicResult.getElementTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .abilityCooldowns(cooldownList)

                .hasNextMove(nextMoveRequest.hasNextMove())
                .nextMoveType(nextMoveRequest.getNextMoveType())
                .nextMoveTarget(nextMoveRequest.getNextMoveTarget())
                .build();
    }


}
