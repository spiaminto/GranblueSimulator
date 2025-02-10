package com.gbf.granblue_simulator.controller.request.insert.character;

import lombok.Data;

import java.util.List;

@Data
public class AbilityRequest {
    private Long characterId;

    private String type;

    private String name;
    private String info;
    private Double damageRate;
    private Integer hitCount;
    private Integer coolDown;
    private String duration;


    private String iconSrc;
    private String effectVideoSrc;
    private String motionVideoSrc;
    private String seAudioSrc;
    private String voiceAudioSrc;

    private List<AbilityStatus> statuses; // 최대 5개

    @Data
    public static class AbilityStatus {
        private String type;
        private String effectType;
        private Integer maxLevel;
        private String name;
        private String targetType;
        private String effectText;
        private String statusText;
        private Integer duration;
        private String iconSrcs;

        private String statusEffects; // type, value \n
    }
}
