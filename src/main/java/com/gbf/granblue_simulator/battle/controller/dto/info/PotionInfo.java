package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PotionInfo {
    private int potionCount;
    private int allPotionCount;
    private int elixirCount;
}
