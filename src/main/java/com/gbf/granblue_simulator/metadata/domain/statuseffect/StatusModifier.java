package com.gbf.granblue_simulator.metadata.domain.statuseffect;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class StatusModifier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "base_status_effect_id")
    private BaseStatusEffect baseStatusEffect;

    @Enumerated(EnumType.STRING)
    private StatusModifierType type;

    @Getter(AccessLevel.NONE) // 기본 getter 사용금지
    private double value;

    public void setBaseStatusEffect(BaseStatusEffect baseStatusEffect) {
        this.baseStatusEffect = baseStatusEffect;
        baseStatusEffect.getStatusModifiers().put(this.type, this);
    }

    /**
     * 레벨을 반영하지 않은 기본값을 반환
     * @return
     */
    public double getInitValue() { return this.value; }


}
