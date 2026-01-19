package com.gbf.granblue_simulator.metadata.controller.request.enemy;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import lombok.Data;

import java.util.List;

@Data
public class EnemyChargeAttackRequest {

    private Long enemyId;

    private String name;
    private String type;
    private String info;
    private ElementType elementType;
    private Integer hitCount;
    private Integer damageRate;
    private Integer damageConstant;
    private Integer randomStatusCount;
    private String effectVideoSrc;
    private String seAudioSrc;
    private Integer effectHitDelay;
    private String isAllTarget;

    private List<ChargeAttackStatus> statuses;

    @Data
    public static class ChargeAttackStatus {
        private String type;
        private String effectText;
        private Integer maxLevel;
        private String targetType;
        private String statusText;
        private Integer duration;
        private String removable;
        private String isResistible;
        private String iconSrcs; // 엔터로 구분되는 src 들


        private String statusEffects; // type, value \n
    }
}
