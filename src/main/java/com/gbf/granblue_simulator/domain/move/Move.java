package com.gbf.granblue_simulator.domain.move;

import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.Character;
import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.move.prop.asset.Asset;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString(exclude = {"actor", "statuses", "asset", "omen"})
public class Move {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private Actor actor;

    @Enumerated(EnumType.STRING)
    private MoveType type;

    @OneToOne(mappedBy = "move")
    private Asset asset;

    @OneToMany(mappedBy = "move")
    private List<Status> statuses;

    @OneToOne(mappedBy = "move")
    private Omen omen;

    private String name;
    private String info;

    private Double damageRate;

    // 통상공격
    // 히트수는 배틀상태에서 결정

    // 어빌리티
    private Integer coolDown; // 쿨타임
    private String duration; // 지속시간 -> 표현만을 위한 지속시간. 쉼표로 여러개 구분
    private Integer hitCount; // 데미지 횟수
    
    // 오의
    //...

    // set character
    public void setCharacter(Actor actor) {
        this.actor = actor;
    }
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
