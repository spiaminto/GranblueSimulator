package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class EnemyInfo {
    private Long id;
    private String name;
    private Integer formOrder;
    private List<StatusEffectDto> statuses;
    private Integer hp;
    private Integer hpRate;
    private Integer currentChargeGauge;
    private List<Integer> maxChargeGauge; // each 반복을 위해 카운트 크기의 배열

    private OmenResult omen;

    // 메타데이터
    private String portraitSrc;
    private Long baseId;
}
