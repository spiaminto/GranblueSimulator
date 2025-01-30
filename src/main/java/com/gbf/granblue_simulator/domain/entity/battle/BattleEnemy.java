package com.gbf.granblue_simulator.domain.entity.battle;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.domain.move.Move;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode @ToString
public class BattleEnemy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private List<Move> moves = new ArrayList<>();

    @ManyToOne @JoinColumn(name = "member_id")
    private Member member;

    private String name;

    private Integer attackPoint; // 공력력
    private Integer hitPoint; // 체력
    private Integer defensePoint; // 방어력

    private Double doubleAttackRate; // 더블어택율
    private Double tripleAttackRate; // 트리플어택율

    private Double deBuffResistRate; // 디버프 저항력
    private Double deBuffSuccessRate; // 디버프 성공률

    private Double criticalRate; // 크리티컬율
    private Double criticalDamageRate; // 크리티컬 데미지율

    private Double hitRate; // 명중률
    private Double dodgeRate; // 회피율

    // 보스 기믹 관련 속성
    // ...

}
