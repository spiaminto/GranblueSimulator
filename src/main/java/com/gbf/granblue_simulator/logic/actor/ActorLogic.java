package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;

import java.util.List;


public interface ActorLogic {

    ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    ActorLogicResult onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

}
