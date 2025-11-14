package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.logic.actor.dto.StatusEffectDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@Builder
public class SetStatusResult {
    // order by currentOrder [적][아군][아군][아군][아군]
    @Builder.Default
    private List<List<StatusEffectDto>> addedStatusesList = new ArrayList<>();
    @Builder.Default
    private List<List<StatusEffectDto>> removedStatuesList = new ArrayList<>();
    @Builder.Default
    private List<Integer> healValues = new ArrayList<>();
    @Builder.Default
    private List<Integer> damageValues = new ArrayList<>();

    /**
     * 빈 결과를 프론트에 맞게 생성 (resultMapper 에서 null 로 받을시 사용)
     * @return
     */
    public static SetStatusResult emptyResult() {
        return SetStatusResult.builder()
                .addedStatusesList(IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList()))
                .removedStatuesList(IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList()))
                .healValues(new ArrayList<>(Collections.nCopies(5, null)))
                .damageValues(new ArrayList<>(Collections.nCopies(5, null)))
                .build();
    }
}
