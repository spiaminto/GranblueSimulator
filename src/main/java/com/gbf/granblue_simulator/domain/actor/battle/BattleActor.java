package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
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
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@EqualsAndHashCode
@ToString(exclude = {"member"})
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn
public class BattleActor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    // lombok getter 안먹혀서 생성
    public String getdType() {
        return dtype;
    }


    public boolean isEnemy() {
        return "BattleEnemy".equals(this.dtype);
    }

    /**
     * 적의 경우 무기공인항 및 무기수호항을 0 으로 초기화
     */
    public void clearWeaponRate() {
        this.atkWeaponRate = 0.0;
        this.hpWeaponRate = 0.0;
    }

    /**
     * 현재 체력 비율을 NN% 로 반환
     *
     * @return
     */
    public Integer calcHpRate() {
        return (int) (((double) hp / maxHp) * 100);
    }

    public void toggleGuard() {
        this.isGuardOn = !this.isGuardOn;
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
