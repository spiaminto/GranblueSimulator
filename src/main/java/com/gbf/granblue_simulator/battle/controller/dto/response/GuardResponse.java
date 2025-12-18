package com.gbf.granblue_simulator.battle.controller.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class GuardResponse {
    boolean isGuardActivated;
    @Builder.Default
    List<Boolean> guardStates = new ArrayList<>();
}
