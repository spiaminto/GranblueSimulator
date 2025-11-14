package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.base.types.MoveDamageType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class GetDamageResult {
    private MoveDamageType damageType;
    private List<ElementType> elementTypes;
    private List<Integer> damages;
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private Integer attackMultiHitCount = 1;
}
