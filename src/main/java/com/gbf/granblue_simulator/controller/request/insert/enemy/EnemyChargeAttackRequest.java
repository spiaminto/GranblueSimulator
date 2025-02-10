package com.gbf.granblue_simulator.controller.request.insert.enemy;

import lombok.Data;

import java.util.List;

@Data
public class EnemyChargeAttackRequest {

    private Long enemyId;

    private String name;
    private String type;
    private String info;
    private Integer damageRate;
    private String effectVideoSrc;
    private String seAudioSrc;

    private List<ChargeAttackStatus> statuses;

    @Data
    public static class ChargeAttackStatus {
        private String type;
        private String effectText;
        private Integer maxLevel;
        private String targetType;
        private String statusText;
        private Integer duration;
        private String iconSrcs; // 엔터로 구분되는 src 들

        private String statusEffects; // type, value \n
    }
}
