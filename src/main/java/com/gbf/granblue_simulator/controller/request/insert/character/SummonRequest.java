package com.gbf.granblue_simulator.controller.request.insert.character;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Data;

import java.util.List;

@Data
public class SummonRequest {

    private Long characterId;

    private String name;
    private String info;
    private Double damageRate;
    private Integer hitCount;
    private Integer coolDown;
    private String effectVideoSrc;
    private String seAudioSrc;
    private ElementType elementType;
    private String iconSrc;

    private List<SummonStatus> statuses;

    @Data
    public static class SummonStatus {
        private String type;
        private String name;
        private String targetType;
        private Integer maxLevel;
        private String effectText;
        private String statusText;
        private Integer duration;
        private String removable;
        private String iconSrcs; // 엔터로 구분되는 src 들

        private String statusEffects; // type, value \n
    }
}
