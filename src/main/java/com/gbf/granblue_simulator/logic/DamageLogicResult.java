package com.gbf.granblue_simulator.logic;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DamageLogicResult {
    private List<Integer> damages;
    private List<Integer> additionalDamages;
}
