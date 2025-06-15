package com.gbf.granblue_simulator.domain.move.prop.status;

import com.gbf.granblue_simulator.domain.move.Move;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;

import java.util.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString(exclude = {"move"})
/**
 * 온전히 표현을 위해사용
 * 값과 계산은 캐릭터 로직에서 담당
 */
public class Status {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "move_id")
    private Move move;

    private String name; // 일단 effectText 와 동일하게 사용

    @Builder.Default
    private Integer maxLevel = 0; // 최고 레벨

    @Enumerated(EnumType.STRING)
    private StatusType type;

    @Enumerated(EnumType.STRING)
    private StatusTargetType target;

    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트
    private Integer duration; // 효과시간
    @Accessors(fluent = true)
    private boolean removable; // 소거불가, 해제불가, 회복불가
    private boolean resistible; // 필중인지 확인

    @OneToMany(mappedBy = "status") @MapKey(name = "type") @Builder.Default
    Map<StatusEffectType, StatusEffect> statusEffects = new LinkedHashMap<>();

    @Type(ListArrayType.class)
    @Column(name = "iconSrcs", columnDefinition = "text[]") @Builder.Default
    private List<String> iconSrcs = new ArrayList<>();

    public void setMove(Move move) {
        this.move = move;
        move.getStatuses().add(this);
    }

}