package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;

import java.util.List;


public interface CharacterLogic {

    void attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    void onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

}
