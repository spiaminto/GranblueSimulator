package com.gbf.granblue_simulator.metadata.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
@Inheritance(strategy = InheritanceType.JOINED) @DiscriminatorColumn
public abstract class BaseActor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(insertable = false, updatable = false)
    private String dtype;

    @OneToMany(mappedBy = "baseActor") @MapKey(name = "type") @ToString.Exclude @EqualsAndHashCode.Exclude
    private Map<MoveType, Move> moves = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private ElementType elementType; // 속성

    private String name;
    private String nameEn; // 영어명

    @Accessors(fluent = true)
    private boolean isLeaderCharacter; // 주인공 여부, Character 로 분리?

    private String battlePortraitSrc;
    private String weaponId;

    // base status
    private int atk;
    private int maxHp;
    private double def; // .1f
    private double doubleAttackRate;
    private double tripleAttackRate;
    private double debuffResistRate;
    private double debuffSuccessRate;
    private double criticalRate;
    private double criticalDamageRate;
    private int maxChargeGauge;
    private double chargeGaugeIncreaseRate;
    private double accuracyRate;
    private double dodgeRate;

    public boolean isEnemy() {
        return "BaseEnemy".equals(this.getDtype());
    }

    public boolean isCharacter() {
        return "BaseCharacter".equals(this.getDtype());
    }

    // TODO insert 관련 추가 수정필요
    public void initCharacterBaseStatus() {
        this.atk = 10000;
        this.maxHp = 20000;
        this.def = 2.0;
        this.doubleAttackRate = 0.25;
        this.tripleAttackRate = 0.1;
        this.debuffResistRate = 0;
        this.debuffSuccessRate = 1.0;
        this.criticalRate = 0.0;
        this.criticalDamageRate = 0.5;
        this.maxChargeGauge = 100;
        this.chargeGaugeIncreaseRate = 0.0;
        this.accuracyRate = 1.0;
        this.dodgeRate = 0;
    }
    
    public void initEnemyBaseStatus() {
        // 적은 DB 에딧으로 대체
    }


}
