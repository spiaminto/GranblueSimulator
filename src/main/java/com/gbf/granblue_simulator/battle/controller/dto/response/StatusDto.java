package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatusDto {
    private String type; // StatusType
    private String name;

    private String imageSrc;

    private String effectText; // 이펙트에 띄울 텍스트
    private String statusText; // 스테이터스 창에 띄울 텍스트
    private int displayPriority;

    private StatusDurationType durationType;
    private int duration;
    private int remainingDuration;

    public static StatusDto of(ResultStatusEffectDto statusEffect) {
        return StatusDto.builder()
                .type(statusEffect.getStatusEffectType().name())
                .name(statusEffect.getName())
                .imageSrc(statusEffect.getIconSrc())
                .effectText(statusEffect.getEffectText())
                .statusText(statusEffect.getStatusText())
                .displayPriority(statusEffect.getDisplayPriority())
                .durationType(statusEffect.getDurationType())
                .duration(statusEffect.getDuration())
                .remainingDuration(statusEffect.getRemainingDuration())
                .build();
    }
}
