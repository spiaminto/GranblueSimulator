package com.gbf.granblue_simulator.metadata.controller.request.character;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Data;

@Data
public class CharacterInsertRequest {
    private String name;
    private String nameEn;
    private ElementType elementType;
    private String isLeaderCharacter;
    private String isAttackAllTarget; // 일반공격의 전체공격 여부
    private Long actorVisualId;
}
