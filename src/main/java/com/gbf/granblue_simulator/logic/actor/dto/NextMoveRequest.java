package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NextMoveRequest {
    @Accessors(fluent = true)
    private boolean hasNextMove;
    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;

    public static NextMoveRequest of(boolean hasNextMove, MoveType nextMoveType, StatusTargetType nextMoveTarget) {
        NextMoveRequest request = new NextMoveRequest();
        request.hasNextMove = hasNextMove;
        request.nextMoveType = nextMoveType;
        request.nextMoveTarget = nextMoveTarget;
        return request;
    }
}
