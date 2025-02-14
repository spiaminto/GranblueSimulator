package com.gbf.granblue_simulator.domain;

import com.gbf.granblue_simulator.domain.move.MoveType;
import io.hypersistence.utils.hibernate.type.array.IntArrayType;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

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

    private Long mainActorId; // 행동주체

    private Long targetActorId; // 행동 타겟

    private MoveType moveType;

    private Integer hitCount;

    @Type(ListArrayType.class)
    @Column(name = "damages", columnDefinition = "integer[]")
    private List<Integer> damages;

    @Type(IntArrayType.class)
    @Column(name = "additional_damages", columnDefinition = "integer[][]")
    private Integer[][] additionalDamages;

    @Type(ListArrayType.class)
    @Column(name = "status_types", columnDefinition = "text[]")
    private List<String> statusTypes; // StatusType

    @Type(ListArrayType.class)
    @Column(name = "status_effect_types", columnDefinition = "text[]")
    private List<String> statusEffectTypes; // StatusEffectType

}
