package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.metadata.domain.asset.AssetType;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AssetInfo {

    private int currentOrder; // position

    // 기타 상태
    private Boolean isEnemy;
    private Boolean isLeaderCharacter;
    private Boolean isChargeAttackSkip;

    private Asset asset;
    private MotionType startMotion;

    @Data @Builder
    public static class Asset { // from Asset
        private Long actorId;
        private String mainCjs;
        private List<String> attackCjses;
        @Builder.Default
        private List<String> specialCjses = new ArrayList<>();
        @Builder.Default
        private Map<AssetType, AbilityCjsDto> abilityCjses = new HashMap<>();

        // 비 필수
        private int chargeAttackStartFrame;
        private String weaponId; // 주인공은 필수
        private String additionalMainCjs;
        @Builder.Default
        private List<String> additionalSpecialCjses = new ArrayList<>();
        @Builder.Default
        private Map<Long, String> summonCjses = new HashMap<>();

        @Data
        public static class AbilityCjsDto {
            private final String cjs;
            private final Boolean isTargetedEnemy;
        }
    }

}
