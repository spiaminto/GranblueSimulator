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
    private String logicId;

    // ability
    private String type;
    private String abilityType;
    private Integer hitCount;
    private Integer coolDown;
    private Long defaultVisualId;
    private String motionType;
    private String conditionTrackerString;
    private String triggerType;
    private String triggerPhase;

    // summon
    private String summonCjsName;
    private String elementType;

    // visual
    private String visualType;
    private Long actorVisualId;
    private String cjsName;
    private int chargeAttackStartFrame;
    private String isTargetedEnemy;

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
        private String isUniqueFrame;
        private String conditionalModifier;
        private Integer applyOrder;
        private String gid;

        private String statusModifiers; // type, value \n
    }
}
