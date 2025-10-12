package com.gbf.granblue_simulator.domain;

import com.gbf.granblue_simulator.domain.move.MoveType;
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

    private Long mainActorId; // 행동주체 Actor.id
    private Long targetActorId; // 타겟 id, 주로 적측을 확인하며 적의 폼에 따라 필터링도 해야되서 필요, nullable

    @Enumerated(EnumType.STRING)
    private MoveType moveType;

    private Integer hitCount;

    @Type(ListArrayType.class)
    @Column(name = "damages", columnDefinition = "integer[]")
    private List<Integer> damages;

    @Type(ListArrayType.class)
    @Column(name = "damage_element_types", columnDefinition = "text[]")
    private List<String> damageElementTypes;

    @Type(IntArrayType.class)
    @Column(name = "additional_damages", columnDefinition = "integer[][]")
    private int[][] additionalDamages;

    @Type(ListArrayType.class)
    @Column(name = "status_types", columnDefinition = "text[]")
    private List<String> statusTypes; // StatusType

    @Type(ListArrayType.class)
    @Column(name = "status_ids", columnDefinition = "bigint[]")
    private List<Long> statusIds;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
