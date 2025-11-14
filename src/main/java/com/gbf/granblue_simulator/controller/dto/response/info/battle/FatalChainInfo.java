package com.gbf.granblue_simulator.controller.dto.response.info.battle;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class FatalChainInfo {
    private Long id;
    private String name;
    private String info;
    private Integer gaugeValue;

    private String effectVideoSrc;
    private String seAudioSrc;
}
