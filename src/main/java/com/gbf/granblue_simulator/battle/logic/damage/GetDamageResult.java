package com.gbf.granblue_simulator.battle.logic.damage;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class GetDamageResult {
    private MoveDamageType damageType;
    private List<ElementType> elementTypes;
    private List<Integer> damages;
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private Integer attackMultiHitCount = 1;
}
