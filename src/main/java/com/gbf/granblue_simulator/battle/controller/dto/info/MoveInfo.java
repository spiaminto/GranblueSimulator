package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MoveInfo {
    private Long id;
    private String name;
    private String info;
    private String iconImageSrc;
    private String portraitImageSrc;
    private Integer cooldown;

    private String abilityType;
}
