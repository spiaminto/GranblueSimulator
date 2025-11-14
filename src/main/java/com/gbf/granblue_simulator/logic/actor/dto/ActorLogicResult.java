package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.base.types.MoveDamageType;
import com.gbf.granblue_simulator.domain.battle.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.domain.base.move.MotionType;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.omen.OmenType;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @ToString @EqualsAndHashCode
public class ActorLogicResult {

    // 필수
    private Long mainBattleActorId;
    private Long mainActorId; // battleActor.actor.id
    private int mainBattleActorOrder;
    private MoveType moveType;
    private MotionType motionType;
    private int motionSkipDuration;

    private Integer currentTurn;

    private List<StatusDto> statuses = new ArrayList<>();
    private List<StatusDetails> statusDetails = new ArrayList<>();
    private List <DamageStatusDetails> damageStatusDetails = new ArrayList<>();

    // 프론트 갱신용
    // 0: enemy , 1~: character (index 는 currentOrder)
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<MoveDamageType> damageTypes = new ArrayList<>();
    @Builder.Default
    private List<ElementType> damageElementTypes = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();
    @Builder.Default
    private List<Integer> chargeGauges = new ArrayList<>();
    private int fatalChainGauge;
    @Builder.Default
    private List<List<StatusEffectDto>> addedStatusEffectsList = new ArrayList<>();
    @Builder.Default
    private List<List<StatusEffectDto>> removedStatusEffectsList = new ArrayList<>();
    @Builder.Default
    private List<List<StatusEffectDto>> currentStatusEffectsList = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityCooldowns = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> abilityUseCounts = new ArrayList<>();
    @Builder.Default
    private List<List<Boolean>> abilityUsables = new ArrayList<>();
    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    
    // 프론트 갱신용 텍스트
    private String moveName;
    private String mainActorName;

    // 비필수, 중요
    private int strikeCount; // 공격 행동 횟수
    private int totalHitCount; // 통상공격의 경우 추격까지 모두 더함
    private int attackMultiHitCount; // 난격시 난격 카운트
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();
    private int enemyChargeGauge;
    
    // 소환
    @Builder.Default
    private List<Long> summonIds = new ArrayList<>();

    private OmenType omenType;
    private Integer omenValue;
    private String omenCancelCondInfo;
    private String omenName;
    private String omenInfo;

    // 적의 데미지 발생시 사용
    @Builder.Default
    private List<Integer> enemyAttackTargetOrders = new ArrayList<>(); // 적의 특수기 공격타겟 currentOrder
    private boolean isAllTarget;
    
    // 적 글로벌 효과 사용여부
    private boolean enemyPowerUp;
    private boolean enemyCtMax;

    // 공헌도
    private int honor;

    @Accessors(fluent = true)
    private boolean executeChargeAttack; // 오의 재발동 여부
    private StatusEffectTargetType executeAttackTargetType; // 턴 진행 없이 일반공격 대상, 없으면 null

    public void updateSummonIds(List<Long> summonIds) {
        this.summonIds.addAll(summonIds);
    }

    public void updateHonor(int honor) { this.honor = honor; }
}
