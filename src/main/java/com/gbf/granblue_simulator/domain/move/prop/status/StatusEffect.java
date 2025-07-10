package com.gbf.granblue_simulator.domain.move.prop.status;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode @ToString
public class StatusEffect {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "status_id")
    private Status status;

    @Enumerated(EnumType.STRING)
    private StatusEffectType type;

    private double value;

    @Transient
    private Integer currentLevel; // DB 에 저장되지 않고, CommonLogic.getStatusEffectMap 에서 초기화 되고 사용됨. 사용후엔 휘발됨.

    public void setStatus(Status status) {
        this.status = status;
        status.getStatusEffects().put(this.type, this);
    }

    public StatusEffect setCurrentLevel(Integer level) {
        this.currentLevel = level;
        return this;
    }

    public Double getCalcValue() {
        return this.currentLevel > 0 ? this.value * this.currentLevel : this.value;
    }

}
