package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder @Data
public class StatusEffectDto {
    private Long id;
    private Long baseId; // transient 일시 null

    private StatusDurationType durationType;
    private Integer duration;
    private Integer remainingDuration;

    private Integer level;
    private String iconSrc;

    // from baseStatusEffect
    private String name;
    private StatusEffectType type;
    private String effectText;
    private String statusText;
    private int displayPriority;
    private String gid;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StatusEffectDto of(BaseStatusEffect baseEffect) {
        return builder()
                .id(null)
                .baseId(baseEffect.getId())
                .durationType(baseEffect.getDurationType())
                .duration(baseEffect.getDuration())
                .remainingDuration(baseEffect.getDuration())
                .level(baseEffect.getMaxLevel())
                .iconSrc(baseEffect.getIconSrcs().isEmpty() ? "" : baseEffect.getIconSrcs().getFirst())
                .name(baseEffect.getName())
                .type(baseEffect.getType())
                .effectText(baseEffect.getEffectText())
                .statusText(baseEffect.getStatusText())
                .displayPriority(baseEffect.getDisplayPriority())
                .gid(baseEffect.getGid())
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    public static StatusEffectDto of(StatusEffect statusEffect) {
        return builder()
                .id(statusEffect.getId())
                .baseId(statusEffect.getBaseStatusEffect().getId())
                .durationType(statusEffect.getBaseStatusEffect().getDurationType())
                .duration(statusEffect.getDuration())
                .remainingDuration(statusEffect.getRemainingDuration())
                .level(statusEffect.getLevel())
                .iconSrc(statusEffect.getIconSrc())
                .name(statusEffect.getBaseStatusEffect().getName())
                .type(statusEffect.getBaseStatusEffect().getType())
                .effectText(statusEffect.getBaseStatusEffect().getEffectText())
                .statusText(statusEffect.getBaseStatusEffect().getStatusText())
                .displayPriority(statusEffect.getBaseStatusEffect().getDisplayPriority())
                .gid(statusEffect.getBaseStatusEffect().getGid())
                .createdAt(statusEffect.getCreatedAt())
                .updatedAt(statusEffect.getUpdatedAt())
                .build();
    }

    /**
     * 오의게이지 변화량을 보여주기 위해 사용
     */
    public static StatusEffectDto fromChargeGaugeEffect(StatusEffect statusEffect, int chargeGauge) {
        String chargeGaugeDeltaPostfix = statusEffect.getBaseStatusEffect().getTargetType() == StatusEffectTargetType.ENEMY
                ? "(" + chargeGauge + ")"
                : "(" + chargeGauge + "%)";
        return builder()
                .id(statusEffect.getId())
                .baseId(statusEffect.getBaseStatusEffect().getId())
                .durationType(statusEffect.getBaseStatusEffect().getDurationType())
                .duration(statusEffect.getDuration())
                .remainingDuration(statusEffect.getRemainingDuration())
                .level(statusEffect.getLevel())
                .iconSrc(statusEffect.getIconSrc())
                .name(statusEffect.getBaseStatusEffect().getName())
                .type(statusEffect.getBaseStatusEffect().getType())
                .effectText(statusEffect.getBaseStatusEffect().getEffectText() + chargeGaugeDeltaPostfix)
                .statusText(statusEffect.getBaseStatusEffect().getStatusText())
                .displayPriority(statusEffect.getBaseStatusEffect().getDisplayPriority())
                .gid(statusEffect.getBaseStatusEffect().getGid())
                .createdAt(statusEffect.getCreatedAt())
                .updatedAt(statusEffect.getUpdatedAt())
                .build();
    }

}
