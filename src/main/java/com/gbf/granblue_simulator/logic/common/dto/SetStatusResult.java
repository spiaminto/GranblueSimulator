package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SetStatusResult {
    // order by currentOrder [적][아군][아군][아군][아군]
    @Builder.Default
    private List<List<BattleStatus>> addedStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<BattleStatus>> removedStatuesList = new ArrayList<>();
    @Builder.Default
    private List<Integer> healValues = new ArrayList<>();
}
