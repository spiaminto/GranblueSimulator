package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.logic.move.dto.ForMemberAbilityInfo;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.logic.damage.MoveDamageType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    private int normalAttackCount;
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
    private List<List<StatusEffectDto>> addedBattleStatusesList = new ArrayList<>(); // 발생한 스테이터스
    @Builder.Default
    private List<List<StatusEffectDto>> removedBattleStatusesList = new ArrayList<>(); // 삭제된 스테이터스
    @Builder.Default
    private List<List<StatusEffectDto>> levelDownedBattleStatusesList = new ArrayList<>(); // 레벨 감소한 스테이터스
    @Builder.Default
    private List<Integer> heals = new ArrayList<>(); // 강압시 0 회복하므로, 0 / null 구분을 위해 Integer
    @Builder.Default
    private List<Integer> effectDamages = new ArrayList<>(); // 슬립데미지, 상태효과 데미지

    // omen
    private OmenResult omen;
    // 참전자 어빌리티
    private ForMemberAbilityInfo forMemberAbilityInfo;

    // snapshot
    private int attackMultiHitCount;
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();
    @Builder.Default
    private List<Integer> barriers = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    @Builder.Default
    private List<Boolean> canChargeAttacks = new ArrayList<>();
    private Integer fatalChainGauge;
    private Integer enemyMaxChargeGauge;
    @Builder.Default
    private List<List<Integer>> abilityCoolDowns = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityUseCounts = new ArrayList<>();
    @Builder.Default
    private List<List<Boolean>> abilitySealeds = new ArrayList<>();
    @Builder.Default
    private List<List<StatusEffectDto>> currentBattleStatusesList = new ArrayList<>(); //갱신용 전체 스테이터스

    // visual
    private VisualInfo visualInfo;

    // 기타
    @Builder.Default
    private List<Integer> summonCooldowns = new ArrayList<>();
    @Builder.Default
    private List<Integer> estimatedEnemyAtk = new ArrayList<>(); // 기준공격력 size 2, min / max

    private int resultHonor; // 내 행동 결과 공헌도

    // 기타 정보
    private String unionSummonCjsName; // 대기중인 합체소환석
    private MoveInfo unionSummonInfo;

    private Boolean isEnemyFormChange; // 적 폼체인지 여부

}


