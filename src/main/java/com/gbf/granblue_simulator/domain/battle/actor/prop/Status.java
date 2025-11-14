package com.gbf.granblue_simulator.domain.battle.actor.prop;

import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "actor_id")
    private Actor actor;

    @Transient
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private StatusDetails statusDetails;

    @Transient
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DamageStatusDetails damageStatusDetails;

    // 공격
    private int atk;

    // 방어력 (소숫점 1째짜리 까지만 허용, 아래 버림)
    private double def;

    // 최대체력
    private int maxHp;

    // 현재 체력
    private int hp;

    private double doubleAttackRate;
    private double tripleAttackRate;

    private double debuffSuccessRate;
    private double debuffResistRate;

    private double accuracyRate;
    private double dodgeRate;

    private double criticalRate;
    private double criticalDamageRate;

    private double chargeGaugeIncreaseRate;

    private int chargeGauge;
    private int maxChargeGauge;
    private int fatalChainGauge;


    /**
     * base 를 기준으로 엔티티 생성시 초기값 저장
     */
    public void init(Actor actor) {
        BaseActor baseActor = actor.getBaseActor();

        this.maxChargeGauge = baseActor.getMaxChargeGauge();
        this.chargeGauge = 0;
        this.fatalChainGauge = 0;
        this.hp = Integer.MIN_VALUE; // hp 선 계산을 위해 지정

        this.actor = actor;
        actor.mapStatus(this);

        this.syncStatus(); // base 만 저장
    }

    /**
     * 체력을 변경 : 로직에서 별도로 변경
     *
     * @param hp
     */
    public void setHp(int hp) {
        this.hp = Math.min(hp, this.maxHp);
    }

    public double getCalcedHpRate() {
        return (double) hp / maxHp;
    }

    public int getCalcedHpRateInt() {
        return (int) Math.ceil(getCalcedHpRate() * 100);
    }

    public void setChargeGauge(int chargeGauge) {
        this.chargeGauge = Math.min(chargeGauge, this.maxChargeGauge);
    }

    public void setFatalChainGauge(int fatalChainGauge) {
        this.fatalChainGauge = Math.min(fatalChainGauge, 100);
    }

    /**
     * 스테이터스 갱신 (재계산)
     */
    public void syncStatus() {
        // 1. 스테이터스 상세 초기화 및 동기화
        if (this.statusDetails == null) {
            this.statusDetails = StatusDetails.init(actor);
            if (this.hp == Integer.MIN_VALUE)
                this.hp = this.statusDetails.getCalcedMaxHp(); // 엔티티 첫 생성의 경우 HP 를 선 초기화
        }
        this.statusDetails.syncStatusDetails(actor);

        // 2. 데미지 스테이터스 상세 초기화 및 동기화
        getSyncDamageStatus();

        // 3. 최종 스테이터스 동기화
        StatusDetails details = this.statusDetails;

        this.maxHp = details.getCalcedMaxHp();
        if (maxHp > 0 && maxHp < hp) this.hp = maxHp; // 혼신, 배수 처리를 위해 HP 를 최우선 처리

        this.atk = details.getCalcedAtk(this.getCalcedHpRate());
        this.def = details.getCalcedDef();

        this.doubleAttackRate = details.getCalcedDoubleAttackRate();
        this.tripleAttackRate = details.getCalcedTripleAttackRate();

        this.debuffSuccessRate = details.getCalcedDebuffSuccessRate();
        this.debuffResistRate = details.getCalcedDebuffResistRate();

        this.accuracyRate = details.getCalcedAccuracyRate();
        this.dodgeRate = details.getCalcedDodgeRate();

        this.criticalRate = details.getCalcedCriticalRate();
        this.criticalDamageRate = details.getCalcedCriticalDamageRate();

        this.chargeGaugeIncreaseRate = details.getCalcedChargeGaugeIncreaseRate();
    }

    /**
     * 요다메 관련 초기화 및 동기화 처리 (데미지로직에서 같이 사용)
     */
    public DamageStatusDetails getSyncDamageStatus() {
        if (this.damageStatusDetails == null)
            this.damageStatusDetails = DamageStatusDetails.init(actor);
        this.damageStatusDetails.syncDamageStatusDetails(actor);
        return this.damageStatusDetails;
    }
}
