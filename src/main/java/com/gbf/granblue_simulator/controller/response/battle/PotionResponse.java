package com.gbf.granblue_simulator.controller.response.battle;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class PotionResponse {

    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    private Integer potionCount;
    private Integer allPotionCount;

}
