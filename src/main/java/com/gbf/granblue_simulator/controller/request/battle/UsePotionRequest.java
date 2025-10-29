package com.gbf.granblue_simulator.controller.request.battle;

import lombok.Data;

@Data
public class UsePotionRequest {

    private Long memberId;
    private Long characterId; // 포션 사용 캐릭터
    private String potionType; // 변할 일 없어서 single, all, elixir 고정

}
