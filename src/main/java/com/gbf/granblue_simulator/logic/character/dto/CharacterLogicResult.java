package com.gbf.granblue_simulator.logic.character.dto;

import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.hibernate.tool.schema.TargetType;

import java.util.List;

@Data
@Builder
public class CharacterLogicResult {

    private Long characterId;
    private List<Integer> damages;
    private List<List<Integer>> additionalDamages;

    private List<Status> statusList;

    private MoveType moveType;

    @Accessors(fluent = true)
    private boolean hasNextMove;

    private MoveType nextMoveType;
    private StatusTargetType nextMoveTarget;

}
