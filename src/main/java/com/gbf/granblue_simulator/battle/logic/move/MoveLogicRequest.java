package com.gbf.granblue_simulator.battle.logic.move;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString @EqualsAndHashCode
@Builder
public class MoveLogicRequest {

    private Move move;
    private MoveLogicResult otherResult;

    public static MoveLogicRequest of(Move move,  MoveLogicResult otherResult) {
        return MoveLogicRequest.builder()
                .move(move)
                .otherResult(otherResult)
                .build();
    }

}
