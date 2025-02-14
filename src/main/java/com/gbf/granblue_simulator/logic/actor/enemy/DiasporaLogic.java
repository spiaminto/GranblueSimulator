package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.logic.actor.ActorLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiasporaLogic implements ActorLogic {

    @Override
    public ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }
}
