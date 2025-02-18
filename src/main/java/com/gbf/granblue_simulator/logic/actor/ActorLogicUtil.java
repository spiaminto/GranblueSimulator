package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import org.springframework.stereotype.Component;

@Component
public class ActorLogicUtil {

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
     * 적이 수행할 ChargeAttack 을 찾음.
     * @param mainActor enemy
     * @throws IllegalArgumentException enemy.getNextStandByType() == null
     * @return
     */
    public Move determineEnemyChargeAttack(BattleActor mainActor) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        MoveType nextStandbyType = enemy.getNextStandbyType();
        if (nextStandbyType == null) throw new IllegalStateException("Enemy has no next standby type == null");

        Move standbyMove = enemy.getActor().getMoves().get(nextStandbyType);
        MoveType chargeAttackType = standbyMove.getType().getChargeAttackType();

        return enemy.getActor().getMoves().get(chargeAttackType);
    }
}
