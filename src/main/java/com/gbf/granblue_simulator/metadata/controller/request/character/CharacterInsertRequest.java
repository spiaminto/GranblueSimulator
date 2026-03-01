package com.gbf.granblue_simulator.metadata.controller.request.character;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Data;

@Data
public class CharacterInsertRequest {
    private String name;
    private String nameEn;
    private ElementType elementType;
    private String isLeaderCharacter;
    private String normalAttackElementType;

    private Long actorVisualId;

    private String visualType;
    private String attackCjsNames;
    private String attackLogicId;

    private String abilityIds;
    private String supportAbilityIds;
    private String allAbilityIds;
    private String allSupportAbilityIds;
    private String chargeAttackIds;
    private String triggeredAbilityIds;
    private String changingMoveIds;

}
