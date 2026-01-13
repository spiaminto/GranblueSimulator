package com.gbf.granblue_simulator.battle.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(exclude = {"statusEffects", "status"})
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
    @Accessors(fluent = true)
    private boolean canGuard;

    private Integer substitute; // 감싸기 (우선순위, 1 2 존재)

    private int executedStrikeCount; // 해당 턴 공격행동 횟수 (턴 종료시 0으로 초기화)

    @Type(ListArrayType.class)
    @Column(name = "ability_cooldowns", columnDefinition = "integer[]")
    private List<Integer> abilityCooldowns;

    @Type(ListArrayType.class)
    @Column(name = "ability_use_counts", columnDefinition = "integer[]")
    private List<Integer> abilityUseCounts;

    @Type(ListArrayType.class)
    @Column(name = "summon_move_ids", columnDefinition = "bigint[]")
    private List<Long> summonMoveIds; // 소환석 id, MC 에 귀속시켜야함, 나중에 isMC 같은거 추가해야할듯

    @Type(ListArrayType.class)
    @Column(name = "summon_cool_downs", columnDefinition = "integer[]")
    private List<Integer> summonCoolDowns; // 소환석 쿨타임, 순서 지켜서, 소환금지 상태효과는 구현하지 않을 예정

    // 페이탈체인 id
    private Long fatalChainMoveId;

    @OneToMany(mappedBy = "actor")
    @Builder.Default
    @ToString.Exclude
    private List<StatusEffect> statusEffects = new ArrayList<>();

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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Transient
    private MoveType commandType; // 커맨드로 수행하는 어빌리티의 moveType

    @ToString.Include(name = "statusEffects") // 출력될 이름 지정
    public List<String> toStringStatusEffects() {
        if (statusEffects == null) return null;
        return statusEffects.stream()
                .map(statusEffect -> statusEffect.getBaseStatusEffect().getName() + "(" + statusEffect.getDuration() + "턴)")
                .collect(Collectors.toList());
    }

    public void mapStatus(Status status) {
        this.status = status;
    }

    public void mapMember(Member member) {
        this.member = member;
        member.getActors().add(this);
    }

    public void updateBaseActor(BaseActor baseActor) {
        this.name = baseActor.getName();
        this.baseActor = baseActor;
    }

    public void updateActorVisual(ActorVisual actorVisual) {
        this.actorVisual = actorVisual;
    }

//    public String getdType() {
//        return dtype; // lombok getter 안먹혀서 생성
//    }

    public boolean isEnemy() {
        return "BaseEnemy".equals(this.baseActor.getDtype());
    }

    public boolean isCharacter() {
        return "BaseCharacter".equals(this.baseActor.getDtype());
    }

    public void init() {
        if (this.isEnemy()) {
            Enemy enemy = (Enemy) this;
            enemy.updateLatestTriggeredHp(100);
            enemy.updateCurrentForm(1);
        }
        this.elementType = this.getBaseActor().getElementType();
        this.abilityCooldowns = new ArrayList<>(List.of(0, 0, 0, 0));
        this.abilityUseCounts = new ArrayList<>(List.of(0, 0, 0, 0));
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

    public void updateFatalChainGauge(int gauge) {
        this.status.setFatalChainGauge(gauge);
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
    public int getHpRate() {
        return this.status.getCalcedHpRateInt();
    }

    /**
     * 자신의 Move 반환
     *
     * @param moveType
     * @return 없으면 MoveType.NONE 반환
     */
    public Move getMove(MoveType moveType) {
        return this.getBaseActor().getMoves().getOrDefault(moveType, Move.getTransientMove(MoveType.NONE));
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
     * 자신의 현재 공격행동 횟수를 증가 <br>
     * 가드, 턴친행 없이 일반공격 은 이 카운트 수정 x
     */
    public void increaseExecutedStrikeCount() {
        this.executedStrikeCount++;
    }

    public void resetStrikeCount() {
        this.executedStrikeCount = 0;
    }


    public int getAbilityCooldown(MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        return this.abilityCooldowns.get(abilityIndex);
    }

    public void updateAbilityCooldowns(int cooldown, MoveType... abilityTypes) {
        for (MoveType abilityType : abilityTypes) {
            int abilityIndex = abilityType.getAbilityOrder() - 1;
            this.abilityCooldowns.set(abilityIndex, Math.max(cooldown, 0));
        }
    }

    /**
     * 어빌리티의 쿨타임 update
     *
     * @param amount       증감값
     * @param abilityTypes 어빌리티 타입
     */
    public void modifyAbilityCooldowns(int amount, MoveType... abilityTypes) {
        for (MoveType abilityType : abilityTypes) {
            int abilityIndex = abilityType.getAbilityOrder() - 1;
            this.abilityCooldowns.set(abilityIndex, Math.max(this.abilityCooldowns.get(abilityIndex) + amount, 0));
        }
    }

    /**
     * 어빌리티 쿨타임 진행
     */
    public void progressAbilityCoolDown() {
        this.abilityCooldowns.replaceAll(coolDown -> Math.max(0, coolDown - 1));
    }

    /**
     * 어빌리티 사용 횟수 확인
     *
     * @param abilityType
     * @return
     */
    public int getAbilityUseCount(MoveType abilityType) {
        int abilityOrder = abilityType.getAbilityOrder();
        return this.abilityUseCounts.get(abilityOrder - 1);
    }

    /**
     * 어빌리티의 사용횟수 증가
     *
     * @param abilityType 어빌리티 타입
     */
    public int increaseAbilityUseCount(MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        this.abilityUseCounts.set(abilityIndex, this.abilityUseCounts.get(abilityIndex) + 1);
        return this.abilityUseCounts.get(abilityIndex);
    }

    /**
     * 어빌리티 사용횟수 초기화 (턴 종료시, 어빌리티 사용횟수는 감소없이 초기화만)
     */
    public void resetAbilityUseCount() {
        this.abilityUseCounts = List.of(0, 0, 0, 0);
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
     * 소환석 쿨타임 적용
     *
     * @param coolDown
     * @param summonIndexes
     */
    public void updateSummonCoolDown(int coolDown, int... summonIndexes) {
        for (int summonIndex : summonIndexes) {
            this.summonCoolDowns.set(summonIndex, coolDown);
        }
    }

    /**
     * 소환석 쿨타임 진행
     */
    public void progressSummonCoolDown() {
        this.summonCoolDowns.replaceAll(coolDown -> Math.max(0, coolDown - 1));
    }

    /**
     * 커맨드로 수행하는 moveType 을 transient 로 설정 <br>
     * 현재 어빌리티만 설정중
     * @param moveType
     */
    public void updateCommandType(MoveType moveType) {
        this.commandType = moveType;
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
