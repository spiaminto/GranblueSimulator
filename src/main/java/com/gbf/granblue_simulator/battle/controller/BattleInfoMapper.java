package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.AssetInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.CharacterBattleInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.EnemyInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BattleInfoMapper {

    public static CharacterBattleInfo toCharacterInfo(Actor partyMember) {
        return CharacterBattleInfo.builder()
                .id(partyMember.getId())
                .name(partyMember.getName())
                .order(partyMember.getCurrentOrder())
                .portraitSrc(partyMember.getActorVisual().getPortraitImageSrc())
                .statuses(partyMember.getStatusEffects().stream()
                        .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                        .filter(StatusEffect::isDisplayable)
                        .map(StatusEffectDto::of)
                        .toList())
                .hp(partyMember.getHp())
                .maxHp(partyMember.getMaxHp())
                .hpRate(partyMember.getHpRateInt())
                .chargeGauge(partyMember.getChargeGauge())
                .maxChargeGauge(partyMember.getMaxChargeGauge())
                .abilities(partyMember.getMoves().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .map(MoveInfo::from)
                        .toList())
                .supportAbilities(partyMember.getMoves().stream()
                        .filter(move -> move.getType() != MoveType.TRIGGERED_ABILITY && move.getType().getParentType() == MoveType.SUPPORT_ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .map(MoveInfo::from)
                        .toList())
                .chargeAttack(MoveInfo.from(partyMember.getFirstMove(MoveType.CHARGE_ATTACK_DEFAULT)))
                .abilityCoolDowns(partyMember.getAbilityCooldowns())
                .abilitySealeds(partyMember.getAbilitySealeds())
                .fatalChainGauge(partyMember.getBaseActor().isLeaderCharacter() ? partyMember.getFatalChainGauge() : null)
                .build();
    }


    public static EnemyInfo toEnemyInfo(Enemy enemy) {
        return EnemyInfo.builder()
                .id(enemy.getId())
                .name(enemy.getName())
                .formOrder(enemy.getCurrentForm())
                .statuses(enemy.getStatusEffects().stream()
                        .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                        .filter(StatusEffect::isDisplayable)
                        .map(StatusEffectDto::of)
                        .toList())
                .hp(enemy.getHp())
                .hpRate(enemy.getHpRateInt())
                .currentChargeGauge(enemy.getChargeGauge())
                .maxChargeGauge(Collections.nCopies(enemy.getMaxChargeGauge(), 1)) // 타임리프로 순회돌리려고 리스트로 넘김
                .omen(null)
                .build();
    }

    public static List<AssetInfo> toAssetInfo(List<Actor> currentFieldActors, List<Move> summonMoves) {
        List<AssetInfo> assets =  currentFieldActors.stream()
                .map(actor -> {
                    ActorVisual actorVisual = actor.getActorVisual();
                    
                    // 본체
                    String mainCjs = actorVisual.getCjsName();
                    String additionalMainCjsName = actorVisual.getAdditionalCjsName();

                    // 일반 공격
                    List<String> attackCjses = actorVisual.getAttackVisuals().stream().map(EffectVisual::getCjsName).sorted().toList();

                    // 어빌리티
                    Map<Long, AssetInfo.CjsDto> abilityCjses = actor.getMoves().stream()
                            .filter(move -> move.getBaseMove().getDefaultVisual() != null && (move.getType().getParentType() == MoveType.ABILITY || move.getType().getParentType() == MoveType.SUPPORT_ABILITY))
                            .collect(Collectors.toMap(
                                    Move::getId,
                                    move -> {
                                        EffectVisual defaultVisual = move.getBaseMove().getDefaultVisual();
                                        return AssetInfo.CjsDto.builder()
                                                .cjs(defaultVisual.getCjsName())
                                                .isTargetedEnemy(defaultVisual.isTargetedEnemy())
                                                .voiceLabel(defaultVisual.getVoiceLabel())
                                                .build();
                                    }
                            ));
                    // 페이탈 체인
                    if (actor.isCharacter()) {
                        Move fatalChainMove = actor.getFirstMove(MoveType.FATAL_CHAIN_DEFAULT);
                        abilityCjses.put(fatalChainMove.getId(), AssetInfo.CjsDto.builder()
                                .cjs(fatalChainMove.getBaseMove().getDefaultVisual().getCjsName())
                                .isTargetedEnemy(fatalChainMove.getBaseMove().getDefaultVisual().isTargetedEnemy())
                                .build());
                    }

                    // 오의
                    List<AssetInfo.CjsDto> specialCjses = actorVisual.getChargeAttackVisuals().stream()
                            .map(effectVisual -> AssetInfo.CjsDto.builder()
                                    .cjs(effectVisual.getCjsName())
                                    .voiceLabel(effectVisual.getVoiceLabel())
                                    .build()).toList();
                    Integer chargeAttackStartFrame = actorVisual.getChargeAttackVisuals().stream().map(EffectVisual::getChargeAttackStartFrame).max(Comparator.naturalOrder()).orElse(0);

                    // 추가 오의
                    List<String> additionalSpecialCjses = actorVisual.getAdditionalChargeAttackVisuals().stream().map(EffectVisual::getCjsName).toList();

                    // 메인캐릭터 추가 - 소환석
                    boolean isLeaderCharacter = actor.getBaseActor().isLeaderCharacter();
                    Map<Long, String> summonCjses = new HashMap<>();
                    if (isLeaderCharacter) {
                        summonMoves.forEach(move -> summonCjses.put(move.getId(), move.getBaseMove().getDefaultVisual().getCjsName()));
                    }

                    return AssetInfo.builder()
                            .actorId(actor.getId())
                            .actorOrder(actor.getCurrentOrder())
                            .isLeaderCharacter(isLeaderCharacter)
                            .isEnemy(actor.isEnemy())
                            .weaponId(actorVisual.getWeaponId())
                            .isChargeAttackSkip(actor.getMember().isChargeAttackSkip()) // 일단 멤버단위
                            .chargeAttackStartFrame(chargeAttackStartFrame)
                            .mainCjs(mainCjs)
                            .attackCjses(attackCjses)
                            .abilityCjses(abilityCjses)
                            .specialCjses(specialCjses)
                            .summonCjses(summonCjses)
                            .additionalMainCjs(additionalMainCjsName)
                            .additionalSpecialCjses(additionalSpecialCjses)
                            .build();
                }).toList();
        return assets;
    }

    public static MoveInfo toFatalChainInfo(BaseMove fatalChainMove) {
        return MoveInfo.builder()
                .id(fatalChainMove.getId())
                .name(fatalChainMove.getName())
                .info(fatalChainMove.getInfo())
                .iconImageSrc(fatalChainMove.getIconImageSrc())
                .build();
    }

    public static MoveInfo toSummonInfo(Move move) {
        return MoveInfo.builder()
                .id(move.getId())
                .name(move.getBaseMove().getName())
                .info(move.getBaseMove().getInfo())
                .iconImageSrc(move.getBaseMove().getIconImageSrc())
                .portraitImageSrc(move.getBaseMove().getDefaultVisual().getPortraitImageSrc())
                .cutinImageSrc(move.getBaseMove().getDefaultVisual().getCutinImageSrc())
                .cjsName(move.getBaseMove().getDefaultVisual().getCjsName())
                .cooldown(move.getCooldown())
                .maxCooldown(move.getBaseMove().getCoolDown())
                .build();
    }
}
