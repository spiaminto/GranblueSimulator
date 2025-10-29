package com.gbf.granblue_simulator.controller.response.battle;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data @Builder
public class BattleResponse {
    private int charOrder;
    private String charName;

    private MoveType moveType;
    private String moveName;

    private String motion;

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
    private List<List<Integer>> abilityUseCounts = new ArrayList<>();
    @Builder.Default
    private List<List<Boolean>> abilityUsables = new ArrayList<>();

    @Builder.Default
    private List<Integer> heals = new ArrayList<>(); // 강압시 0 회복하므로, 0 / null 구분을 위해 Integer
    @Builder.Default 
    private List<List<StatusDto>> addedBattleStatusesList = new ArrayList<>(); // 발생한 스테이터스
    @Builder.Default
    private List<List<StatusDto>> removedBattleStatusesList = new ArrayList<>(); // 삭제된 스테이터스
    @Builder.Default
    private List<List<StatusDto>> currentBattleStatusesList = new ArrayList<>(); //갱신용 전체 스테이터스

    @Builder.Default
    private List<Integer> enemyAttackTargetOrders = new ArrayList<>();
    private boolean isAllTarget;

    private OmenType omenType;
    private Integer omenValue;
    private String omenCancelCondInfo;
    private String omenName;
    private String omenInfo;

    private List<Long> summonIds; // 소환석 사용시 반환
    @Builder.Default
    private List<String> summonCjsNames = new ArrayList<>();

    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    private int fatalChainGauge;

    @Builder.Default
    private Map<String, Integer> memberHonors = new HashMap<>(); // 방 멤버 전체 공현도
    private int resultHonor; // 내 행동 결과 공헌도


    private boolean isEnemyPowerUp;
    private boolean isEnemyCtMax;
}


