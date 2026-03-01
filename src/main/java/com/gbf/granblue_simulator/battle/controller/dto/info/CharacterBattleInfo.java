package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CharacterBattleInfo {

    private Long id;
    private String name;
    private Integer order;
    private String portraitSrc;

    private Integer chargeGauge;
    private Integer maxChargeGauge;
    private Integer fatalChainGauge;

    private Integer maxHp;
    private Integer hp;
    private Integer hpRate;

    private MoveInfo chargeAttack;
    private List<MoveInfo> abilities;
    private List<MoveInfo> supportAbilities;
    private List<StatusEffectDto> statuses;

    private List<Integer> abilityCoolDowns;
    private List<Boolean> abilitySealeds;


}
