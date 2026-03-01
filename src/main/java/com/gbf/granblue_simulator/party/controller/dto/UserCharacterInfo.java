package com.gbf.granblue_simulator.party.controller.dto;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserCharacterInfo {

    private Long id;
    private String name;
    private String portraitSrc;
    private String iconSrc;

    // 캐릭터 상세
    private MoveInfo chargeAttack;
    private List<MoveInfo> abilities;
    private List<MoveInfo> supportAbilities;
    private boolean isLeaderCharacter;
    private String elementType;
    private Integer atk;
    private Integer hp;
    private Double def;
    private Integer doubleAttackRate;
    private Integer tripleAttackRate;

    private List<UserCharacterMoveStatus> abilityStatuses;
    private List<UserCharacterMoveStatus> supportAbilityStatuses;
}
