package com.gbf.granblue_simulator.controller.response.info.battle;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AssetInfo {

    // 필수
    private Long actorId;
    private String mainCjs;
    private String attackCjs;
    @Builder.Default 
    private List<String> specialCjses = new ArrayList<>();
    @Builder.Default 
    private List<String> abilityCjses = new ArrayList<>();
    private String startMotion; // 아군은 stbwait or 빈사, 적은 wait or standby

    // 비 필수
    private String weaponId; // 주인공은 필수
    private String additionalMainCjs;
    @Builder.Default 
    private List<String> additionalSpecialCjses = new ArrayList<>();
    @Builder.Default
    private List<String> summonCjses = new ArrayList<>();

    // 기타 상태
    private Boolean isEnemy;
    private Boolean isMainCharacter;
    private Boolean isChargeAttackSkip;
    private int chargeAttackStartFrame;

}
