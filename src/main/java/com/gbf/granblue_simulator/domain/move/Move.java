package com.gbf.granblue_simulator.domain.move;

import com.gbf.granblue_simulator.domain.entity.Character;
import com.gbf.granblue_simulator.domain.entity.Enemy;
import com.gbf.granblue_simulator.domain.move.prop.Asset;
import com.gbf.granblue_simulator.domain.move.prop.Omen;
import com.gbf.granblue_simulator.domain.move.prop.Status;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class Move {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "character_id")
    private Character character;

    @ManyToOne @JoinColumn(name = "enemy_id")
    private Enemy enemy;

    @Enumerated(EnumType.STRING)
    private MoveType moveType;

    @OneToOne
    private Asset asset;

    @OneToOne
    private Status status;

    @OneToOne
    private Omen omen;
    
    // 기타속성
    
    // 데미지 있을시
    private Double damageRate; // 어빌리티 및 오의 데미지 배율
    // 데미지는 배틀상태에서 결정

    // 통상공격
    // 히트수는 배틀상태에서 결정

    // 어빌리티
    private Integer coolDown; // 쿨타임
    private Integer duration; // 지속시간 -> 가지고 있는 status 중 가장 긴녀석을 따라감
    
    // 오의
    //...
}

/*

 기본적으로 캐릭터 또는 적이 가능한 행동을 기준으로 생성.
 가능한 행동이란 모션이 붙어있는것을 의미 (공격, 어빌리티, 피격 등)
 필요한경우 분리하겠으나, 일단 합쳐서 구현.
 
 또 기본적으로 Move 객체는 Immutable.
 만들어 배틀상태에서 변화하는 부분은 
    1. 별도의 Move 로 생성
        ex) 조건부로 효과가 변하는 경우 FIRST_ABILITY -> FIRST_ABILITY_CHANGE 로 변경
        배율이 변화하거나 후행동이 변화하거나 하는부분을 전부 CHANGED 로 변경하도록 한다.


 */
