package com.gbf.granblue_simulator.controller.response.battle;

import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class GuardResponse {
    boolean isGuardActivated;
    @Builder.Default
    List<GuardResult> guardResults = new ArrayList<>();
}
