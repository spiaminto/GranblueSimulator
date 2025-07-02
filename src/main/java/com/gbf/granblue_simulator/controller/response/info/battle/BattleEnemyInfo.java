package com.gbf.granblue_simulator.controller.response.info.battle;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
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
    private List<BattleStatus> statuses;
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
