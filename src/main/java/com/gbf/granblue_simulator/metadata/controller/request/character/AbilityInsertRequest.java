package com.gbf.granblue_simulator.metadata.controller.request.character;

import lombok.Data;

import java.util.List;

@Data
public class AbilityInsertRequest {
    private Long characterId;

    private String type;

    private String name;
    private String info;
    private Double damageRate;
    private Integer hitCount;
    private Integer coolDown;
    private String hasMotion;
    private String hasSupportAbilityEffect;

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
        private String removable;
        private String isResistible;

        private String statusEffects; // type, value \n
    }
}
