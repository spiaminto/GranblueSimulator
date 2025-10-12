package com.gbf.granblue_simulator.controller.request.battle;

import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import lombok.Data;

@Data
public class UsePotionRequest {

    private Long memberId;
    private Long actorId; // 포션 사용 캐릭터
    private StatusTargetType targetType;

}
