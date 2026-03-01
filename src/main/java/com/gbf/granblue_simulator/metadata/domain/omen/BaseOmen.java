package com.gbf.granblue_simulator.metadata.domain.omen;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

// 전조
@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString(exclude = {"enemy"})
public class BaseOmen {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private MoveType standbyType; // 스탠바이 타입

    @Enumerated(EnumType.STRING)
    private MotionType motionType;

    @Enumerated(EnumType.STRING)
    private OmenType omenType;

    private boolean isTriggerPrimary; // INCANT_ATTACK 이 우선인경우 사용
    
    private String info; // 전조 설명

    private Integer cancelConditionCount; // 해제조건 갯수

    @Type(ListArrayType.class)
    @Column(name = "triggerHps", columnDefinition = "integer[]")
    private List<Integer> triggerHps = new ArrayList<>(); // HP트리거와 차지어택이 의 경우 현재 ENEMY 의 HP 가 이 값보다 작은경우 발동

    @OneToMany(mappedBy = "omen")
    private List<OmenCancelCond> omenCancelConds = new ArrayList<>();

    @ManyToOne @JoinColumn(name = "base_enemy_id")
    private BaseEnemy enemy;

}
