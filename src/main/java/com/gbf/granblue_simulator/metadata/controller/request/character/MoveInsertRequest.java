package com.gbf.granblue_simulator.metadata.controller.request.character;

import lombok.Data;

import java.util.List;

@Data
public class MoveInsertRequest {
    // common
    private Long actorId;
    private String name;
    private String info;
    private Double damageRate;

    // ability
    private String type;
    private Integer hitCount;
    private Integer coolDown;
    private String hasMotion;
    private String hasSupportAbilityEffect;

    // summon
    private String cjsName;
    private String elementType;

    private List<MoveStatus> statuses;

    @Data
    public static class MoveStatus {
        private String type;
        private String name;
        private String targetType;
        private Integer maxLevel;
        private String effectText;
        private String statusText;
        private String durationType;
        private Integer duration;
        private String removable;
        private String isResistible;
        private Integer applyOrder;
        private String gid;

        private String statusModifiers; // type, value \n
    }
}
