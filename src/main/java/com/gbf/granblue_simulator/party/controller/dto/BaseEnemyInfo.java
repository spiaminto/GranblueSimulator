package com.gbf.granblue_simulator.party.controller.dto;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BaseEnemyInfo {

    private Long id;
    private Long nextFormId;
    private Long beforeFormId;
    private String name;
    private String portraitSrc;


    private List<ChargeAttack> chargeAttacks;
    private List<MoveInfo> abilities;
    private List<MoveInfo> supportAbilities;
    private List<MoveInfo> chargeAttackInfos;

    private String elementType;
    private Integer atk;
    private Integer hp;
    private Double def;
    private Integer doubleAttackRate;
    private Integer tripleAttackRate;
    private Integer maxChargeGauge;

    private List<UserCharacterMoveStatus> abilityStatuses;
    private List<UserCharacterMoveStatus> supportAbilityStatuses;

    @Data
    @Builder
    public static class ChargeAttack {
        private BaseOmen omen;
        private List<OmenCancelCond> cancelConds;
        private MoveInfo move;
    }

}
