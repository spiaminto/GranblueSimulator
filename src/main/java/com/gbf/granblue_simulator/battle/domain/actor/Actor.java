package com.gbf.granblue_simulator.battle.domain.actor;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.prop.*;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(of = {"id", "createdAt", "dtype", "name", "currentOrder", "elementType"})
@ToString
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn
public abstract class Actor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(insertable = false, updatable = false)
    private String dtype;

    private String name;

    private Integer currentOrder; // 자신의 배치 순서

    @Enumerated(EnumType.STRING)
    private ElementType elementType;

    @Accessors(fluent = true)
    private boolean isGuardOn;

    private int executedStrikeCount; // 해당 턴 공격행동 횟수 (턴 종료시 0으로 초기화)

    @OneToMany(mappedBy = "actor")
    @Builder.Default
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private List<Move> moves = new ArrayList<>();
    @Transient
    @Builder.Default
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private Map<MoveType, List<Move>> moveTypeMap = new HashMap<>();
    @Transient
    @Builder.Default
    @ToString.Exclude
    @Getter(AccessLevel.NONE)
    private Map<TriggerType, List<Move>> triggerTypeMap = new HashMap<>();

    @OneToOne(mappedBy = "actor")
    @ToString.Exclude
    private Status status;

    @ManyToOne
    @JoinColumn(name = "member_id")
    @ToString.Exclude
    private Member member;

    @ManyToOne
    @JoinColumn(name = "base_actor_id")
    @ToString.Exclude
    private BaseActor baseActor;

    @OneToOne
    @JoinColumn(name = "actor_visual_id")
    @ToString.Exclude
    private ActorVisual actorVisual;

    @OneToMany(mappedBy = "actor")
    @Builder.Default
    @ToString.Exclude
    private List<StatusEffect> statusEffects = new ArrayList<>();

    @ToString.Include(name = "statusEffects")
    public List<String> toStringStatusEffects() {
        if (statusEffects == null) return null;
        return statusEffects.stream()
                .map(statusEffect -> statusEffect.getBaseStatusEffect().getName() + "(" + statusEffect.getDuration() + "턴)")
                .collect(Collectors.toList());
    }

    @CreationTimestamp
    private LocalDateTime createdAt;

    @PostLoad
    protected void postLoad() {
        initMoveMap();
    }

    // INIT, MAPPING =====================================================================================

    public void init() {
        if (this.isEnemy()) {
            Enemy enemy = (Enemy) this;
            enemy.updateLatestTriggeredHp(100);
            enemy.updateCurrentForm(1);
        }
        this.elementType = this.getBaseActor().getElementType();
    }

    public void mapStatus(Status status) {
        this.status = status;
    }

    public Actor mapMember(Member member) {
        this.member = member;
        member.getActors().add(this);
        return this;
    }

    // MOVE =============================================================================================

    private void initMoveMap() {
        this.moveTypeMap.clear();
        this.triggerTypeMap.clear();
        for (Move move : moves) {
            addMoveToMap(move);
        }
    }

    private void addMoveToMap(Move move) {
        this.moveTypeMap.computeIfAbsent(move.getType(), k -> new ArrayList<>()).add(move);
        this.triggerTypeMap.computeIfAbsent(move.getTriggerType(), k -> new ArrayList<>()).add(move);
    }

    private void removeMoveFromMap(Move move) {
        List<Move> byType = this.moveTypeMap.get(move.getType());
        if (byType != null) byType.remove(move);
        List<Move> byTrigger = this.triggerTypeMap.get(move.getTriggerType());
        if (byTrigger != null) byTrigger.remove(move);
    }

    public void addMove(Move move) {
        this.moves.add(move);
        addMoveToMap(move);
    }

    public void addMoves(List<Move> newMoves) {
        this.moves.addAll(newMoves);
        for (Move move : newMoves) {
            addMoveToMap(move);
        }
    }

    /**
     * 매핑된 Move 를 삭제, MoveService 로 사용권장
     */
    public void removeMove(Move move) {
        if (this.moves.remove(move)) {
            removeMoveFromMap(move);
        }
    }

    /**
     * 매핑된 Move 를 삭제, MoveService 로 사용권장
     */
    public void removeMoves(List<Move> targetMoves) {
        this.moves.removeAll(targetMoves);
        for (Move move : targetMoves) {
            removeMoveFromMap(move);
        }
    }

    /**
     * 수정 불가능한 moves 반환, 수정작업은 엔티티 메서드로만 할 것
     */
    public List<Move> getMoves() {
        return Collections.unmodifiableList(this.moves);
    }

    public List<Move> getMoves(TriggerType triggerType) {
        return this.triggerTypeMap.getOrDefault(triggerType, Collections.emptyList());
    }

    public List<Move> getMoves(MoveType moveType) {
        return this.moveTypeMap.getOrDefault(moveType, Collections.emptyList());
    }

    public Move getFirstMove(MoveType moveType) {
        List<Move> moves = this.moveTypeMap.get(moveType);
        return moves == null || moves.isEmpty()
                ? null
                : moves.getFirst();
    }

    public Move getFirstMoveByLogicId(String logicId) {
        return this.moves.stream().filter(move -> move.getBaseMove().getLogicId().equals(logicId)).findFirst().orElse(null);
    }

    /**
     * (커맨드) 어빌리티만 조회
     */
    public List<Move> getAbilities() {
        return Stream.of(
                        MoveType.FIRST_ABILITY,
                        MoveType.SECOND_ABILITY,
                        MoveType.THIRD_ABILITY,
                        MoveType.FOURTH_ABILITY
                )
                .map(this::getFirstMove)
                .filter(Objects::nonNull)
                .toList();
    }

    // ========================================================================================================

    public boolean isEnemy() {
        return "BaseEnemy".equals(this.baseActor.getDtype());
    }

    public boolean isCharacter() {
        return "BaseCharacter".equals(this.baseActor.getDtype());
    }

    public void updateBaseActor(BaseActor baseActor) {
        this.name = baseActor.getName();
        this.baseActor = baseActor;
    }

    public void updateActorVisual(ActorVisual actorVisual) {
        this.actorVisual = actorVisual;
    }

    public StatusDetails getStatusDetails() {
        return this.status.getStatusDetails();
    }

    public DamageStatusDetails getDamageStatusDetails() {
        return this.status.getDamageStatusDetails();
    }

    /**
     * 로직에서 HP 를 업데이트 <br>
     * 음수값을 허용하기 때문에 데미지 로직에서는 반드시 MIN 0 으로 변경하여 업데이트 요망 <br>
     * 음수값은 특수상황에서 사용 [사망시 Integer.MIN] [Actor 엔티티 첫 생성시 -1]
     *
     * @param hp
     */
    public void updateHp(int hp) {
        this.status.setHp(hp);
    }

    public void updateChargeGauge(int chargeGauge) {
        this.status.setChargeGauge(chargeGauge);
    }

    /**
     * currentOrder 를 업데이트,
     * 캐릭터가 죽으면 +100, 후열캐릭터 등장시 해당 order 세팅해서 넘기기
     *
     * @param currentOrder
     */
    public void updateCurrentOrder(int currentOrder) {
        this.currentOrder = currentOrder;
    }

    public int getHp() {
        return this.status.getHp();
    }

    public int getMaxHp() {
        return this.status.getMaxHp();
    }

    public int getChargeGauge() {
        return this.status.getChargeGauge();
    }

    public int getMaxChargeGauge() {
        return this.status.getMaxChargeGauge();
    }

    public int getFatalChainGauge() {
        return this.status.getFatalChainGauge();
    }

    /**
     * 현재 체력 비율을 NN% 로 반환
     */
    public int getHpRateInt() {
        return this.status.getCalcedHpRateInt();
    }

    /**
     * 적이 사망했는지 확인, 사망처리 전일때만 true
     *
     * @return
     */
    public boolean isNowDead() {
        return this.status.getHp() <= 0 && !isAlreadyDead();
    }

    /**
     * 적이 사망했으며 사망처리까지 전부 종료되었을때 true
     *
     * @return
     */
    public boolean isAlreadyDead() {
        return this.status.getHp() == Integer.MIN_VALUE;
    }

    public void changeGuard(boolean isGuardOn) {
        this.isGuardOn = isGuardOn;
    }

    /**
     * 자신의 해당 턴 중 공격행동 횟수를 증가 <br>
     * 가드, 턴친행 없이 일반공격 은 이 카운트 수정 x
     */
    public void increaseExecutedStrikeCount() {
        this.executedStrikeCount++;
    }

    public void resetStrikeCount() {
        this.executedStrikeCount = 0;
    }

    /**
     * 오의 발동 가능 여부를 반환. 발동 가능시 true
     */
    public boolean canCharacterChargeAttack() {
        boolean isCharacter = this.isCharacter();
        boolean isChargeAttackOn = this.getMember().isChargeAttackOn();
        boolean readyChargeAttack = this.getChargeGauge() >= 100;
        boolean chargeAttackSealed = this.getStatus().getStatusDetails().getCalcedChargeAttackSealed();
        boolean isConditionalChargeAttackCan = this.getStatus().getStatusDetails().isConditionalChargeAttackCan(); // 조건형 오의
        return isCharacter && isChargeAttackOn && readyChargeAttack && isConditionalChargeAttackCan && !chargeAttackSealed;
    }

    public List<Integer> getAbilityCooldowns() {
        return this.getAbilities().stream()
                .map(Move::getCooldown)
                .toList();
    }

    public void updateAbilityCooldowns(int cooldown, MoveType... abilityTypes) {
        for (MoveType abilityType : abilityTypes) {
            Move ability = this.getFirstMove(abilityType);
            if (ability != null) {
                ability.updateCooldown(cooldown);
            }
        }
    }

    /**
     * 어빌리티 쿨타임 진행
     */
    public void progressAbilityCoolDown() {
        this.getAbilities().forEach(ability -> ability.updateCooldown(Math.max(0, ability.getCooldown() - 1)));
    }

    /**
     * 어빌리티 현재 턴에서의 사용횟수를 리스트로 반환
     *
     * @return
     */
    public List<Integer> getAbilityUseCounts() {
        return this.getAbilities().stream().map(Move::getCurrentTurnUseCount).toList();
    }

    /**
     * 어빌리티 사용횟수 초기화 (턴 종료시, 어빌리티 사용횟수는 감소없이 초기화만)
     */
    public void resetAbilityUseCount() {
        this.getAbilities().forEach(Move::clearCurrentTurnUseCount);
    }

    /**
     * 전체 어빌리티 봉인 여부
     *
     * @return
     */
    public List<Boolean> getAbilitySealeds() {
        return this.status.getStatusDetails().getCalcedAbilitySealedList();
    }

    /**
     * 어빌리티 봉인 여부 확인
     *
     * @param abilityType
     * @return
     */
    public boolean getAbilitySealed(MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        return this.status.getStatusDetails().getCalcedAbilitySealedList().get(abilityIndex);
    }

    /**
     * 소환석 조회 <br>
     * 슬롯 순서대로 정렬된 소환석 리스트를 반환 (비어있을 수 있음)
     */
    public List<Move> getSummons() {
        return MoveType.SUMMONS.stream() // order by ordinal
                .map(this::getFirstMove)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> getSummonCooldowns() {
        return this.getSummons().stream()
                .map(Move::getCooldown)
                .toList();
    }

    /**
     * 소환석 쿨타임 진행
     */
    public void progressSummonCoolDown() {
        this.getSummons().forEach(move -> move.updateCooldown(Math.max(0, move.getCooldown() - 1)));
    }

    /**
     * 빈 캐릭터를 반환 <br>
     * 멤버정보 출력시 주인공 없을때 스킵용으로 사용
     *
     * @return
     */
    public static Actor getTransientCharacter(Member member) {
        return Character.builder()
                .baseActor(BaseCharacter.builder().name("-").build())
                .elementType(ElementType.NONE)
                .member(member)
                .name("-")
                .id(0L)
                .currentOrder(-1)
                .build();
    }

}
