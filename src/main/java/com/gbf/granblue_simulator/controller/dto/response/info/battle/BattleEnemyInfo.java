package com.gbf.granblue_simulator.controller.dto.response.info.battle;

import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.move.MotionType;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.omen.OmenType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class BattleEnemyInfo {
    private Long id;
    private String name;
    private Integer phase;
    private MoveType initialMoveType;
    private MotionType initialMotionType;
    private List<StatusEffect> statuses;
    private Integer hpRate;
    private Integer currentChargeGauge;
    private List<Integer> maxChargeGauge; // each 반복을 위해 카운트 크기의 배열

    private boolean omenActivated;
    private OmenType omenType;
    private String omenPrefix;
    private Integer omenValue;
    private String omenName;
    private String omenInfo;
}
