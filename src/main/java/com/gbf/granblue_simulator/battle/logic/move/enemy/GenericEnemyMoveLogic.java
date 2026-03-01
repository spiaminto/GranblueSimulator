package com.gbf.granblue_simulator.battle.logic.move.enemy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@Transactional
public class GenericEnemyMoveLogic extends DefaultEnemyMoveLogic {

    protected GenericEnemyMoveLogic(EnemyMoveLogicDependencies enemyMoveLogicDependencies) {
        super(enemyMoveLogicDependencies);
    }

}
