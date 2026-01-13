package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class DamageLogicResult {
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private List<ElementType> elementTypes = new ArrayList<>();
    @Builder.Default
    private List<MoveDamageType> damageTypes = new ArrayList<>(); // 적은 타겟이 여러개라 여러개 써야함
    @Builder.Default
    private Integer attackMultiHitCount = 1;
}
