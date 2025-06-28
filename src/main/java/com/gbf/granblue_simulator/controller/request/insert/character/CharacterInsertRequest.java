package com.gbf.granblue_simulator.controller.request.insert.character;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Data;

@Data
public class CharacterInsertRequest {
    private String name;
    private String nameEn;
    private String battlePortraitSrc;
    private ElementType elementType;
    private String isMainCharacter;
}
