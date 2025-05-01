package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GetDamageResult {
    private List<ElementType> elementTypes;
    private List<Integer> damages;
    private List<List<Integer>> additionalDamages;
}
