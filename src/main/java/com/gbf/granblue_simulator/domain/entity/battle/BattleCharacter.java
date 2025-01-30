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
public class BattleCharacter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient // 주석 1, Load 필수
    private List<Move> moves = new ArrayList<>();

    @ManyToOne @JoinColumn(name = "memberId")
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

    private Integer maxChargeGauge; // 최대 오의 게이지
    private Double chargeGaugeIncreaseRate; // 오의 게이지 증가율
    private Double chargeAttackDamageRate; // 오의 데미지 배율

    private Double hitRate; // 명중률
    private Double dodgeRate; // 회피율
}

// 주석 1
    /*
    moves 는 BattleCharacter 를 구성할때 Character 로부터 받아온 정보로 구성한다.
    Move 객체 자체는 Immutable 하므로 Transient 를 통해 의존관계 없이 데이터만 받아와 저장한다.
    moves 를 의존관계로 바꾸면 Move 객체 내부에 BattleCharcter 과 매핑이 더 필요하므로 이 단계에서 끊는게 맞다.
    moves 에 move 추가할때 반드시 LazyLoading 한 뒤에 추가하도록.
     */
