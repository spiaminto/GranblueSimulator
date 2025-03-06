package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ActorLogicUtil {

    private final SetStatusLogic setStatusLogic;

    public Move determineAttackMove(BattleActor mainActor) {
        MoveType moveType = MoveType.SINGLE_ATTACK;
        if (Math.random() < mainActor.getTripleAttackRate()) {
            moveType = MoveType.TRIPLE_ATTACK;
        } else if (Math.random() < mainActor.getDoubleAttackRate()) {
            moveType = MoveType.DOUBLE_ATTACK;
        }
        return mainActor.getActor().getMoves().get(moveType);
    }

    /**
     * 아군과 적 을 모두 받아 BattleStatus 의 남은시간과 어빌리티 쿨타임을 진행
     */
    @Transactional
    public void progressTurn(BattleActor enemy, List<BattleActor> partyMembers) {
        setStatusLogic.progressBattleStatus(enemy, partyMembers);
        partyMembers.forEach(BattleActor::progressAbilityCoolDown);
    }

}
