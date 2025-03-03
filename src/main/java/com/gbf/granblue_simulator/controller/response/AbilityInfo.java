package com.gbf.granblue_simulator.controller.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AbilityInfo {
    private String name;
    private String info;
    private String iconImageSrc;
    private Integer cooldown;
}
