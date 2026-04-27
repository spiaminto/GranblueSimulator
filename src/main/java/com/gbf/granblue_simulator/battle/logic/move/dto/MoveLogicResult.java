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
@Getter
@ToString
@EqualsAndHashCode
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
    private int multiHitCount = 1;
    @Builder.Default
    private List<Integer> damages = new ArrayList<>();
    @Builder.Default
    private List<MoveDamageType> damageTypes = new ArrayList<>();
    @Builder.Default
    private List<ElementType> damageElementTypes = new ArrayList<>();
    @Builder.Default
    private List<List<Integer>> additionalDamages = new ArrayList<>();

    private List<Integer> summonCooldowns = new ArrayList<>();

    // 로직 추가필드
    private StatusEffectTargetType executeAttackTargetType; // 턴 진행 없이 일반공격 대상, 없으면 null
    @Accessors(fluent = true)
    private boolean executeChargeAttack; // 오의 재발동 여부
    @Builder.Default
    private List<Actor> enemyAttackTargets = new ArrayList<>(); // 적의 공격 타겟
    private boolean isEnemyFormChange;

    /**
     * 스냅샷, key = Actor.id
     */
    @Builder.Default
    private Map<Long, Snapshot> snapshots = new LinkedHashMap<>(); // key : id

    // 해당 컨텍스트에서 별도 update ======================================================================================

    private int honor; // 공헌도

    private OmenResult omenResult; // 전조 (기본매핑 있음)

    private ForMemberAbilityInfo forMemberAbilityInfo; // 참전자 어빌리티

    private Long changedMoveId; // 변경된 Move.id
    private Long deletedMoveId;

    // ================================================================================================================

    // 헬퍼들
    public void updateHonor(int honor) {
        this.honor = honor;
    }

    /**
     * 캐릭터의 행동에 대한 전조 처리는 ActorLogicResult 를 기준으로 하므로, 전조 처리후 오버라이드 필요
     *
     * @param omenResult
     */
    public void updateOmenResult(OmenResult omenResult) {
        this.omenResult = omenResult;
    }

    /**
     * 참전자 어빌리티 있을때 해당정보 추가
     */
    public void updateForMemberAbilityInfo(ForMemberAbilityInfo forMemberAbilityInfo) {
        this.forMemberAbilityInfo = forMemberAbilityInfo;
    }

    /**
     * mainActor의 Move 변화 있을때 해당 Move.id 추가
     */
    public void updateChangedMoveIdWithDeletedMoveId(Long changedMoveId, Long deletedMoveId) {
        this.changedMoveId = changedMoveId;
        this.deletedMoveId = deletedMoveId;
    }

    /**
     * 비어있는 결과 인지 확인
     *
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
     *
     * @param actorId
     * @return
     */
    public boolean hasSnapshot(Long actorId) {
        return snapshots.containsKey(actorId);
    }

    @Builder
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Getter
    @ToString
    @EqualsAndHashCode
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
        @ToString.Exclude
        private ResultStatusDto status;
        @ToString.Exclude
        private StatusDetails statusDetails;
        @ToString.Exclude
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MoveLogicResult(\n");
        sb.append("  mainActor=").append(mainActor == null ? "null" : "Actor(id=" + mainActor.getId() + ", name=" + mainActor.getName() + ")").append(",\n");
        sb.append("  move=").append(move == null ? "null" : "Move(id=" + move.getId() + ", name=" + move.getBaseMove().getName() + ", baseId=" + move.getBaseMove().getId() + ")").append(",\n");
        sb.append("  currentTurn=").append(currentTurn).append(",\n");
        sb.append("  totalHitCount=").append(totalHitCount).append(",\n");
        sb.append("  normalAttackCount=").append(normalAttackCount).append(",\n");
        sb.append("  multiHitCount=").append(multiHitCount).append(",\n");
        sb.append("  damages=").append(damages).append(",\n");
        sb.append("  damageTypes=").append(damageTypes).append(",\n");
        sb.append("  damageElementTypes=").append(damageElementTypes).append(",\n");
        sb.append("  additionalDamages=").append(additionalDamages).append(",\n");
        sb.append("  summonCooldowns=").append(summonCooldowns).append(",\n");
        sb.append("  executeAttackTargetType=").append(executeAttackTargetType).append(",\n");
        sb.append("  executeChargeAttack=").append(executeChargeAttack).append(",\n");
        sb.append("  enemyAttackTargets=").append(enemyAttackTargets == null ? "null" :
                enemyAttackTargets.stream().map(a -> "Actor(id=" + a.getId() + ", name=" + a.getName() + ")").toList()
        ).append(",\n");
        sb.append("  isEnemyFormChange=").append(isEnemyFormChange).append(",\n");
        sb.append("  honor=").append(honor).append(",\n");
        sb.append("  omenResult=").append(omenResult).append(",\n");
        sb.append("  forMemberAbilityInfo=").append(forMemberAbilityInfo).append(",\n");
        sb.append("  changedMoveId=").append(changedMoveId).append(",\n");
        sb.append("  deletedMoveId=").append(deletedMoveId).append(",\n");

        sb.append("  snapshots={\n");
        snapshots.forEach((id, snapshot) -> {
            sb.append("    [actorId=").append(id).append("] Snapshot(\n");
            sb.append("      actorId=").append(snapshot.getActorId()).append(",\n");
            sb.append("      currentOrder=").append(snapshot.getCurrentOrder()).append(",\n");
            sb.append("      hp=").append(snapshot.getHp()).append(",\n");
            sb.append("      hpRate=").append(snapshot.getHpRate()).append(",\n");
            sb.append("      barrier=").append(snapshot.getBarrier()).append(",\n");
            sb.append("      chargeGauge=").append(snapshot.getChargeGauge()).append(",\n");
            sb.append("      fatalChainGauge=").append(snapshot.getFatalChainGauge()).append(",\n");
            sb.append("      maxChargeGauge=").append(snapshot.getMaxChargeGauge()).append(",\n");
            sb.append("      canChargeAttack=").append(snapshot.getCanChargeAttack()).append(",\n");
            sb.append("      heal=").append(snapshot.getHeal()).append(",\n");
            sb.append("      effectDamage=").append(snapshot.getEffectDamage()).append(",\n");
            sb.append("      abilityCooldowns=").append(snapshot.getAbilityCooldowns()).append(",\n");
            sb.append("      abilityUseCounts=").append(snapshot.getAbilityUseCounts()).append(",\n");
            sb.append("      abilitySealeds=").append(snapshot.getAbilitySealeds()).append(",\n");

            sb.append("      currentStatusEffects=[\n");
            snapshot.getCurrentStatusEffects().forEach(e -> sb.append("        ").append(e).append(",\n"));
            sb.append("      ],\n");

            sb.append("      addedStatusEffects=[\n");
            snapshot.getAddedStatusEffects().forEach(e -> sb.append("        ").append(e).append(",\n"));
            sb.append("      ],\n");

            sb.append("      removedStatusEffects=[\n");
            snapshot.getRemovedStatusEffects().forEach(e -> sb.append("        ").append(e).append(",\n"));
            sb.append("      ],\n");

            sb.append("      levelDownedStatusEffects=[\n");
            snapshot.getLevelDownedStatusEffects().forEach(e -> sb.append("        ").append(e).append(",\n"));
            sb.append("      ]\n");

            sb.append("    ),\n");
        });
        sb.append("  }\n");
        sb.append(")");
        return sb.toString();
    }

}
