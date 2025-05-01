package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(insertable = false, updatable = false)
    private String dtype;
    private Integer currentOrder; // 자신의 자리 순서 Party 로 부터받아 처리할 예정
    @Enumerated(EnumType.STRING)
    private ElementType elementType;

    // 공격
    private Integer atk;
    @Builder.Default
    private Double atkWeaponRate = 12.0; // 장비항 (200, 100, 100 상정, 12배율 상정, 속성가호 계산x)
    private Double atkRate; // 공인항
    private Double atkUniqueRate; // 별항
    private Double strengthRate; // 혼신항
    private Double jammedRate; // 배수항

    private Integer supplementalDamagePoint; // 공격 데미지 상승 (가산)
    private Double amplifyDamageRate; // 공격 데미지 업 (승산)

    private Double damageCapRate; // 데미지 상한

    // 체력
    private Integer hp;
    private Integer maxHp;
    @Builder.Default
    private Double hpWeaponRate = 1.0; // 수호항 -> 장비항이므로 100% 로 고정
    private Double maxHpRate; // 최대 HP 배율

    // 방어
    private Integer def; // 방어력
    private Double defRate; // 방어력 배율
    private Double takenDamageCutRate; // 데미지컷 배율

    private Integer takenSupplementalDamagePoint; // 피격 데미지 상승, 감소 (가산)
    private Double takenAmplifyDamageRate; // 피격 데미지 업, 다운 (승산)

    private Double takenAttackAmplifyDamageRate; // 받는 통상공격 데미지 배율
    private Double takenAbilityAmplifyDamageRate; // 받는 어빌리티 데미지 배율
    private Double takenChargeAttackAmplifyDamageRate; // 받는 오의 / 특수기 데미지 배율

    private Integer barrier; // 베리어

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
    private Integer firstAbilityUseCount;
    private Integer secondAbilityCoolDown;
    private Integer secondAbilityUseCount;
    private Integer thirdAbilityCoolDown;
    private Integer thirdAbilityUseCount;

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

    /**
     * 테스트 용
     */
    public void setAtkValues(int atk, double atkUpRate, double strengthRate, double jammedRate, double atkUpUniqueRate, double amplifyDamageRate, int supplementalDamage) {
        this.atk = atk;
        this.strengthRate = strengthRate;
        this.jammedRate = jammedRate;
        this.amplifyDamageRate = amplifyDamageRate;
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

    /**
     * 어빌리티 쿨타임 진행
     */
    public void progressAbilityCoolDown() {
        this.firstAbilityCoolDown = Math.max(0, this.firstAbilityCoolDown - 1);
        this.secondAbilityCoolDown = Math.max(0, this.secondAbilityCoolDown - 1);
        this.thirdAbilityCoolDown = Math.max(0, this.thirdAbilityCoolDown - 1);
    }

}
