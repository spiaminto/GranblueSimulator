package com.gbf.granblue_simulator.metadata.domain.move;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
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
public class BaseMove {

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

    @OneToMany(mappedBy = "move")
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<BaseStatusEffect> baseStatusEffects = new ArrayList<>();

    @OneToOne(mappedBy = "move")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Omen omen;

    @OneToOne @JoinColumn(name = "default_visual_id")
    private EffectVisual defaultVisual;

    private String name; // 외부에 보여줄 값, 필요한경우만 설정 (nullable)
    private String info;

    @Enumerated(EnumType.STRING)
    private ElementType elementType;
    private double damageRate;
    private int damageConstant;

    // 통상공격
    private int hitCount; // 데미지 횟수 (적의 전체공격은 '적전체에게 1회 데미지' 이므로 hitCount = 1, isAllTarget 추가사용)

    // 어빌리티
    private int coolDown; // 쿨타임

    // 랜덤 스테이터스 효과 부여시 사용
    private int randomStatusCount;

    // 오의
    @Accessors(fluent = true)
    private boolean isAllTarget; // 적 전체 대상 공격인지 (보스용)

    // set character
    public void setCharacter(BaseActor baseActor) {
        this.baseActor = baseActor;
    }

    public MoveType getParentType() {
        return this.type.getParentType();
    }

    // 아이콘 이미지
    public String getIconImageSrc() {
        String ext = ".png";
        if (this.type == MoveType.SUMMON_DEFAULT) {
            return "/static/gbf/img/ab/summon-" + this.elementType.name().toLowerCase() + ext;
        }
        if (this.type == MoveType.FATAL_CHAIN_DEFAULT) {
            return "/static/gbf/img/ab/fc-" + this.elementType.name().toLowerCase() + ext;
        }
        return "/static/gbf/img/ab/" + this.id + ext;
    }

    /**
     * 타입만 설정된 무브를 생성 및 반환 <br>
     * 사용중: <br>
     * TURN_END_PROCESS, TURN_FINISH, TURN_END_HEAL, TURN_END_DAMAGE, TURN_END_CHARGE_GAUGE,
     * STRIKE_SEALED, GUARD_DEFAULT,
     * NONE
     *
     * @return
     */
    public static BaseMove getTransientMove(MoveType type) {
        return getTransientMove(type, null, null);
    }

    /**
     * referenceMove 의 정보로 transientMove 생성 및 반환 <br>
     * 사용중: <br>
     * SYNC (참전자 상태효과 적용)
     * <br>
     * CHECK Move 에서 null 나오면 확인
     *
     * @return
     */
    public static BaseMove getTransientMove(MoveType type, String moveName, EffectVisual effectVisual) {
        MotionType motionType = type == MoveType.STRIKE_SEALED ? MotionType.DAMAGE : MotionType.NONE; // 임시구현
        String name = moveName != null ? moveName : "";
        return BaseMove.builder()
                .name(name)
                .defaultVisual(effectVisual)
                .type(type)
                .baseStatusEffects(new ArrayList<>())
                .motionType(motionType)
                .omen(Omen.builder().build())
                .build();
    }

}
