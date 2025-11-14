package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
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
    private StatusEffectTargetType nextMoveTarget;

    public static NextMoveRequest of(boolean hasNextMove, MoveType nextMoveType, StatusEffectTargetType nextMoveTarget) {
        NextMoveRequest request = new NextMoveRequest();
        request.hasNextMove = hasNextMove;
        request.nextMoveType = nextMoveType;
        request.nextMoveTarget = nextMoveTarget;
        return request;
    }
}
