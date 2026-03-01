package com.gbf.granblue_simulator.battle.controller.dto.info;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AssetInfo {

    // 기타 상태
    private Long actorId;
    private int actorOrder;
    private Boolean isLeaderCharacter;
    private Boolean isEnemy;
    private Boolean isChargeAttackSkip;
    private String weaponId; // 주인공만

    // cjs
    private String mainCjs;
    private List<String> attackCjses;
    @Builder.Default
    private List<CjsDto> specialCjses = new ArrayList<>();
    @Builder.Default
    private Map<Long, CjsDto> abilityCjses = new HashMap<>();
    @Builder.Default
    private Map<Long, String> summonCjses = new HashMap<>(); // 주인공만

    // additional cjs
    private String additionalMainCjs;
    @Builder.Default
    private List<String> additionalSpecialCjses = new ArrayList<>();

    // 기타
    private int chargeAttackStartFrame;

    @Data @Builder
    public static class CjsDto {
        private String cjs;
        private Boolean isTargetedEnemy;
        private String voiceLabel;
    }

}
