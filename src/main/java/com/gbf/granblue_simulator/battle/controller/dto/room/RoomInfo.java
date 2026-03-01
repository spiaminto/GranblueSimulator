package com.gbf.granblue_simulator.battle.controller.dto.room;

import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomInfo {
    private Long id;
    private String info;
    private String ownerUsername;
    private RoomStatus roomStatus;
    private Integer memberCount;
    private Integer maxMemberCount;
    private String remainingTime;

    private String enemyName;
    private Integer enemyHpRate;
    private String enemyPortraitSrc;

    // finished
    private String endedAt;
}
