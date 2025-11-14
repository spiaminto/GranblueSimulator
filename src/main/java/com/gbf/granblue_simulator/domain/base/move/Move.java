package com.gbf.granblue_simulator.domain.base.move;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.domain.base.omen.Omen;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class Move {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private BaseActor baseActor;

    @Enumerated(EnumType.STRING)
    private MoveType type;

    @Enumerated(EnumType.STRING)
    private MotionType motionType;
    private int motionSkipDuration; // 모션 스킵시 사용할 duration [FPS], 0: 스킵 x, 15~ : 스킵

    @OneToMany(mappedBy = "move") @Builder.Default  @EqualsAndHashCode.Exclude @ToString.Exclude
    private List<BaseStatusEffect> statusEffects = new ArrayList<>();

    @OneToOne(mappedBy = "move")  @EqualsAndHashCode.Exclude @ToString.Exclude
    private Omen omen;

    private String name; // 외부에 보여줄 값, 필요한경우만 설정 (nullable)
    private String info;

    @Enumerated(EnumType.STRING)
    private ElementType elementType;
    private double damageRate;
    private int damageConstant;

    // 통상공격
    // 히트수는 배틀상태에서 결정

    private int hitCount; // 데미지 횟수 (적의 전체공격은 '적전체에게 1회 데미지' 이므로 hitCount = 1, isAllTarget 추가사용)

    // 어빌리티
    private int coolDown; // 쿨타임

    // 랜덤 스테이터스 효과 부여시 사용
    private int randomStatusCount;
    
    // 오의
    @Accessors(fluent = true)
    private boolean isAllTarget; // 적 전체 대상 공격인지 (보스용)

    private String iconImageSrc; // 어빌리티 아이콘, 소환석 portrait

    // set character
    public void setCharacter(BaseActor baseActor) {
        this.baseActor = baseActor;
    }

//    public List<BaseStatusEffect> getStatuses() {
//        return this.statusEffects;
//    }

    /**
     * 타입만 설정된 무브를 생성 및 반환
     * TURN_END_PROCESS, STRIKE_SEALED, SYNC, NONE 외 사용금지
     * CHECK Move 에서 null 나오면 확인
     * @return
     */
    public static Move getTransientMove(MoveType type) {
        MotionType motionType = type == MoveType.STRIKE_SEALED ? MotionType.DAMAGE : MotionType.NONE; // 임시구현
        return Move.builder()
                .type(type)
                .statusEffects(new ArrayList<>())
                .motionType(motionType)
                .build();
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
