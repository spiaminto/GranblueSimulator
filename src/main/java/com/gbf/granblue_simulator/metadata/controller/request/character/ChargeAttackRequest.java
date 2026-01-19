package com.gbf.granblue_simulator.metadata.controller.request.character;

import lombok.Data;

import java.util.List;

@Data
public class ChargeAttackRequest {

    private Long characterId;

    private String name;
    private String info;
    private Integer damageRate;

    private List<ChargeAttackStatus> statuses;

    @Data
    public static class ChargeAttackStatus {
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

        private String statusEffects; // type, value \n
    }
}
