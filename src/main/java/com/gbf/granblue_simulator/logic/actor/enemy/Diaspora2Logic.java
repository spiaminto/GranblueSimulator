package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.logic.actor.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
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
        return resultMapper.attackToResult(mainActor, partyMembers, attackResult.getResultMove(), attackResult.getDamageLogicResult(), targetOrders);
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        DefaultActorLogicResult chargeAttackResult = defaultChargeAttack(mainActor, partyMembers, mainEnemy.getActor().getMoves().get(mainEnemy.getCurrentStandbyType()));
        List<Integer> targetOrders = chargeAttackResult.getEnemyAttackTargets().stream().map(BattleActor::getCurrentOrder).toList();
        return resultMapper.toResult(mainActor, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), targetOrders, chargeAttackResult.getSetStatusResult());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(mainActor, otherResult);
        if (omenResult.getResultMove() != null) {
            results.add(resultMapper.toResultWithOmen(mainActor, partyMembers, omenResult.getResultMove(), omenResult.getResultMove().getOmen()));
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

        // 전조발생
        omenLogic.determineStandbyMove(enemy).ifPresent(standby ->
                results.add(resultMapper.toResultWithOmen(enemy, partyMembers, standby, standby.getOmen())));
        return results;
    }



}

