package com.gbf.granblue_simulator.battle.controller.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class MemberResponse {

    private String username;
    private String leaderActorName;
    private String leaderActorElementType;
    private Integer honor; // orderBy

}
