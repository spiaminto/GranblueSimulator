package com.gbf.granblue_simulator.metadata.domain.statuseffect;

import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
/**
 * 온전히 표현을 위해사용
 * 값과 계산은 캐릭터 로직에서 담당
 */
public class BaseStatusEffect {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "move_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private BaseMove move;

    private String name; // 일단 effectText 와 동일하게 사용

    @Builder.Default
    private Integer maxLevel = 0; // 최고 레벨

    @Enumerated(EnumType.STRING)
    private StatusEffectType type;

    @Enumerated(EnumType.STRING)
    private StatusEffectTargetType targetType;

    @Enumerated(EnumType.STRING)
    private StatusDurationType durationType; // 효과 시간 타입 [턴, 시간, 레벨]
    private Integer duration; // 효과시간 턴[턴], 시간[초]

    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트
    private int displayPriority; // 우선순위 [기본 0] [영속 해제불가 디버프 8] [영속 해제불가 자버프 10] [적 고유버프 100]
    private int applyOrder; // 적용순서, 필요한경우 1부터 시작

    private boolean removable; // 소거불가, 해제불가, 회복불가
    private boolean resistible; // 필중인지 확인
    private boolean uniqueFrame; // 고유항을 가지는지 여부
    private boolean conditionalModifier; // 레벨에 비례해 효과

    @OneToMany(mappedBy = "baseStatusEffect")
    @MapKey(name = "type")
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Getter(AccessLevel.NONE) // getBaseModifiers 사용
    Map<StatusModifierType, StatusModifier> statusModifiers = new LinkedHashMap<>();

    private String gid; // 리소스 참조용 gbf id

    public void setMove(BaseMove move) {
        this.move = move;
        move.getBaseStatusEffects().add(this);
    }

    /**
     * 외부에 보여줄 효과인지 여부, 버프/디버프 만 true
     */
    public boolean isDisplayable() {
        return this.type.isDisplayable();
    }

    /**
     * 메타데이터로 보여줄 효과인지 여부, 버프/디버프/DISPLAY_PASSIVE 포함
     */
    public boolean isMetadataDisplayable() {
        return this.type.isMetadataDisplayable();
    }

    /**
     * gid 를 이용하여 실제 아이콘 src 반환
     *
     * @return
     */
    public List<String> getIconSrcs() {
        if (!StringUtils.hasText(this.gid)) return new ArrayList<>();
        String prefix = "/static/gbf/img/status/";
        String baseName = prefix + this.gid;
        String ext = ".png";

        List<String> iconSrcs = new ArrayList<>();
        if (this.maxLevel > 0) {
            iconSrcs = IntStream.range(0, this.maxLevel)
                    .mapToObj(index -> prefix + this.gid + "_" + (index + 1) + ext)
                    .toList();
        } else if (this.getTargetType() == StatusEffectTargetType.ENEMY
                && this.durationType == StatusDurationType.TURN
                && this.duration > 0) {
            // 레벨제가 아님 + 적(개인) 타겟 + 턴제(즉발아님) -> 남은 턴 기준으로 바뀌는 여러개의 아이콘을 가짐 (명명법은 레벨제와 동일)
            iconSrcs = IntStream.range(0, this.duration)
                    .mapToObj(index -> prefix + this.gid + "_" + (index + 1) + ext)
                    .toList();
        } else {
            iconSrcs.add(baseName + ext);
        }

        return iconSrcs;
    }

    /**
     * base 로 저장된 modifier 모두 반환 <br>
     * 런타임에서 직접 BaseStatusEffect.get 으로 호출하지 않는경우 StatusEffect.getBaseModifiers 사용 권장
     *
     * @return linkedHashMap
     */
    public Map<StatusModifierType, StatusModifier> getModifiers() {
        return this.statusModifiers;
    }

    /**
     * 첫번째 StatusModifier 를 반환.<br>
     * 주로 기본 상태효과 (BasicStatusEffect) 에서 사용
     *
     * @return StatusModifier, non null
     */
    public StatusModifier getFirstModifier() {
        return this.statusModifiers.values().stream().findFirst().orElseThrow((() -> new IllegalArgumentException("[getFirstModifier] modifier 없음, id = " + this.id + " name = " + this.name)));
    }

    /**
     * StatusModifierType 에 맞는 modifier 가져옴
     *
     * @param type
     * @return 없으면 null
     */
    public StatusModifier getModifier(StatusModifierType type) {
        return this.statusModifiers.get(type);
    }

    public int getProcessOrder() {
        if (this.type == StatusEffectType.PASSIVE || this.type == StatusEffectType.DISPLAY_PASSIVE) return 5;

        if (this.statusModifiers.size() == 1) { // 기본 상태효과
            // dispel 과 clear 를 최우선
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_DISPEL)) return 10;
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_CLEAR)) return 11;
            // 회복효과는 버프 다음
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_HEAL)) return 55;
            // 슬립데미지는 회복효과 다음
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_DAMAGE)) return 56;
            // 게이지 버프/디버프는 일반버프/디버프 다음
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_CHARGE_GAUGE_UP)) return 60;
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_CHARGE_GAUGE_DOWN)) return 110;
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_UP)) return 61;
            if (this.statusModifiers.containsKey(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_DOWN)) return 111;
        }

        // 기준
        if (this.type == StatusEffectType.BUFF) return 50;
        if (this.type == StatusEffectType.DEBUFF) return 100;

        throw new IllegalArgumentException("[getProcessOrder] is not a valid StatusEffectType, this = " + this);
    }

}