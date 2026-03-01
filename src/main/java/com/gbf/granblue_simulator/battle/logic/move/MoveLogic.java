package com.gbf.granblue_simulator.battle.logic.move;

import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;

@FunctionalInterface
public interface MoveLogic {

    MoveLogicResult process(MoveLogicRequest request);

    // 오버로드
//    default ActorLogicResult process() {
//        return process(null);
//    }

}
