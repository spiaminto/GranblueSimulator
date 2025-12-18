package com.gbf.granblue_simulator.metadata.controller.character;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Data;

@Data
public class CharacterInsertRequest {
    private String name;
    private String nameEn;
    private ElementType elementType;
    private String isLeaderCharacter;
}
