package com.gbf.granblue_simulator.party.controller.dto;

import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
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
    private BaseMove chargeAttack;
    private List<BaseMove> abilities;
    private List<BaseMove> supportAbilities;
    private boolean isMainCharacter;
    private String elementType;
    private Integer atk;
    private Integer hp;
    private Double def;
    private Integer doubleAttackRate;
    private Integer tripleAttackRate;
}
