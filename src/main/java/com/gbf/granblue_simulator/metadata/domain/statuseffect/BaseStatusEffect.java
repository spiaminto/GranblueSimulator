package com.gbf.granblue_simulator.metadata.domain.statuseffect;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
/**
 * 온전히 표현을 위해사용
 * 값과 계산은 캐릭터 로직에서 담당
 */
public class BaseStatusEffect {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "move_id") @EqualsAndHashCode.Exclude @ToString.Exclude
    private Move move;

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

    private boolean removable; // 소거불가, 해제불가, 회복불가
    private boolean resistible; // 필중인지 확인

    @OneToMany(mappedBy = "baseStatusEffect") @MapKey(name = "type") @Builder.Default @EqualsAndHashCode.Exclude @ToString.Exclude
    Map<StatusModifierType, StatusModifier> statusModifiers = new LinkedHashMap<>();

    @Type(ListArrayType.class)
    @Column(name = "iconSrcs", columnDefinition = "text[]") @Builder.Default
    private List<String> iconSrcs = new ArrayList<>();

    public void setMove(Move move) {
        this.move = move;
        move.getBaseStatusEffects().add(this);
    }

    /**
     * 첫번째 StatusModifier 를 반환.<br>
     * 주로 기본 상태효과 (BasicStatusEffect) 에서 사용
     * @return
     */
    public StatusModifier getFirstModifier() {
        return this.statusModifiers.values().stream().findFirst().orElse(null);
    }

    public StatusModifier getModifier(StatusModifierType type) {
        return this.statusModifiers.get(type);
    }

}