package com.gbf.granblue_simulator.controller.response.info.party;

import com.gbf.granblue_simulator.domain.move.Move;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PartyCharacterInfo {

    private Long id;
    private String name;
    private String portraitSrc;

    // 캐릭터 상세
    private Move chargeAttack;
    private List<Move> abilities;
    private List<Move> supportAbilities;
    private boolean isMainCharacter;
    private String elementType;
    private Integer atk;
    private Integer hp;
    private Integer def;
    private Integer doubleAttackRate;
    private Integer tripleAttackRate;
}
