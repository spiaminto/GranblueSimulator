package com.gbf.granblue_simulator.logic.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.logic.character.dto.CharacterLogicResult;
import com.gbf.granblue_simulator.logic.enemy.dto.EnemyLogicResult;

import java.util.List;

public interface EnemyLogic {

    EnemyLogicResult attack(BattleActor enemy, List<BattleActor> partyMembers);
    // 고유버프 계싼

    EnemyLogicResult chargeAttack(BattleActor enemy, List<BattleActor> partyMembers);
    // 전조 실패 처리

    void postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);
    // 고유버프 계산
    // 전조 해제 조건 처리

    void onBattleStart(BattleActor enemy, List<BattleActor> partyMembers);
    // 고유버프 추가

    void onTurnEnd(BattleActor enemy, List<BattleActor> partyMembers);
    // 전조 발생
    // 고유버프 계산

    // 보스의 고유버프 발동을 서포트 어빌리티로 지정
    void firstSupportAbility(BattleActor enemy, List<BattleActor> partyMembers);
    void secondSupportAbility(BattleActor enemy, List<BattleActor> partyMembers);
    void thirdSupportAbility(BattleActor enemy, List<BattleActor> partyMembers);
}
