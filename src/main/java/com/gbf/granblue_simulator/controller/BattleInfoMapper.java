package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.response.info.battle.*;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.asset.Asset;
import com.gbf.granblue_simulator.domain.asset.AssetType;
import com.gbf.granblue_simulator.domain.move.MotionType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BattleInfoMapper {

    public static BattleCharacterInfo toCharacterInfo(BattleActor partyMember) {
        return BattleCharacterInfo.builder()
                .id(partyMember.getId())
                .name(partyMember.getName())
                .order(partyMember.getCurrentOrder())
                .portraitSrc(partyMember.getActor().getBattlePortraitSrc())
                .statuses(partyMember.getBattleStatuses().stream()
                        .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                        .filter(battleStatus -> battleStatus.getStatus().getType().isPresentable()).toList())
                .hp(partyMember.getHp())
                .maxHp(partyMember.getMaxHp())
                .hpRate(partyMember.calcHpRate())
                .chargeGauge(partyMember.getChargeGauge())
                .maxChargeGauge(partyMember.getMaxChargeGauge())
                .abilities(partyMember.getActor().getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .map(move -> AbilityInfo.builder()
                                .id(move.getId())
                                .name(move.getName())
                                .info(move.getInfo())
                                .cooldown(move.getCoolDown())
                                .iconImageSrc(move.getIconImageSrc())
                                .build())
                        .toList())
                .chargeAttack(partyMember.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilityCoolDowns(List.of(partyMember.getFirstAbilityCoolDown(), partyMember.getSecondAbilityCoolDown(), partyMember.getThirdAbilityCoolDown()))
                .build();
    }


    public static BattleEnemyInfo toEnemyInfo(BattleEnemy enemy) {
        String omenPrefix = null;
        Integer omenValue = null;
        OmenType omenType = null;
        String omenName = null;
        String omenInfo = null;
        MotionType initialMotionType = null;
        if (enemy.getCurrentStandbyType() != null) { // TODO 나중에 리팩토링 해야될듯
            Move standbyMove = enemy.getMove(enemy.getCurrentStandbyType());
            initialMotionType = standbyMove.getMotionType();
            Omen omen = standbyMove.getOmen();
            omenPrefix = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo();
            omenValue = enemy.getOmenValue();
            omenType = omen.getOmenType();
            omenName = omen.getName();
            omenInfo = omen.getInfo();
        }

        return BattleEnemyInfo.builder()
                .id(enemy.getId())
                .name(enemy.getName())
                .phase(enemy.getCurrentForm())
                .statuses(enemy.getBattleStatuses().stream().filter(battleStatus -> battleStatus.getStatus().getType().isPresentable()).toList())
                .hpRate(enemy.calcHpRate())
                .currentChargeGauge(enemy.getChargeGauge())
                .maxChargeGauge(Collections.nCopies(enemy.getMaxChargeGauge(), 1)) // 타임리프로 순회돌리려고 리스트로 넘김
                .initialMoveType(enemy.getCurrentStandbyType() == null ? MoveType.IDLE_DEFAULT : enemy.getCurrentStandbyType()) // 동적으로
                .initialMotionType(initialMotionType)
                .omenActivated(enemy.getCurrentStandbyType() != null)

                .omenPrefix(omenPrefix)
                .omenValue(omenValue)
                .omenType(omenType)
                .omenName(omenName)
                .omenInfo(omenInfo)

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

                    // 메인캐릭터 추가 (소환석, 페이탈체인)
                    boolean isMainCharacter = mainActorAsset.getActorId().equals(mainCharacterActorId);
                    Map<Long, String> summonCjsMap = new HashMap<>();
                    if (isMainCharacter) {
                        summonCjsMap = summonAssets.stream()
                                .collect(Collectors.toMap(
                                        Asset::getMoveId,
                                        Asset::getRootCjsName,
                                        (existing, replacement) -> replacement // 중복 병합
                                ));
                        abilityCjsMap.put(fatalChainAsset.getType(), new AssetInfo.Asset.AbilityCjsDto(fatalChainAsset.getCjsName(), fatalChainAsset.isTargetedEnemy()));
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

    public static FatalChainInfo toFatalChainInfo(BattleActor mainCharacter, Move fatalChainMove) {
        return FatalChainInfo.builder()
                .id(fatalChainMove.getId())
                .name(fatalChainMove.getName())
                .info(fatalChainMove.getInfo())
                .gaugeValue(mainCharacter.getFatalChainGauge())
                .effectVideoSrc("")
                .seAudioSrc("")
                .build();

    }

    public static SummonInfo toSummonInfo(Move move, BattleActor mainCharacter) {
        return SummonInfo.builder()
                .id(move.getId())
                .name(move.getName())
                .info(move.getInfo())
                .iconImageSrc(move.getIconImageSrc())
                .cooldown(mainCharacter.getSummonCoolDowns().get(mainCharacter.getSummonMoveIds().indexOf(move.getId())))
                .build();
    }


    public static Map<String, String> getVideoSrcMap(List<Move> moves) {
        Map<String, String> videoSrcMap = new HashMap<>();
        moves.forEach(move -> {
            // 이펙트
            String effectVideoSrc = move.getAsset().getEffectVideoSrc();
            if (effectVideoSrc != null && !effectVideoSrc.isEmpty()) {
                videoSrcMap.put(effectVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            }
            // 모션
            String motionVideoSrc = move.getAsset().getMotionVideoSrc();
            if (motionVideoSrc != null && !motionVideoSrc.isEmpty()) {
                String fullSizeClassName = move.getAsset().isMotionVideoFull() ? "full-size" : "";
                videoSrcMap.put(motionVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " motion " + fullSizeClassName);
            }
        });
        return videoSrcMap;
    }

    public static Map<String, String> getAudioSrcMap(List<Move> moves) {
        Map<String, String> audioSrcMap = new HashMap<>();
        moves.forEach(move -> {
            // 이펙트
            String seAudioSrc = move.getAsset().getSeAudioSrc();
            if (seAudioSrc != null && !seAudioSrc.isEmpty()) {
                audioSrcMap.put(move.getAsset().getSeAudioSrc(), move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            }
            // 보이스
            String voiceAudioSrc = move.getAsset().getVoiceAudioSrc();
            if (voiceAudioSrc != null && !voiceAudioSrc.isEmpty()) {
                audioSrcMap.put(voiceAudioSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " voice");
            }
        });
        return audioSrcMap;
    }


    /**
     * 적의 effectVideo 를 key = effectVideoSrc, value = [MoveType.parentType.className, MoveType.className1 , ...] 인 Map 으로 변환
     * ex) 같은 effectVideoSrc (standby-1.webm) 을 가지는 STAND_BY_A, STAND_BY_D 의 경우 value 를 [standby, standby-a, standby-d] 로 묶는다.
     *
     * @param enemy
     * @return
     */
    public static Map<String, EnemyVideoInfo> getEnemyVideoSrcMap(BattleActor enemy) {
        Map<String, EnemyVideoInfo> enemyVideoSrcMap = new HashMap<>();
        enemyVideoSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getEffectVideoSrc() != null ? move.getAsset().getEffectVideoSrc() : "",
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    Integer hitEffectDelay = moves.getFirst().getAsset().getEffectHitDelay();
                                    classNames.add(parentClassName);
                                    classNames.add("effect"); // 적 이펙트
                                    return EnemyVideoInfo.builder()
                                            .effectHitDelay(hitEffectDelay)
                                            .classNames(classNames)
                                            .build();
                                }
                        )
                )));
        return enemyVideoSrcMap;
    }

    public static Map<String, List<String>> getEnemyAudioSrcMap(BattleActor enemy) {
        Map<String, List<String>> enemyAudioSrcMap = new HashMap<>();
        enemyAudioSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getSeAudioSrc() != null ? move.getAsset().getSeAudioSrc() : "",
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    classNames.add(parentClassName);
                                    return classNames;
                                }
                        )
                )));
        return enemyAudioSrcMap;
    }
}
