package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class PotionResult {

    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();

    private int potionCount;
    private int allPotionCount;
    private int elixirCount;
}
