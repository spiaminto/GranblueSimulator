package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.logic.damage.MoveDamageType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data @Builder
public class BattleResponse {

    // actor
    private Long actorId;
    private int actorOrder;
    private String actorName;

    // move
    private Long moveId;
    private String moveName;
    private MoveType moveType;
    private String motion;
    private int motionCustomDuration;
    private Boolean isAllTarget;

    // damageResult
    private int totalHitCount;
    @Builder.Default
    private List<String> damages = new ArrayList<>();
    @Builder.Default
    private List<ElementType> elementTypes = new ArrayList<>();
    @Builder.Default
    private List<MoveDamageType> damageTypes = new ArrayList<>();
    @Builder.Default
    private List<List<String>> additionalDamages = new ArrayList<>();

    @Builder.Default
    private List<Long> enemyAttackTargetIds = new ArrayList<>();

    // statusResult
    @Builder.Default
    private List<List<StatusDto>> addedBattleStatusesList = new ArrayList<>(); // 발생한 스테이터스
    @Builder.Default
    private List<List<StatusDto>> removedBattleStatusesList = new ArrayList<>(); // 삭제된 스테이터스
    @Builder.Default
    private List<Integer> heals = new ArrayList<>(); // 강압시 0 회복하므로, 0 / null 구분을 위해 Integer
    @Builder.Default
    private List<Integer> effectDamages = new ArrayList<>(); // 슬립데미지, 상태효과 데미지

    // omen
    private OmenDto omen;

    // snapshot
    private int attackMultiHitCount;
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    private Integer fatalChainGauge;
    private Integer enemyMaxChargeGauge;
    @Builder.Default
    private List<List<Integer>> abilityCoolDowns = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityUseCounts = new ArrayList<>();
    @Builder.Default
    private List<List<Boolean>> abilitySealeds = new ArrayList<>();
    @Builder.Default
    private List<List<StatusDto>> currentBattleStatusesList = new ArrayList<>(); //갱신용 전체 스테이터스

    // 기타
    @Builder.Default
    private List<Integer> summonCooldowns = new ArrayList<>();
    @Builder.Default
    private List<Integer> estimatedEnemyAtk = new ArrayList<>(); // 기준공격력 size 2, min / max

    private int resultHonor; // 내 행동 결과 공헌도

    // 기타 정보
    private Long unionSummonId; // 대기중인 합체소환석
    private boolean hasUnionSummon; // SUMMON 일 시, unionSummon 이 있는지 여부
    private Boolean isUnionSummon; // SUMMON 일 시, unionSummon 인지 여부

}


