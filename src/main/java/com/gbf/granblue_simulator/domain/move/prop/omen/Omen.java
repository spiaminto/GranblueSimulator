package com.gbf.granblue_simulator.domain.move.prop.omen;

import com.gbf.granblue_simulator.domain.move.Move;
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
@Getter @EqualsAndHashCode @ToString(exclude = {"move"})
public class Omen {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 차지어택 이름 따라갈 예정, 이걸로 연결된 차지어택 찾을 예정

    @Enumerated(EnumType.STRING)
    private OmenType omenType;
    
    private String info; // 전조 설명

    @Type(ListArrayType.class)
    @Column(name = "triggerHps", columnDefinition = "integer[]")
    private List<Integer> triggerHps = new ArrayList<>(); // HP트리거와 차지어택이 의 경우 현재 ENEMY 의 HP 가 이 값보다 작은경우 발동

    @OneToMany(mappedBy = "omen")
    private List<OmenCancelCond> omenCancelConds = new ArrayList<>();

    @OneToOne @JoinColumn(name = "move_id")
    private Move move;

}
