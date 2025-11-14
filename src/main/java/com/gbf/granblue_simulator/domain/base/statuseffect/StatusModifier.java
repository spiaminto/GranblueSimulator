package com.gbf.granblue_simulator.domain.base.statuseffect;

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

    private double value;

    @Transient
    private Integer currentLevel; // 편의를 위해 추가

    public void setBaseStatusEffect(BaseStatusEffect baseStatusEffect) {
        this.baseStatusEffect = baseStatusEffect;
        baseStatusEffect.getStatusModifiers().put(this.type, this);
    }

    public StatusModifier setCurrentLevel(Integer level) {
        this.currentLevel = level;
        return this;
    }

    public double getCalcValue() {
        return this.currentLevel > 0 ? Math.floor(this.value * this.currentLevel * 100) / 100.0 : this.value;
    }

}
