package com.gbf.granblue_simulator.logic.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.enemy.dto.EnemyLogicResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiasporaLogic implements EnemyLogic{

    private final SetStatusLogic setStatusLogic;

    public DiasporaLogic(SetStatusLogic setStatusLogic) {
        this.setStatusLogic = setStatusLogic;
    }

    @Override
    public EnemyLogicResult attack(BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public EnemyLogicResult chargeAttack(BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public void postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void onBattleStart(BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void onTurnEnd(BattleActor enemy, List<BattleActor> partyMembers) {
    }

    @Override
    public void firstSupportAbility(BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void secondSupportAbility(BattleActor enemy, List<BattleActor> partyMembers) {

    }

    @Override
    public void thirdSupportAbility(BattleActor enemy, List<BattleActor> partyMembers) {

    }
}
