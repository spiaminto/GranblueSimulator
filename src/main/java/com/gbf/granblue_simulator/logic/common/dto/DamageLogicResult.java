package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.move.MoveType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DamageLogicResult {
    @Builder.Default
    private final List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private final List<List<Integer>> additionalDamages = new ArrayList<>();
    private final boolean isEnemyHpZero;
}
