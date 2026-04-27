package com.gbf.granblue_simulator.battle.logic.system.dto;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class PotionResult {

    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();

    private int potionCount;
    private int allPotionCount;
    private int elixirCount;

    private Actor revivedActor;
}
