package com.gbf.granblue_simulator.domain.actor;

import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
@Inheritance(strategy = InheritanceType.JOINED) @DiscriminatorColumn
public abstract class Actor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(insertable = false, updatable = false)
    private String dtype;

    private String name;
    private String nameEn; // 영어명
    @Enumerated(EnumType.STRING)
    private ElementType elementType; // 속성

    @Accessors(fluent = true)
    private boolean isMainCharacter; // 주인공 여부, Character 로 분리?

    @Builder.Default
    private Integer baseAttackPoint = 1000; // 공력력
    @Builder.Default
    private Integer baseHitPoint = 10000; // 체력
    @Builder.Default
    private Integer baseDefencePoint = 10; // 방어력

    @Builder.Default
    private Double baseDoubleAttackRate = 0.25; // 더블어택율
    @Builder.Default
    private Double baseTripleAttackRate = 0.1; // 트리플어택율

    @Builder.Default
    private Double baseDeBuffResistRate = 0.1; // 디버프 저항력
    @Builder.Default
    private Double baseDeBuffSuccessRate = 1.0; // 디버프 성공률

    @Builder.Default
    private Double baseCriticalRate = 0.0; // 크리티컬율
    @Builder.Default
    private Double baseCriticalDamageRate = 0.5; // 크리티컬 데미지 증가율

    @Builder.Default
    private Integer maxChargeGauge = 100; // 최대 오의 게이지 or 차지 턴
    @Builder.Default
    private Double baseChargeGaugeIncreaseRate = 0.0; // 오의 게이지 증가율

    @Builder.Default
    private Double baseAccuracy = 1.0; // 명중률
    @Builder.Default
    private Double baseDodgeRate = 0.01; // 회피율

    @OneToMany(mappedBy = "actor") @MapKey(name = "type") @ToString.Exclude @EqualsAndHashCode.Exclude
    private Map<MoveType, Move> moves = new HashMap<>();

    @OneToMany(mappedBy = "actor") @Builder.Default  @ToString.Exclude @EqualsAndHashCode.Exclude
    private List<BattleActor> battleActors = new ArrayList<>();

    // Asset
    private String battlePortraitSrc;
}
