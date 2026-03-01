package com.gbf.granblue_simulator.user.domain;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserCharacterMove {

    private MoveType moveType;
    private UserCharacterMoveStatus status;

}
