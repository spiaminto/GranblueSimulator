package com.gbf.granblue_simulator.metadata.domain.move;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
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
@EqualsAndHashCode
@ToString
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private BaseActor baseActor;

    @Enumerated(EnumType.STRING)
    private MoveType type;

    @Enumerated(EnumType.STRING)
    private AbilityType abilityType;

    @Enumerated(EnumType.STRING)
    private MotionType motionType;
    private int motionCustomDuration; // 별도로 지정된 motionDuration, 0(없을시) 이면 기본 duration 사용

    @OneToMany(mappedBy = "move")
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<BaseStatusEffect> baseStatusEffects = new ArrayList<>();

    @OneToOne(mappedBy = "move")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
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

    private String iconImageSrc; // 아이콘 이미지
    private String portraitImageSrc; // 소환석 portrait 이미지로 사용

    // set character
    public void setCharacter(BaseActor baseActor) {
        this.baseActor = baseActor;
    }

    public MoveType getParentType() {
        return this.type.getParentType();
    }


    /**
     * 타입만 설정된 무브를 생성 및 반환 <br>
     * 사용중: <br>
     * TURN_END_PROCESS, TURN_FINISH, TURN_END_HEAL, TURN_END_DAMAGE, TURN_END_CHARGE_GAUGE,
     * STRIKE_SEALED, SYNC, NONE
     * <br>
     * CHECK Move 에서 null 나오면 확인
     *
     * @return
     */
    public static Move getTransientMove(MoveType type) {
        MotionType motionType = type == MoveType.STRIKE_SEALED ? MotionType.DAMAGE : MotionType.NONE; // 임시구현
        return Move.builder()
                .type(type)
                .baseStatusEffects(new ArrayList<>())
                .motionType(motionType)
                .build();
    }

}
