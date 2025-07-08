package com.gbf.granblue_simulator.logic.actor.dto;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class BattleStatusDto {
    private Long id;
    private Long statusId;
    private Long battleActorId;

    private Integer duration;
    private Integer level;
    private String iconSrc;

    // from status
    private String name;
    private StatusType statusType;
    private String effectText;
    private String statusText;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BattleStatusDto of(BattleStatus battleStatus) {
        return builder()
                .id(battleStatus.getId())
                .statusId(battleStatus.getStatus().getId())
                .battleActorId(battleStatus.getBattleActor().getId())
                .duration(battleStatus.getDuration())
                .level(battleStatus.getLevel())
                .iconSrc(battleStatus.getIconSrc())
                .name(battleStatus.getStatus().getName())
                .statusType(battleStatus.getStatus().getType())
                .effectText(battleStatus.getStatus().getEffectText())
                .statusText(battleStatus.getStatus().getStatusText())
                .createdAt(battleStatus.getCreatedAt())
                .updatedAt(battleStatus.getUpdatedAt())
                .build();
    }
}
