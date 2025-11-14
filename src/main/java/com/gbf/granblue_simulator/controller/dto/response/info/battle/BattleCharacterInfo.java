package com.gbf.granblue_simulator.controller.dto.response.info.battle;

import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.move.Move;
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
    private List<StatusEffect> statuses;

    private List<Integer> abilityCoolDowns;


}
