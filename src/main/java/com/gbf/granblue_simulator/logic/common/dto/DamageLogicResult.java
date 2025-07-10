package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class DamageLogicResult {
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private List<ElementType> elementTypes = new ArrayList<>();
    private boolean isEnemyHpZero;
    @Builder.Default
    private Integer attackMultiHitCount = 1;

    @Builder.Default
    private List<Integer> targetOrders = new ArrayList<>(); // 턴종 데미지에서 임시사용
}
