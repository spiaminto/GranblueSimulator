package com.gbf.granblue_simulator.controller.dto.response.info.party;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PartySummonInfo {

    private Long id;
    private String name;
    private String info;
    private String portraitSrc;
    private Integer cooldown;

}
