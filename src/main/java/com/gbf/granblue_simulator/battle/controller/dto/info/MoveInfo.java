package com.gbf.granblue_simulator.battle.controller.dto.info;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MoveInfo {
    private Long id;
    private Integer order;

    private Long actorId;
    private Integer actorIndex;

    private String type;
    private String abilityType;

    private String name;
    private String info;

    private String iconImageSrc;
    private String portraitImageSrc; // 소환석 포트레이트
    private String cutinImageSrc; // 소환석 컷인

    private Integer maxCooldown;
    private Integer cooldown;
    private Boolean sealed;

    @Builder.Default
    private List<StatusEffectDto> statusEffects = new ArrayList<>();

    private String cjsName;

    public static MoveInfo from(Move move) {
        BaseMove baseMove = move.getBaseMove();

        String cutinSrc = "";
        String portraitSrc = "";
        if (baseMove.getDefaultVisual() != null) {
            // 소환석만 사용중
            cutinSrc = move.getType().getParentType() == MoveType.SUMMON ? baseMove.getDefaultVisual().getCutinImageSrc() : "";
            portraitSrc = move.getType().getParentType() == MoveType.SUMMON ? baseMove.getDefaultVisual().getPortraitImageSrc() : "";
        }
        boolean sealed = false;
        if (move.getType().getParentType() == MoveType.ABILITY) {
            sealed = move.getActor().getAbilitySealeds().get(move.getType().getOrder() - 1);
        }

        return MoveInfo.builder()
                .id(move.getId())
                .order(move.getType().getOrder())

                .actorId(move.getActor().getId())
                .actorIndex(move.getActor().getCurrentOrder())

                .type(move.getType().getParentType().name())
                .abilityType(baseMove.getAbilityType() == null ? "" : baseMove.getAbilityType().name())

                .name(baseMove.getName())
                .info(baseMove.getInfo())

                .iconImageSrc(baseMove.getIconImageSrc())
                .cutinImageSrc(cutinSrc)
                .portraitImageSrc(portraitSrc)

                .cooldown(move.getCooldown())
                .maxCooldown(baseMove.getCoolDown())
                .sealed(sealed)

                .statusEffects(baseMove.getOrderedBaseStatusEffects().stream()
                        .filter(BaseStatusEffect::isMetadataDisplayable)
                        .map(StatusEffectDto::of)
                        .toList()
                )
                .build();
    }

    // from baseMove (fatalChain)
    public static MoveInfo from(BaseMove baseMove) {
        return MoveInfo.builder()
                .id(baseMove.getId())
                .order(baseMove.getType().getOrder())

                .actorId(null)
                .actorIndex(null)

                .type(baseMove.getType().getParentType().name()) // 페이탈 체인은 DEFAULT
                .abilityType(baseMove.getAbilityType() == null ? "" : baseMove.getAbilityType().name())

                .name(baseMove.getName())
                .info(baseMove.getInfo())

                .iconImageSrc(baseMove.getIconImageSrc())
                .cutinImageSrc("")
                .portraitImageSrc("")

                .cooldown(0)
                .maxCooldown(baseMove.getCoolDown())
                .sealed(false)

                .statusEffects(baseMove.getOrderedBaseStatusEffects().stream()
                        .filter(BaseStatusEffect::isMetadataDisplayable)
                        .map(StatusEffectDto::of)
                        .toList()
                )
                .build();
    }

}
