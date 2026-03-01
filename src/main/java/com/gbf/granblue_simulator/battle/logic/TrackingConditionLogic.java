package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@Transactional
@Slf4j
@RequiredArgsConstructor
public class TrackingConditionLogic {

    private final BattleContext battleContext;

    public void updateCommonConditions(MoveLogicResult logicResult) {
        List<Actor> actors = battleContext.getCurrentFieldActors();

        for (Actor actor : actors) {
            for (Move move : actor.getMoves()) {
                Map<TrackingCondition, Object> tracker = move.getConditionTracker();
                if (tracker.isEmpty()) continue;

                for (TrackingCondition key : tracker.keySet()) {
                    int deltaValue = 0; // 변화량

                    deltaValue = switch (key) {
                        case HIT_COUNT_BY_ENEMY -> hitCountByEnemy(logicResult, actor);
                        case HIT_COUNT_BY_CHARACTER -> hitCountByCharacter(logicResult);
                        case PASSED_TURN_COUNT -> passedTurnCount(logicResult);
                        case TRIPLE_ATTACK_COUNT -> tripleAttackCount(logicResult);
                        case TAKEN_HEAL_EFFECT_COUNT -> takenHealEffectCount(logicResult, actor);
                        default ->
                                throw new IllegalArgumentException("[updateCommonConditions] invalid TrackingCondition, key = " + key.name());
                    };

                    if (deltaValue > 0) {
                        log.info("[updateCommonConditions] {} {} {} {} {}", actor.getName(), move.getBaseMove().getName(), key.name(), deltaValue, tracker);
                        int existingValue = TrackingConditionUtil.getInt(tracker, key);
                        tracker.put(key, existingValue + deltaValue); // CHECK 일단 누적처리를 기본으로 하나, 별도 처리가 필요한 구현이 필요한경우 연산 변경 필요할듯
                    }
                }
            } // for actor.moves
        } // for actors
    }

    private int hitCountByEnemy(MoveLogicResult logicResult, Actor actor) {
        if (!logicResult.getMainActor().isEnemy()) return 0;

        List<Actor> targets = logicResult.getEnemyAttackTargets();
        List<Integer> damages = logicResult.getDamages();

        int count = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getId().equals(actor.getId()) && damages.get(i) > 0
                    && targets.get(i).getStatus().getBarrier() <= 0) {
                count++;
            }
        }
        return count;
    }

    private int hitCountByCharacter(MoveLogicResult logicResult) {
        if (logicResult.getMainActor().isEnemy()) return 0;
        return logicResult.getTotalHitCount();
    }

    private int passedTurnCount(MoveLogicResult logicResult) {
        return logicResult.getMove().getType() == MoveType.TURN_FINISH ? 1 : 0;
    }

    private int tripleAttackCount(MoveLogicResult logicResult) {
        return !logicResult.getMainActor().isEnemy() && logicResult.getNormalAttackCount() >= 3 ? 1 : 0;
    }

    private int takenHealEffectCount(MoveLogicResult logicResult, Actor actor) {
        Integer heal = logicResult.getSnapshots().get(actor.getId()).getHeal();
        return heal != null && heal > 0 ? 1 : 0;
    }
}
