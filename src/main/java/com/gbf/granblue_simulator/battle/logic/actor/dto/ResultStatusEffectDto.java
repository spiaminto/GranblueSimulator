package com.gbf.granblue_simulator.battle.logic.actor.dto;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Builder @Data
public class ResultStatusEffectDto {
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

    public static ResultStatusEffectDto of(StatusEffect statusEffect) {
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
