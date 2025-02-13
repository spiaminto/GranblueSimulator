package com.gbf.granblue_simulator.logic.enemy.dto;

import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
public class EnemyLogicResult {
    private Long enemyId;
    private List<Integer> damages;
    private List<List<Integer>> additionalDamages;

    private List<Status> statusList;

    private MoveType moveType;

    @Accessors(fluent = true)
    private boolean hasNextMove;

    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;
}
