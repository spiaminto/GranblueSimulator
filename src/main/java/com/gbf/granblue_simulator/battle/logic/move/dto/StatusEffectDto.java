package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Builder
@Data
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
    private Integer maxLevel;
    private Boolean removable;
    private Boolean resistible;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Map<String, String> modifiers;

    public static StatusEffectDto of(BaseStatusEffect baseEffect) {
        return of(baseEffect, false);
    }

    /**
     * BaseStatusEffect 일때, 디버깅 및 확인용으로 사용 (현재 ROLE 기반으로 사용여부 판단중)
     * @return modifier 정보가 포함된 Dto
     */
    public static StatusEffectDto ofWithModifier(BaseStatusEffect baseEffect) {
        return of(baseEffect, true);
    }

    private static StatusEffectDto of(BaseStatusEffect baseEffect, boolean withModifier) {
        Map<String, String> modifiers = null;
        if (withModifier) {
            modifiers = baseEffect.getModifiers().values().stream()
                    .collect(Collectors.toMap(
                            modifier -> modifier.getType().name(),
                            modifier -> modifier.getInitValue() + ""
                    ));
        }
        return builder()
                .id(null)
                .baseId(baseEffect.getId())
                .durationType(baseEffect.getDurationType())
                .duration(baseEffect.getDuration())
                .remainingDuration(baseEffect.getDuration())
                .level(baseEffect.getMaxLevel())
                .maxLevel(baseEffect.getMaxLevel())
                .iconSrc(baseEffect.getIconSrcs().isEmpty() ? "" : baseEffect.getIconSrcs().getFirst())
                .name(baseEffect.getName())
                .type(baseEffect.getType())
                .effectText(baseEffect.getEffectText())
                .statusText(baseEffect.getStatusText())
                .displayPriority(baseEffect.getDisplayPriority())
                .gid(baseEffect.getGid())
                .removable(baseEffect.isRemovable())
                .resistible(baseEffect.isResistible())
                .createdAt(null)
                .updatedAt(null)
                .modifiers(modifiers)
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
                .maxLevel(statusEffect.getBaseStatusEffect().getMaxLevel())
                .iconSrc(statusEffect.getIconSrc())
                .name(statusEffect.getBaseStatusEffect().getName())
                .type(statusEffect.getBaseStatusEffect().getType())
                .effectText(statusEffect.getBaseStatusEffect().getEffectText())
                .statusText(statusEffect.getBaseStatusEffect().getStatusText())
                .displayPriority(statusEffect.getBaseStatusEffect().getDisplayPriority())
                .gid(statusEffect.getBaseStatusEffect().getGid())
                .createdAt(statusEffect.getCreatedAt())
                .updatedAt(statusEffect.getUpdatedAt())
                .removable(statusEffect.getBaseStatusEffect().isRemovable())
                .resistible(statusEffect.getBaseStatusEffect().isResistible())
                .build();
    }

    /**
     * 오의게이지 변화량을 보여주기 위해 사용
     */
    public static StatusEffectDto fromChargeGaugeEffect(StatusEffect statusEffect, int chargeGauge) {
        String chargeGaugeDeltaPostfix = "";
        if (!statusEffect.getBaseStatusEffect().getName().contains("초기화")) {
            chargeGaugeDeltaPostfix = statusEffect.getBaseStatusEffect().getTargetType() == StatusEffectTargetType.ENEMY
                    ? "(" + chargeGauge + ")"
                    : "(" + chargeGauge + "%)";
        }
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
