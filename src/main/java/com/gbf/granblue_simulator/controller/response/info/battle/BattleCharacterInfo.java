package com.gbf.granblue_simulator.controller.response.info.battle;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BattleCharacterInfo {

    private Long id;
    private String name;
    private Integer order;
    private String portraitSrc;

    private Integer chargeGauge;
    private Integer maxChargeGauge;

    private Integer maxHp;
    private Integer hp;
    private Integer hpRate;

    private Move chargeAttack;
    private List<AbilityInfo> abilities;
    private List<BattleStatus> statuses;

    private List<Integer> abilityCoolDowns;


}
