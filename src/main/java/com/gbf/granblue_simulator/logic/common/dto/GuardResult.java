package com.gbf.granblue_simulator.logic.common.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GuardResult {
    private Integer currentOrder;
    private boolean isGuardOn;
}
