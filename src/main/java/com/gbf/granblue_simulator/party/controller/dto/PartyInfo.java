package com.gbf.granblue_simulator.party.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PartyInfo {

    private final Long id;
    private final String name;
    private final String info;
    private final boolean isPrimary;

    private final List<PartyCharacterInfo> characterInfos;
    private final List<PartySummonInfo> summonInfos;


}
