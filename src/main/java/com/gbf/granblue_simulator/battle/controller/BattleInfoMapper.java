package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.AssetInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.CharacterInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.EnemyInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.controller.dto.response.OmenDto;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.metadata.domain.asset.Asset;
import com.gbf.granblue_simulator.metadata.domain.asset.AssetType;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BattleInfoMapper {

    public static CharacterInfo toCharacterInfo(Actor partyMember) {
        return CharacterInfo.builder()
                .id(partyMember.getId())
                .name(partyMember.getName())
                .order(partyMember.getCurrentOrder())
                .portraitSrc(partyMember.getBaseActor().getBattlePortraitSrc())
                .statuses(partyMember.getStatusEffects().stream()
                        .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                        .filter(battleStatus -> battleStatus.getBaseStatusEffect().getType().isPresentable()).toList())
                .hp(partyMember.getHp())
                .maxHp(partyMember.getMaxHp())
                .hpRate(partyMember.getHpRate())
                .chargeGauge(partyMember.getChargeGauge())
                .maxChargeGauge(partyMember.getMaxChargeGauge())
                .abilities(partyMember.getBaseActor().getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .map(move -> MoveInfo.builder()
                                .id(move.getId())
                                .name(move.getName())
                                .info(move.getInfo())
                                .cooldown(move.getCoolDown())
                                .iconImageSrc(move.getIconImageSrc())
                                .abilityType(move.getAbilityType().name())
                                .build())
                        .toList())
                .chargeAttack(partyMember.getBaseActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilityCoolDowns(partyMember.getAbilityCooldowns())
                .fatalChainGauge(partyMember.getBaseActor().isLeaderCharacter() ? partyMember.getFatalChainGauge() : null)
                .build();
    }


    public static EnemyInfo toEnemyInfo(Enemy enemy) {
        OmenDto omenDto = OmenDto.builder().build();
        if (enemy.getCurrentStandbyType() != null) {
            Move standbyMove = enemy.getMove(enemy.getCurrentStandbyType());
            Omen omen = standbyMove.getOmen();
            omenDto = OmenDto.builder()
                    .type(omen.getOmenType())
                    .remainValue(enemy.getOmenValue())
                    .cancelCondition(omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo())
                    .name(omen.getName())
                    .motion(standbyMove.getMotionType().getMotion())
                    .build();
        }


        return EnemyInfo.builder()
                .id(enemy.getId())
                .name(enemy.getName())
                .formOrder(enemy.getCurrentForm())
                .statuses(enemy.getStatusEffects().stream().filter(battleStatus -> battleStatus.getBaseStatusEffect().getType().isPresentable()).toList())
                .hp(enemy.getHp())
                .hpRate(enemy.getHpRate())
                .currentChargeGauge(enemy.getChargeGauge())
                .maxChargeGauge(Collections.nCopies(enemy.getMaxChargeGauge(), 1)) // 타임리프로 순회돌리려고 리스트로 넘김
                .initialMoveType(enemy.getCurrentStandbyType() == null ? MoveType.IDLE_DEFAULT : enemy.getCurrentStandbyType()) // 동적으로
                .omen(omenDto)
                .build();
    }

    public static List<AssetInfo.Asset> toAssetInfoAsset(Long mainCharacterActorId, String weaponId, List<Asset> assets, List<Asset> summonAssets, Asset fatalChainAsset) {
        // rootCjsName 를 key 로 하는 Map
        Map<String, List<Asset>> assetMapByRootCjsName = assets.stream().collect(Collectors.groupingBy(Asset::getRootCjsName));
        log.info("assetMapByRootCjsName: {}", assetMapByRootCjsName);
        // 각 rootCjsName 별 Map.values 을 다시 MotionType 을 key 로 하는 Map 으로 변환
        List<Map<AssetType, List<Asset>>> assetMapList = assetMapByRootCjsName.values().stream()
                .map(assetList -> assetList.stream().collect(Collectors.groupingBy(Asset::getType)))
                .toList();

        return assetMapList.stream()
                .map(assetMap -> { // key: AssetType, mainCjs 별로 분리되어있음
                    log.info("assetMap = {}", assetMap);
                    // main cjs
                    List<Asset> actorAssets = assetMap.get(AssetType.ACTOR);
                    log.info("actorAssets = {}", actorAssets);
                    Asset mainActorAsset = actorAssets.getFirst();
                    String mainCjs = mainActorAsset.getCjsName();
                    // attack cjs
                    List<String> attackCjses = assetMap.get(AssetType.ATTACK).stream()
                            .map(Asset::getCjsName)
                            .sorted().toList();
                    // ability cjs
                    Map<AssetType, AssetInfo.Asset.AbilityCjsDto> abilityCjsMap = assetMap.entrySet().stream()
                            .filter(entry -> entry.getKey().isAbility())
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> new AssetInfo.Asset.AbilityCjsDto(entry.getValue().getFirst().getCjsName(), entry.getValue().getFirst().isTargetedEnemy())));
                    // fatalchain 추가
                    abilityCjsMap.put(fatalChainAsset.getType(), new AssetInfo.Asset.AbilityCjsDto(fatalChainAsset.getCjsName(), fatalChainAsset.isTargetedEnemy()));
                    // charge attack (special cjs)
                    List<Asset> specialAssets = assetMap.get(AssetType.SPECIAL);
                    List<String> specialCjses = specialAssets.stream().map(Asset::getCjsName).toList();
                    int chargeAttackStartFrame = specialAssets.getFirst().getChargeAttackStartFrame();
                    // additional main cjs and additional special cjs
                    boolean hasAdditionalAsset = actorAssets.size() > 1;
                    String additionalMainCjs = hasAdditionalAsset ? actorAssets.get(1).getCjsName() : null;
                    List<String> additionalSpecialCjses = hasAdditionalAsset
                            ? assetMap.get(AssetType.ADDITIONAL_SPECIAL).stream().map(Asset::getCjsName).toList()
                            : new ArrayList<>();

                    // 메인캐릭터 추가 - 소환석
                    boolean isMainCharacter = mainActorAsset.getActorId().equals(mainCharacterActorId);
                    Map<Long, String> summonCjsMap = new HashMap<>();
                    if (isMainCharacter) {
                        summonCjsMap = summonAssets.stream()
                                .collect(Collectors.toMap(
                                        Asset::getMoveId,
                                        Asset::getRootCjsName,
                                        (existing, replacement) -> replacement // 중복 병합
                                ));
                    }

                    return AssetInfo.Asset.builder()
                            .actorId(mainActorAsset.getActorId())
                            .mainCjs(mainCjs)
                            .attackCjses(attackCjses)
                            .abilityCjses(abilityCjsMap)
                            .specialCjses(specialCjses)
                            .summonCjses(summonCjsMap)
                            .additionalMainCjs(additionalMainCjs)
                            .additionalSpecialCjses(additionalSpecialCjses)
                            .weaponId(weaponId)
                            .chargeAttackStartFrame(chargeAttackStartFrame)
                            .build();
                }).collect(Collectors.toList());
    }

    public static MoveInfo toFatalChainInfo(Move fatalChainMove) {
        return MoveInfo.builder()
                .id(fatalChainMove.getId())
                .name(fatalChainMove.getName())
                .info(fatalChainMove.getInfo())
                .iconImageSrc(fatalChainMove.getIconImageSrc())
                .build();
    }

    public static MoveInfo toSummonInfo(Move move, Actor mainCharacter) {
        return MoveInfo.builder()
                .id(move.getId())
                .name(move.getName())
                .info(move.getInfo())
                .iconImageSrc(move.getIconImageSrc())
                .portraitImageSrc(move.getPortraitImageSrc())
                .cooldown(mainCharacter.getSummonCoolDowns().get(mainCharacter.getSummonMoveIds().indexOf(move.getId())))
                .build();
    }
}
