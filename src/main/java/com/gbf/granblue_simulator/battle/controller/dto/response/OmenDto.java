package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class OmenDto {
    private OmenType type;
    private MoveType standbyMoveType;
    private Integer remainValue;
    private String cancelCondition;
    private String updateTiming;

    private String name;
    private String info;
    private String motion;
}
