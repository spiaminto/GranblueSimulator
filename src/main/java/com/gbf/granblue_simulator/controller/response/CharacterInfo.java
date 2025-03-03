package com.gbf.granblue_simulator.controller.response;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CharacterInfo {

    private Long id;
    private String name;
    private String portraitSrc;

    private Integer chargeGauge;
    private Integer maxChargeGauge;

    private Integer maxHp;
    private Integer hp;
    private Integer hpRate;

    private Move chargeAttack;
    private List<Move> abilities;
    private List<BattleStatus> statuses;

    private List<Integer> abilityCoolDowns;


}
