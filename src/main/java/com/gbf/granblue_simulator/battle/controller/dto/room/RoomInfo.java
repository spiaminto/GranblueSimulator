package com.gbf.granblue_simulator.battle.controller.dto.room;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomInfo {
    private Long id;
    private String info;
    private Integer memberCount;
    private Integer enemyHpRate;
    private String ownerUsername;
}
