package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class Diaspora2Logic extends EnemyLogic {

    public Diaspora2Logic(StatusUtil statusUtil, EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, ActorRepository actorRepository) {
        super(statusUtil, resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, actorRepository);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(BattleActor mainActor, List<BattleActor> partyMembers) {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers) {
        DefaultActorLogicResult attackResult = defaultAttack(mainActor, partyMembers);
        List<Integer> targetOrders = attackResult.getEnemyAttackTargets().stream().map(BattleActor::getCurrentOrder).toList();
        return resultMapper.attackToResult(mainActor, partyMembers, attackResult.getResultMove(), attackResult.getDamageLogicResult(), targetOrders, attackResult.getNextMoveType());
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        DefaultActorLogicResult chargeAttackResult = defaultChargeAttack(mainActor, partyMembers, mainEnemy.getActor().getMoves().get(mainEnemy.getCurrentStandbyType()));
        List<Integer> targetOrders = chargeAttackResult.getEnemyAttackTargets().stream().map(BattleActor::getCurrentOrder).toList();
        return resultMapper.toResult(mainActor, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), targetOrders, chargeAttackResult.getSetStatusResult(), chargeAttackResult.getNextMoveType());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(mainActor, otherResult);
        if (omenResult.getResultMove() != null) {
            results.add(resultMapper.toResultWithOmen(mainActor, partyMembers, omenResult.getResultMove(), omenResult.getResultOmen()));
        }

        return results;
    }

    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult enemyResult) {
        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();

        // 서포어비 1
        results.add(firstSupportAbility(mainActor, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY), null));

        // 전조발생
        omenLogic.determineStandbyMove(enemy).ifPresent(standby ->
                results.add(resultMapper.toResultWithOmen(enemy, partyMembers, standby, standby.getOmen())));

        return results;
    }

    @Override // 자신의 모드에 따라 받은 데미지의 누적값이 일정 수치에 도달할경우 턴 종료시 (자신의 모드 레벨 상승 및) 공격력 증가, 재공격 효과
    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        BattleStatus matchedBattleStatus = statusUtil.getBattleStatusByName(mainActor, "모드 『").orElse(null);
        log.info("[firstSupportAbility] matchedBattleStatus = {}", matchedBattleStatus);
        if (matchedBattleStatus == null) {
            // 해당 타입에 맞는 모드 스테이터스 없음
            log.info("[firstSupportAbility] matchedBattleStatus is null, 모드 스테이터스 없음");
            return resultMapper.emptyResult();
        }
        String matchedStatusName = matchedBattleStatus.getStatus().getName();
        MoveType getDamageSumMovetype =
                matchedStatusName.contains("알파") ? MoveType.ATTACK :
                        matchedStatusName.contains("베타") ? MoveType.ABILITY :
                                matchedStatusName.contains("감마") ? MoveType.CHARGE_ATTACK : null;
        if (getDamageSumMovetype != null) {
            // 해당 공격 타입 누적데미지 합과 증가시킬 스테이터스
            Integer takenDamageSum = battleLogService.getTakenDamageSumByMoveType(mainActor, getDamageSumMovetype);
            // log.info("[firstSupportAbility] otherMovetype = {}, takenDamageSum = {}, mathcingStatusNAme = {}, matchedBattleStatus: {}", otherMoveType, takenDamageSum, matchingStatusName, matchedBattleStatus);
            int addLevel = takenDamageSum / 30000 + 1 - matchedBattleStatus.getLevel(); // 모드레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
            log.info("[firstSupportAbility] addLevel = {}, matchedLevel = {}, takenDamageSum = {}", addLevel, matchedBattleStatus.getLevel(), takenDamageSum);
            if (addLevel > 0) {
                // 모드 레벨 상승 (결과 반환 x) - CHECK 불가능 하진 않지만 한 행동이 레벨을 2회 올릴수도 있으나, 일단 이대로 킵.
                setStatusLogic.addBattleStatusLevel(mainActor, addLevel, false, matchedBattleStatus.getStatus().getId());
                // 가하는 데미지 상승 및 재공격 적용 (결과 반환 ㅇ)
                SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, ability);
                return resultMapper.toResultWithEffect(mainActor, partyMembers, ability, null, null, setStatusResult, true, false);
            }
        }
        return resultMapper.emptyResult();
    }
}

