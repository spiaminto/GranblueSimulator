package com.gbf.granblue_simulator.controller.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class StatusDto {
    private String type; // StatusType
    private String name;

    private String imageSrc;

    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트
    private int duration;
}
