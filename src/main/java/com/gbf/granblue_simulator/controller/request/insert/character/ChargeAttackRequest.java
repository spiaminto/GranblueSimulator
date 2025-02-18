package com.gbf.granblue_simulator.controller.request.insert.character;

import lombok.Data;

import java.util.List;

@Data
public class ChargeAttackRequest {

    private Long characterId;

    private String name;
    private String info;
    private String effectVideoSrc;
    private String seAudioSrc;

    private List<ChargeAttackStatus> statuses;

    @Data
    public static class ChargeAttackStatus {
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
