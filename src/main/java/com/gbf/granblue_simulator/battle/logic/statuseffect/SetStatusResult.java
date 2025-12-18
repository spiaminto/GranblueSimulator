package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Builder
public class SetStatusResult {
    // order by currentOrder [적][아군][아군][아군][아군]
    @Builder.Default
    private List<List<ResultStatusEffectDto>> addedStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<ResultStatusEffectDto>> removedStatuesList = new ArrayList<>();
    @Builder.Default
    private List<Integer> healValues = new ArrayList<>();
    @Builder.Default
    private List<Integer> damageValues = new ArrayList<>(); // effectDamages 로

    /**
     * 빈 결과를 프론트에 맞게 생성 (resultMapper 에서 null 로 받을시 사용)
     * @return
     */
    public static SetStatusResult emptyResult() {
        return SetStatusResult.builder()
                .addedStatusesList(Stream.generate(ArrayList<ResultStatusEffectDto>::new).limit(5).collect(Collectors.toUnmodifiableList()))
                .removedStatuesList(Stream.generate(ArrayList<ResultStatusEffectDto>::new).limit(5).collect(Collectors.toUnmodifiableList()))
                .healValues(new ArrayList<>(Collections.nCopies(5, null)))
                .damageValues(new ArrayList<>(Collections.nCopies(5, null)))
                .build();
    }
}
