package com.gbf.granblue_simulator.metadata.domain.move;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;

import java.util.*;
import java.util.stream.Collectors;

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

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MoveType type = MoveType.NONE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TriggerType triggerType = TriggerType.NONE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TriggerPhase triggerPhase = TriggerPhase.NONE;

    @Enumerated(EnumType.STRING)
    private AbilityType abilityType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MotionType motionType = MotionType.NONE;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<TrackingCondition, Object> conditionTracker = new HashMap<>();

    @OneToMany(mappedBy = "move")
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<BaseStatusEffect> baseStatusEffects = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "default_visual_id")
    private EffectVisual defaultVisual; // 이펙트가 있는 어빌리티, 고유 이펙트가 있는 캐릭터 오의 에서 사용

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

    // 오의
    @Accessors(fluent = true)
    private boolean isAllTarget; // 적 전체 대상 공격인지 (보스용)

    private String logicId;

    /*
    // CHECK DB 컬럼은 유지중
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    @ToString.Exclude
    private BaseActor baseActor;
     */

    public MoveType getParentType() {
        return this.type.getParentType();
    }

    /**
     * applyOrder 를 key 로 하는 맵 반환, 일반적으로 조건에 따라 적용되는 효과가 다를시 사용
     */
    public Map<Integer, List<BaseStatusEffect>> getEffectsGroupByApplyOrder() {
        return this.baseStatusEffects.stream().collect(Collectors.groupingBy(BaseStatusEffect::getApplyOrder)); // value 는 mutable list
    }

    /**
     * applyOrder + id 로 정렬하여 반환
     */
    public List<BaseStatusEffect> getOrderedBaseStatusEffects() {
        return this.baseStatusEffects.stream()
                .filter(baseStatusEffect -> baseStatusEffect.getType() != StatusEffectType.PASSIVE)
                .sorted(Comparator.comparing(BaseStatusEffect::getApplyOrder).thenComparing(BaseStatusEffect::getId))
                .toList();
    }

    // 아이콘 이미지
    public String getIconImageSrc() {
        String ext = ".png";
        if (this.type == MoveType.SUMMON) {
            return "/static/gbf/img/ab/summon-" + this.elementType.name().toLowerCase() + ext;
        }
        if (this.type == MoveType.FATAL_CHAIN_DEFAULT) {
            return "/static/gbf/img/ab/fc-" + this.elementType.name().toLowerCase() + ext;
        }
        if (this.type == MoveType.ABILITY) {
            return "/static/gbf/img/ab/" + this.id + ext;
        }
        return ""; // 나머지는 비워서 반환
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
        MotionType motionType = type == MoveType.STRIKE_SEALED ? MotionType.DAMAGE : MotionType.NONE; // 임시구현
        return BaseMove.builder()
                .name(type.name())
                .defaultVisual(null)
                .type(type)
                .baseStatusEffects(new ArrayList<>())
                .motionType(motionType)
                .build();
    }


}
