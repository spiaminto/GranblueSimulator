package com.gbf.granblue_simulator.battle.domain;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import io.hypersistence.utils.hibernate.type.array.IntArrayType;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일단 최소한의 필드만 두고 확장
 */
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class BattleLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roomId;

    private Integer currentTurn;

    private Long mainActorBaseId; // 행동주체 baseId
    private Long enemyActorBaseId; // 적의 baseId, 행동주체가 enemy 인경우 null

    @Enumerated(EnumType.STRING)
    private MoveType moveType;
    
    @Enumerated(EnumType.STRING)
    private MoveType parentMoveType; // 구분 편의를 위해 추가

    private Integer hitCount;

    @Type(ListArrayType.class)
    @Column(name = "damages", columnDefinition = "integer[]")
    private List<Integer> damages;

    @Type(ListArrayType.class)
    @Column(name = "effect_damages", columnDefinition = "integer[]")
    private List<Integer> effectDamages;

    @Type(ListArrayType.class)
    @Column(name = "damage_element_types", columnDefinition = "text[]")
    private List<String> damageElementTypes;

    @Type(IntArrayType.class)
    @Column(name = "additional_damages", columnDefinition = "integer[][]")
    private int[][] additionalDamages;

    @Type(ListArrayType.class)
    @Column(name = "statuses", columnDefinition = "text[]")
    private List<String> statuses;

    @Type(ListArrayType.class)
    @Column(name = "status_details", columnDefinition = "text[]")
    private List<String> statusDetails;

    @Type(ListArrayType.class)
    @Column(name = "damage_status_details", columnDefinition = "text[]")
    private List<String> damageStatusDetails;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
