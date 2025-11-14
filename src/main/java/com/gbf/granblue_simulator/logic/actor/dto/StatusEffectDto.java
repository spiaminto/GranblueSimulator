package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.base.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class StatusEffectDto {
    private Long id;
    private Long statusId;
    private Long battleActorId;

    private StatusDurationType durationType;
    private Integer duration;
    private Integer remainingDuration;

    private Integer level;
    private String iconSrc;

    // from baseStatusEffect
    private String name;
    private StatusEffectType statusEffectType;
    private String effectText;
    private String statusText;
    private int displayPriority;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StatusEffectDto of(StatusEffect statusEffect) {
        return builder()
                .id(statusEffect.getId())
                .statusId(statusEffect.getBaseStatusEffect().getId())
                .battleActorId(statusEffect.getActor().getId())
                .durationType(statusEffect.getBaseStatusEffect().getDurationType())
                .duration(statusEffect.getDuration())
                .remainingDuration(statusEffect.getRemainingDuration())
                .level(statusEffect.getLevel())
                .iconSrc(statusEffect.getIconSrc())
                .name(statusEffect.getBaseStatusEffect().getName())
                .statusEffectType(statusEffect.getBaseStatusEffect().getType())
                .effectText(statusEffect.getBaseStatusEffect().getEffectText())
                .statusText(statusEffect.getBaseStatusEffect().getStatusText())
                .displayPriority(statusEffect.getBaseStatusEffect().getDisplayPriority())
                .createdAt(statusEffect.getCreatedAt())
                .updatedAt(statusEffect.getUpdatedAt())
                .build();
    }
}
