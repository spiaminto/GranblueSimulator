package com.gbf.granblue_simulator.battle.controller.dto.request;

import com.gbf.granblue_simulator.battle.domain.PotionType;
import lombok.Data;

@Data
public class UsePotionRequest {

    private PotionType potionType;
    private Long targetActorId; // 포션 타겟 캐릭터 (mainActor 와 별도)

}
