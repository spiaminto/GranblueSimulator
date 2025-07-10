package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
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
@EqualsAndHashCode @ToString
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
    private Double atkWeaponRate = 12.0; // 장비항 (200, 100, 100 상정, 12배율 상정, 속성가호 계산x)
    @Builder.Default
    private Double hpWeaponRate = 1.0; // 수호항 -> 장비항이므로 100% 로 고정

    // 방어
    private Integer def; // 방어력
    @Accessors(fluent = true)
    private boolean isGuardOn;

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

    // 어빌리티 쿨다운, 각 어빌리티 1턴당 사용횟수. 나중에 분리할수도
    private Integer firstAbilityCoolDown;
    private Integer secondAbilityCoolDown;
    private Integer thirdAbilityCoolDown;
    private Integer fourthAbilityCoolDown;
    private Integer firstAbilityUseCount;
    private Integer secondAbilityUseCount;
    private Integer thirdAbilityUseCount;
    private Integer fourthAbilityUseCount;

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

    public String getdType() {
        return dtype; // lombok getter 안먹혀서 생성
    }

    public boolean isEnemy() {
        return "BattleEnemy".equals(this.dtype);
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
        this.maxHp = this.getHp(); // HP 가 먼저 세팅 되어 있어야함
        this.elementType = this.getActor().getElementType();
        this.fatalChainGauge = 0;
        this.firstAbilityCoolDown = 0;
        this.secondAbilityCoolDown = 0;
        this.thirdAbilityCoolDown = 0;
        this.fourthAbilityCoolDown = 0;
        this.firstAbilityUseCount = 0;
        this.secondAbilityUseCount = 0;
        this.thirdAbilityUseCount = 0;
        this.fourthAbilityUseCount = 0;
    }

    public void updateMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public void updateHp(int hp) {
        this.hp = hp;
    }

    public void updateChargeGauge(int chargeGauge) {
        this.chargeGauge = chargeGauge;
    }

    public void updateFatalChainGauge(int gauge) {
        this.fatalChainGauge = gauge;
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
    public Integer calcHpRate() {
        return (int) (((double) hp / maxHp) * 100);
    }

    public void toggleGuard() {
        this.isGuardOn = !this.isGuardOn;
    }

    public void increaseStrikeCount() {
        this.strikeCount++;
    }

    public void resetStrikeCount() {
        this.strikeCount = 0;
    }

    /**
     * 어빌리티의 쿨타임 update
     *
     * @param coolDown 적용할 쿨타임
     * @param abilityType 어빌리티 타입
     */
    public void updateAbilityCoolDown(int coolDown, MoveType abilityType) {
        switch (abilityType) {
            case FIRST_ABILITY:
                firstAbilityCoolDown = Math.max(0, coolDown);
                break;
            case SECOND_ABILITY:
                secondAbilityCoolDown = Math.max(0, coolDown);
                break;
            case THIRD_ABILITY:
                thirdAbilityCoolDown = Math.max(0, coolDown);
                break;
            case FOURTH_ABILITY:
                fourthAbilityCoolDown = Math.max(0, coolDown);
                break;
            default:
                throw new IllegalArgumentException("invalid ability type " + abilityType.name());
        }
    }

    /**
     * 어빌리티 쿨타임 진행
     */
    public void progressAbilityCoolDown() {
        this.firstAbilityCoolDown = Math.max(0, this.firstAbilityCoolDown - 1);
        this.secondAbilityCoolDown = Math.max(0, this.secondAbilityCoolDown - 1);
        this.thirdAbilityCoolDown = Math.max(0, this.thirdAbilityCoolDown - 1);
        this.fourthAbilityCoolDown = Math.max(0, this.fourthAbilityCoolDown - 1);
    }

}
