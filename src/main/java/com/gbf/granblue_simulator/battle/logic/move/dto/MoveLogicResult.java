package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.battle.logic.damage.MoveDamageType;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @ToString @EqualsAndHashCode
public class MoveLogicResult {
    // entity
    private Actor mainActor;
    private Move move;

    // Member
    private Integer currentTurn;

    // DamageResult
    private int totalHitCount;
    private int normalAttackCount;
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
    
    // 참전자 어빌리티
    private ForMemberAbilityInfo forMemberAbilityInfo;

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
    private boolean isEnemyFormChange;

    /**
     * 스냅샷, key = Actor.id
     */
    @Builder.Default
    private Map<Long, Snapshot> snapshots = new LinkedHashMap<>(); // key : id

    // 헬퍼들
    public void updateHonor(int honor) { this.honor = honor; }

    /**
     * 캐릭터의 행동에 대한 전조 처리는 ActorLogicResult 를 기준으로 하므로, 전조 처리후 오버라이드 필요
     * @param omenResult
     */
    public void updateOmenResult(OmenResult omenResult) {this.omenResult = omenResult;} 

    /**
     * 비어있는 결과 인지 확인
     * @return move.type == NONE 인경우 true 반환
     */
    public boolean isEmpty() {
        return this.move.getType() == MoveType.NONE;
    }

    public boolean isFromActor(Actor actor) {
        return actor.getId().equals(this.mainActor.getId());
    }

    /**
     * 스냅샷이 있는지 확인 <br>
     * 스냅샷이 없는경우 getOrDefault(empty()) 로 생성해서 객체 자체는 있음을 보장할지, 일일이 hasSnapshot 을 써야 할지 고민중
     * @param actorId
     * @return
     */
    public boolean hasSnapshot(Long actorId) {
        return snapshots.containsKey(actorId);
    }

    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter @ToString @EqualsAndHashCode
    public static class Snapshot {
        // from SetStatusEffectResult
        @Builder.Default
        private List<StatusEffectDto> addedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<StatusEffectDto> removedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<StatusEffectDto> levelDownedStatusEffects = new ArrayList<>();
        private Integer heal;
        private Integer effectDamage;

        // from Actor
        private Long actorId;
        private Integer currentOrder;
        private Integer hp;
        private Integer hpRate;
        private Integer barrier;
        private Integer chargeGauge;
        private Integer fatalChainGauge; // 일단은 여기에
        private Integer maxChargeGauge;
        private Boolean canChargeAttack;
        private ResultStatusDto status;
        private StatusDetails statusDetails;
        private DamageStatusDetails damageStatusDetails;
        @Builder.Default
        private List<StatusEffectDto> currentStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<Integer> abilityCooldowns = new ArrayList<>();
        @Builder.Default
        private List<Integer> abilityUseCounts = new ArrayList<>();
        @Builder.Default
        private List<Boolean> abilitySealeds = new ArrayList<>();
    }

}
