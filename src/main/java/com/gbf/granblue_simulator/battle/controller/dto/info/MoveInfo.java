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
import java.util.function.Function;

@Data
@Builder
public class MoveInfo {
    private Long id;
    private Integer order;

    private Long actorId;
    private Integer actorIndex;

    private String type;
    private String abilityType;
    private String displayAbilityType;

    private String name;
    private String info;
    private double damageRate;
    private int hitCount;

    private String iconImageSrc;
    private String portraitImageSrc; // 소환석 포트레이트
    private String cutinImageSrc; // 소환석 컷인
    private String detailImageSrc;

    private Integer maxCooldown;
    private Integer cooldown;
    private Boolean sealed;

    @Builder.Default
    private List<StatusEffectDto> statusEffects = new ArrayList<>();

    private Long nextMoveId;
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
                .displayAbilityType(baseMove.getAbilityType() == null ? "" : baseMove.getAbilityType().getDisplayName())

                .name(baseMove.getName())
                .info(baseMove.getInfo())
                .damageRate(baseMove.getDamageRate())
                .hitCount(baseMove.getHitCount())

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
                .nextMoveId(baseMove.getNextMoveId())
                .build();
    }

    // from baseMove (fatalChain)
    public static MoveInfo from(BaseMove baseMove) {
        return from(baseMove, StatusEffectDto::of);
    }

    public static MoveInfo fromWithModifier(BaseMove baseMove) {
        return from(baseMove, StatusEffectDto::ofWithModifier);
    }

    private static MoveInfo from(BaseMove baseMove, Function<BaseStatusEffect, StatusEffectDto> modifier) {

        String detailSrc = "";
        String portraitSrc = "";
        if (baseMove.getDefaultVisual() != null) {
            // 소환석만 사용중
            detailSrc = baseMove.getType() == MoveType.SUMMON ? baseMove.getDefaultVisual().getDetailImageSrc() : "";
            portraitSrc = baseMove.getType() == MoveType.SUMMON ? baseMove.getDefaultVisual().getPortraitImageSrc() : "";
        }

        return MoveInfo.builder()
                .id(baseMove.getId())
                .order(baseMove.getType().getOrder())

                .actorId(null)
                .actorIndex(null)

                .type(baseMove.getType().name())
                .abilityType(baseMove.getAbilityType() == null ? "" : baseMove.getAbilityType().name())
                .displayAbilityType(baseMove.getAbilityType() == null ? "" : baseMove.getAbilityType().getDisplayName())

                .name(baseMove.getName())
                .info(baseMove.getInfo())
                .damageRate(baseMove.getDamageRate())
                .hitCount(baseMove.getHitCount())

                .iconImageSrc(baseMove.getIconImageSrc())
                .detailImageSrc(detailSrc)
                .portraitImageSrc(portraitSrc)

                .cooldown(0)
                .maxCooldown(baseMove.getCoolDown())
                .sealed(false)

                .statusEffects(baseMove.getOrderedBaseStatusEffects().stream()
                        .filter(BaseStatusEffect::isMetadataDisplayable)
                        .map(modifier)
                        .toList()
                )
                .nextMoveId(baseMove.getNextMoveId())
                .build();
    }

}
