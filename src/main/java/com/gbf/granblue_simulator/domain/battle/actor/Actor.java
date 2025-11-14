package com.gbf.granblue_simulator.domain.battle.actor;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.battle.actor.prop.Status;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.domain.base.actor.BaseCharacter;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
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

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(exclude = {"statusEffects", "status"})
@ToString(exclude = {"statusEffects", "status"})
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

    private int strikeCount; // 해당 턴 공격행동 횟수 (턴 종료시 0으로 초기화)

    @Type(ListArrayType.class)
    @Column(name = "ability_cooldowns", columnDefinition = "integer[]")
    private List<Integer> abilityCooldowns;

    @Type(ListArrayType.class)
    @Column(name = "ability_use_counts", columnDefinition = "integer[]")
    private List<Integer> abilityUseCounts;

    @Type(ListArrayType.class)
    @Column(name = "ability_usables", columnDefinition = "boolean[]")
    private List<Boolean> abilityUsables; // 어빌리티 사용가능 여부, 어빌리티 봉인등으로 true 로 변경됨 (쿨다운 관련 x)

    @Type(ListArrayType.class)
    @Column(name = "summon_move_ids", columnDefinition = "bigint[]")
    private List<Long> summonMoveIds; // 소환석 id, MC 에 귀속시켜야함, 나중에 isMC 같은거 추가해야할듯

    @Type(ListArrayType.class)
    @Column(name = "summon_cool_downs", columnDefinition = "integer[]")
    private List<Integer> summonCoolDowns; // 소환석 쿨타임, 순서 지켜서.

    // 페이탈체인 id
    private Long fatalChainMoveId;

    @OneToMany(mappedBy = "actor")
    @Builder.Default
    private List<StatusEffect> statusEffects = new ArrayList<>();

    @OneToOne(mappedBy = "actor")
    private Status status;

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "base_actor_id")
    private BaseActor baseActor;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void mapStatus(Status status) {
        this.status = status;
    }

    public void mapMember(Member member) {
        this.member = member;
        member.getActors().add(this);
    }

    public void updateBaseActor(BaseActor baseActor) {
        this.baseActor = baseActor;
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
            enemy.setLatestTriggeredHp(100);
            enemy.setCurrentForm(1);
        }
        this.elementType = this.getBaseActor().getElementType();
        this.abilityCooldowns = new ArrayList<>(List.of(0, 0, 0, 0));
        this.abilityUseCounts = new ArrayList<>(List.of(0, 0, 0, 0));
        this.abilityUsables = new ArrayList<>(List.of(true, true, true, true));
    }

    // 로직에서 hp, 게이지 업데이트
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

    public Move getMove(MoveType moveType) {
        return this.getBaseActor().getMoves().getOrDefault(moveType, Move.getTransientMove(MoveType.NONE));
    }

    /**
     * 자신의 hp 가 0 이하인경우 true
     *
     * @return
     */
    public boolean isDead() {
        return this.status.getHp() <= 0;
    }

    public void changeGuard(boolean isGuardOn) {
        this.isGuardOn = isGuardOn;
    }

    public void increaseStrikeCount() {
        this.strikeCount++;
    }

    public void resetStrikeCount() {
        this.strikeCount = 0;
    }


    public int getAbilityCooldown(MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        return this.abilityCooldowns.get(abilityIndex);
    }

    /**
     * 어빌리티의 쿨타임 update
     *
     * @param amount      증감값
     * @param abilityType 어빌리티 타입
     */
    public void modifyAbilityCooldowns(int amount, MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        this.abilityCooldowns.set(abilityIndex, Math.max(this.abilityCooldowns.get(abilityIndex) + amount, 0));
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

    public void updateAbilityUsable(boolean usable, MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        this.abilityUsables.set(abilityIndex, usable);
    }

    /**
     * 어빌리티 사용가능 여부(봉인여부) 확인
     *
     * @param abilityType
     * @return
     */
    public boolean getAbilityUsable(MoveType abilityType) {
        int abilityIndex = abilityType.getAbilityOrder() - 1;
        return this.abilityUsables.get(abilityIndex);
    }


    /**
     * 빈 캐릭터를 반환 <br>
     * <b>턴종처리</b> 에서만 사용
     *
     * @return
     */
    public static Actor getTransientCharacter(Member member) {
        return Character.builder()
                .baseActor(BaseCharacter.builder().name("transient").build())
                .member(member)
                .name("transient")
                .id(0L)
                .currentOrder(-1)
                .build();
    }

}
