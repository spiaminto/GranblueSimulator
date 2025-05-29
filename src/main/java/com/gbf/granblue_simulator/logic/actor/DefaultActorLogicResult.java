package com.gbf.granblue_simulator.logic.actor;

import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DefaultActorLogicResult {
    private final Move move;
    private final DamageLogicResult damageLogicResult;
    private final SetStatusResult setStatusResult;
}
