package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.metadata.domain.RaidType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaidInfo {

    private Long id;

    private RaidType type;
    private String name;
    private String info;
    private String raidImageSrc;

}
