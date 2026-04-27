package com.gbf.granblue_simulator.battle.domain;

import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultStatusDto;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import io.hypersistence.utils.hibernate.type.array.IntArrayType;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 일단 최소한의 필드만 두고 확장
 */
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
@Immutable // 로그는 변경 x (JSON 더티체킹 방지 목적도 잇음)
public class BattleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    private List<Integer> damages; // 명중률을 통해 회피한경우 음수 (-1) 가능

    @Type(ListArrayType.class)
    @Column(name = "effect_damages", columnDefinition = "integer[]")
    private List<Integer> effectDamages;

    @Type(ListArrayType.class)
    @Column(name = "damage_element_types", columnDefinition = "text[]")
    private List<String> damageElementTypes;

    @Type(IntArrayType.class)
    @Column(name = "additional_damages", columnDefinition = "integer[][]")
    private int[][] additionalDamages;

    @CreationTimestamp
    private LocalDateTime createdAt;


    // 로깅용, 순서 유지를 위해 jsonb 대신 json, LAZY
    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("json_details")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "statuses", columnDefinition = "json")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Map<Long, ResultStatusDto> statuses = new LinkedHashMap<>();

    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("json_details")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "status_details", columnDefinition = "json")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Map<Long, StatusDetails> statusDetails = new LinkedHashMap<>();

    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("json_details")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "damage_status_details", columnDefinition = "json")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Map<Long, DamageStatusDetails> damageStatusDetails = new LinkedHashMap<>();

}
