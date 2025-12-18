package com.gbf.granblue_simulator.battle.controller.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class PotionResponse {

    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();

    private Integer potionCount;
    private Integer allPotionCount;

}
