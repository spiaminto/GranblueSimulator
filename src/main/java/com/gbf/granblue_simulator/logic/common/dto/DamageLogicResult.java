package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DamageLogicResult {
    private final List<Integer> damages;
    private final List<List<Integer>> additionalDamages;
    private final boolean isEnemyHpZero;
}
