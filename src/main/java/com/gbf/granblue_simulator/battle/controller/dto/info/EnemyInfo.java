package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.battle.controller.dto.response.OmenDto;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class EnemyInfo {
    private Long id;
    private String name;
    private Integer formOrder;
    private MoveType initialMoveType;
    private MotionType initialMotionType;
    private List<StatusEffect> statuses;
    private Integer hp;
    private Integer hpRate;
    private Integer currentChargeGauge;
    private List<Integer> maxChargeGauge; // each 반복을 위해 카운트 크기의 배열

    private OmenDto omen;
}
