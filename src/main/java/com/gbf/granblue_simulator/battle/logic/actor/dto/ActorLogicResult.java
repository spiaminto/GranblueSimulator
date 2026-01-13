package com.gbf.granblue_simulator.battle.logic.actor.dto;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.logic.damage.MoveDamageType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.*;

@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @ToString @EqualsAndHashCode
public class ActorLogicResult {
    // entity
    private Actor mainActor;
    private Move move;

    // Member
    private Integer currentTurn;

    // DamageResult
    private int totalHitCount;
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<MoveDamageType> damageTypes = new ArrayList<>();
    @Builder.Default
    private List<ElementType> damageElementTypes = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();

    // 전조
    private OmenResult omenResult;

    private List<Integer> summonCooldowns = new ArrayList<>();

    // 공헌도
    private int honor;

    // 로직 추가필드
    private StatusEffectTargetType executeAttackTargetType; // 턴 진행 없이 일반공격 대상, 없으면 null
    @Accessors(fluent = true)
    private boolean executeChargeAttack; // 오의 재발동 여부
    @Builder.Default
    private List<Actor> enemyAttackTargets = new ArrayList<>(); // 적의 공격 타겟
    private boolean isUnionSummon; // 합체 소환 된 소환석인지 여부

    // 스냅샷
    @Builder.Default
    private Map<Long, Snapshot> snapshots = new LinkedHashMap<>(); // key : id

    // 헬퍼들
    public void updateHonor(int honor) { this.honor = honor; }

    public boolean notEmpty() {
        return this.move.getType() != MoveType.NONE;
    }

    public boolean isFromActor(Actor actor) {
        return actor.getId().equals(this.mainActor.getId());
    }

    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter @ToString @EqualsAndHashCode
    public static class Snapshot {
        // from SetStatusEffectResult
        @Builder.Default
        private List<ResultStatusEffectDto> addedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<ResultStatusEffectDto> levelDownedStatusEffects = new ArrayList<>();
        private Integer heal;
        private Integer effectDamage;

        // from Actor
        private Long actorId;
        private Integer currentOrder;
        private Integer hp;
        private Integer hpRate;
        private Integer chargeGauge;
        private Integer fatalChainGauge; // 일단은 여기에
        private Integer maxChargeGauge;
        private ResultStatusDto status;
        private StatusDetails statusDetails;
        private DamageStatusDetails damageStatusDetails;
        @Builder.Default
        private List<ResultStatusEffectDto> currentStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<Integer> abilityCooldowns = new ArrayList<>();
        @Builder.Default
        private List<Integer> abilityUseCounts = new ArrayList<>();
        @Builder.Default
        private List<Boolean> abilitySealeds = new ArrayList<>();
    }
}
