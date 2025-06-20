package com.gbf.granblue_simulator.controller.response;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class BattleResponse {
    private int charOrder;
    private MoveType moveType;
    private int totalHitCount;
    private int attackMultiHitCount;

    @Builder.Default
    private List<String> damages = new ArrayList<>();
    @Builder.Default
    private List<ElementType> elementTypes = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();
    @Builder.Default
    private List<List<String>> additionalDamages = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityCoolDowns = new ArrayList<>();

    @Builder.Default 
    private List<List<StatusDto>> addedBattleStatusList = new ArrayList<>(); // 발생한 스테이터스
    @Builder.Default
    private List<List<StatusDto>> removedBattleStatusList = new ArrayList<>(); // 삭제된 스테이터스
    @Builder.Default
    private List<List<StatusDto>> battleStatusList = new ArrayList<>(); //갱신용 전체 스테이터스

    @Builder.Default
    private List<Integer> enemyAttackTargetOrders = new ArrayList<>();
    private boolean isAllTarget;

    private OmenType omenType;
    private Integer omenValue;
    private String omenCancelCondInfo;
    private String omenName;
    private String omenInfo;

    private Long summonId; // 소환석 사용시 반환

    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();

    private boolean isPartyMemberDispelled;
    private boolean isEnemyDispelled;
    private boolean isEnemyPowerUp;
    private boolean isEnemyCtMax;
}
