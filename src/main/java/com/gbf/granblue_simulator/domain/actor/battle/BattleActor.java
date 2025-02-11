package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.logic.character.CharacterLogic;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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

    private Integer atk; // 공력력
    @Builder.Default
    private Double weaponAtkUpRate = 12.0; // 장비항 (200, 100, 100 상정, 12배율 상정, 속성가호 계산x)
    private Double atkUpRate; //공인항
    private Double atkUpUniqueRate; // 별항
    private Double atkDownRate; // 공격력 다운
    private Double strengthRate; // 혼신항
    private Double jammedRate; // 배수항

    @Builder.Default
    private Integer supplementalDamage = 0; // 요다메 증가(가산)
    @Builder.Default
    private Double amplifyDamageRate = 0.0; // 요다메 UP(증산)

    @Builder.Default
    private Double damageCapRate = 0.0;

    private Integer hp; // 체력
    @Builder.Default
    private Double hpUpRate = 1.0; // 수호항 -> 장비항이므로 100% 로 고정
    private Double maxHpDownRate;

    private Integer def; // 방어력
    private Double defUpRate; // 방어항
    private Double defDownRate; // 방어력 다운
    private Double takenDamageCut; // 데미지컷
    private Integer takenDamageFixedDown; // 피격 데미지 감소
    private Double takenDamageUpRate; // 피격 데미지 증가
    private Double takenDamageDownRate; // 피격 데미지 감소
    private Integer barrier; // 베리어

    // 연공
    private Double doubleAttackUpRate;
    private Double doubleAttackDownRate;
    private Double tripleAttackUpRate;
    private Double tripleAttackDownRate;

    // 디버프 성공률 및 저항률
    private Double deBuffResistUpRate;
    private Double deBuffResistDownRate;
    private Double deBuffSuccessUpRate;
    private Double deBuffSuccessDownRate;

    // 크리티컬
    private Double criticalRate; // 크리티컬율 (증가만 있고 감소는 없음)
    @Builder.Default
    private Double criticalDamageRate = 0.5; // 크리티컬 데미지증가율
    
    private Integer maxChargeGauge; // 최대 오의 게이지
    @Builder.Default
    private Integer chargeGauge = 0; // 현재 오의게이지
    private Double chargeGaugeIncreaseUpRate; // 오의 게이지 증가율 증가
    private Double chargeGaugeIncreaseDownRate; // 오의 게이지 증가율 감소

    private Double accuracyUpRate;
    private Double accuracyDownRate;

    private Double dodgeUpRate; // 회피율 증가

    private Integer substitute; // 감싸기 (우선순위, 1 2 존재)

    // 어빌리티 쿨다운, 각 어빌리티 1턴당 사용횟수. 나중에 분리할수도
    private Integer firstAbilityCoolDown;
    private Integer firstAbilityUseCount;
    private Integer secondAbilityCoolDown;
    private Integer secondAbilityUseCount;
    private Integer thirdAbilityCoolDown;
    private Integer thirdAbilityUseCount;

    @Transient
    private CharacterLogic characterLogic;

    @OneToMany(mappedBy = "battleActor") @MapKey(name = "type")
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
        this.atkUpRate = atkUpRate;
        this.strengthRate = strengthRate;
        this.jammedRate = jammedRate;
        this.atkUpUniqueRate = atkUpUniqueRate;
        this.amplifyDamageRate = amplifyDamageRate;
        this.supplementalDamage = supplementalDamage;
    }

    public static boolean isEnemy(BattleActor battleActor) {
        return "BattleEnemy".equals(battleActor.getdType());
    }
}
