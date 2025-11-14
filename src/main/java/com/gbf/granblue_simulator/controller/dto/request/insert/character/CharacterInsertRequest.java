package com.gbf.granblue_simulator.controller.dto.request.insert.character;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import lombok.Data;

@Data
public class CharacterInsertRequest {
    private String name;
    private String nameEn;
    private ElementType elementType;
    private String isLeaderCharacter;
}
