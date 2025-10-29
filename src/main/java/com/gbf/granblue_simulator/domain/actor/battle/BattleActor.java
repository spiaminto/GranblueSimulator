package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.Character;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.common.dto.SyncStatusDto;
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
@EqualsAndHashCode
@ToString
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn
public abstract class BattleActor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(insertable = false, updatable = false)
    private String dtype;

    private String name;
    private Integer currentOrder; // 자신의 배치 순서

    @Enumerated(EnumType.STRING)
    private ElementType elementType;

    // 공격
    private Integer atk;

    // 체력
    private Integer hp;
    private Integer maxHp;

    // 장비항 -> 나중에 장비 추가시 스테이터스로 전환 예정
    @Builder.Default
    private Double atkWeaponRate = 50.0; // 장비항 (마그나 400, 일반 100, ex 100, 속성 150 상정, 5 * 2 * 2 * 2.5 50배율 상정)
    @Builder.Default
    private Double hpWeaponRate = 3.0; // 수호항 -> 장비 수호항 3배율 상정

    // 방어
    private Integer def; // 방어력
    @Accessors(fluent = true)
    private boolean isGuardOn;
    @Accessors(fluent = true)
    private boolean canGuard;

    // 연공
    private Double doubleAttackRate;
    private Double tripleAttackRate;

    // 디버프 성공률 및 저항률
    private Double deBuffResistRate;
    private Double deBuffSuccessRate;

    // 명중회피
    private Double accuracyRate;
    private Double dodgeRate;

    // 크리티컬
    private Double criticalRate; // 크리티컬율 (증가만 있고 감소는 없음)
    private Double criticalDamageRate; // 크리티컬 데미지 배율 (합산)

    private Integer maxChargeGauge; // 최대 오의 게이지
    private Integer chargeGauge; // 현재 오의게이지
    private Double chargeGaugeIncreaseRate; // 오의 게이지 증가율

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

    // 소환석 id, MC 에 귀속시켜야함, 나중에 isMC 같은거 추가해야할듯
    @Type(ListArrayType.class)
    @Column(name = "summon_move_ids", columnDefinition = "bigint[]")
    private List<Long> summonMoveIds;

    // 소환석 쿨타임, 순서 지켜서.
    @Type(ListArrayType.class)
    @Column(name = "summon_cool_downs", columnDefinition = "integer[]")
    private List<Integer> summonCoolDowns;

    // 페이탈체인 id
    private Long fatalChainMoveId;

    // 페이탈 체인 게이지
    private Integer fatalChainGauge;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "battleActor")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BattleStatus> battleStatuses = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private Actor actor;

    public void setMember(Member member) {
        this.member = member;
        member.getBattleActors().add(this);
    }

    public void setActor(Actor actor) {
        this.actor = actor;
        this.actor.getBattleActors().add(this);
    }

//    public String getdType() {
//        return dtype; // lombok getter 안먹혀서 생성
//    }

    public boolean isEnemy() {
        return "Enemy".equals(this.actor.getDtype());
    }

    public boolean isCharacter() {
        return "Character".equals(this.actor.getDtype());
    }

    /**
     * 기타 스테이터스 초기화
     */
    public void initStatus() {
        if (this.isEnemy()) {
            this.atkWeaponRate = 0.0;
            this.hpWeaponRate = 0.0;
        }
        this.maxChargeGauge = this.getActor().getMaxChargeGauge();
        this.chargeGauge = 0;
        this.elementType = this.getActor().getElementType();
        this.fatalChainGauge = 0;
        this.abilityCooldowns = List.of(0, 0, 0, 0);
        this.abilityUseCounts = List.of(0, 0, 0, 0);
        this.abilityUsables = List.of(true, true, true, true);
    }

    public void updateMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    /**
     * 체력 값을 업데이트, maxHP 보다 많은 값이 전달될경우 maxHP 로 설정
     *
     * @param hp
     */
    public void updateHp(int hp) {
        this.hp = Math.min(this.maxHp, hp);
    }

    public void updateChargeGauge(int chargeGauge) {
        this.chargeGauge = chargeGauge;
    }

    public void updateFatalChainGauge(int gauge) {
        this.fatalChainGauge = gauge;
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

    public void syncStatus(SyncStatusDto dto) {
        this.atk = dto.getAtk();
        this.def = dto.getDef();
        this.doubleAttackRate = dto.getDoubleAttackRate();
        this.tripleAttackRate = dto.getTripleAttackRate();
        this.accuracyRate = dto.getAccuracyRate();
        this.dodgeRate = dto.getDodgeRate();
        this.deBuffSuccessRate = dto.getDeBuffSuccessRate();
        this.deBuffResistRate = dto.getDeBuffResistRate();
        this.criticalRate = dto.getCriticalRate();
        this.criticalDamageRate = dto.getCriticalDamageRate();
        this.chargeGaugeIncreaseRate = dto.getChargeGaugeIncreaseRate();
    }

    /**
     * 현재 체력 비율을 NN% 로 반환
     */
    public Integer getHpRate() {
        return (int) Math.ceil(((double) hp / maxHp) * 100);
    }

    /**
     * 자신의 hp 가 0 이하인경우 true
     *
     * @return
     */
    public boolean isDead() {
        return this.hp <= 0;
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

    public Move getMove(MoveType moveType) {
        return this.getActor().getMoves().getOrDefault(moveType, Move.getTransientMove(MoveType.NONE));
    }

    /**
     * 어빌리티 사용 횟수 확인
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
    public static BattleActor getTransientCharacter(Member member) {
        return BattleCharacter.builder()
                .actor(Character.builder().name("transient").build())
                .member(member)
                .name("transient")
                .id(0L)
                .currentOrder(-1)
                .build();
    }

}
