package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GetDamageResult {
    private List<Integer> damages;
    private List<List<Integer>> additionalDamages;
}
