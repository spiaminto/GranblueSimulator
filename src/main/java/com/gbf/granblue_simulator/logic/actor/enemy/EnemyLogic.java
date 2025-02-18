package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.EnemyLogicResult;

import java.util.List;


public interface EnemyLogic {

    ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult secondAbility(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult thirdAbility(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult secondSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult thirdSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult postProcessOtherMove(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult postProcessEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult onBattleStart(BattleActor mainActor, List<BattleActor> partyMembers);

    ActorLogicResult onTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers);

    List<ActorLogicResult> onOtherMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult);

}
