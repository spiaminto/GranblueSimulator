package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class StatusDto {
    private String imageSrc;
    private Integer duration;

    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트

    public static StatusDto of(Status status) {
        return StatusDto.builder()
//                .imageSrc(status.getStatusIcons().get(0).getSrc()) // TODO 수정
//                .duration(status.getDuration())
                .effectText(status.getEffectText())
                .statusText(status.getStatusText())
                .build();
    }

}
