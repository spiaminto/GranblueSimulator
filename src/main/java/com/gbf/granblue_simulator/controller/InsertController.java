package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.request.insert.character.*;
import com.gbf.granblue_simulator.controller.request.insert.character.AbilityInsertRequest;
import com.gbf.granblue_simulator.controller.response.insert.InsertResponse;
import com.gbf.granblue_simulator.domain.actor.Character;
import com.gbf.granblue_simulator.domain.asset.Asset;
import com.gbf.granblue_simulator.domain.asset.AssetType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.asset.LegacyAsset;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.repository.AssetRepository;
import com.gbf.granblue_simulator.repository.actor.CharacterRepository;
import com.gbf.granblue_simulator.repository.move.LegacyAssetRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import com.gbf.granblue_simulator.repository.move.StatusEffectRepository;
import com.gbf.granblue_simulator.repository.move.StatusRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gbf.granblue_simulator.controller.request.insert.InsertSrcMapper.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsertController {

    private final CharacterRepository characterRepository;
    private final LegacyAssetRepository legacyAssetRepository;
    private final MoveRepository moveRepository;
    private final StatusRepository statusRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final AssetRepository assetRepository;

    @PostMapping("/insert/character")
    public ResponseEntity<InsertResponse> insertCharacter(@RequestBody CharacterInsertRequest characterInsertRequest) {
        log.info("characterInsertRequest: {}", characterInsertRequest);
        String nameEn = characterInsertRequest.getNameEn();

        Character character = Character.builder()
                .name(characterInsertRequest.getName())
                .nameEn(nameEn)
                .battlePortraitSrc(getBattlePortraitSrc(nameEn))
                .elementType(characterInsertRequest.getElementType())
                .isLeaderCharacter(Boolean.parseBoolean(characterInsertRequest.getIsLeaderCharacter()))
                .build();
        character = characterRepository.save(character);
        log.info("savedChar = {}", character);

        // idle
        Move idle = Move.builder()
                .type(MoveType.IDLE_DEFAULT)
                .name("idle")
                .info("idle")
                .elementType(character.getElementType())
                .actor(character)
                .build();
        idle = moveRepository.save(idle);

//        LegacyAsset idleLegacyAsset = LegacyAsset.builder()
//                .motionVideoSrc(getMotionVideoSrc(nameEn, MoveType.IDLE_DEFAULT))
//                .move(idle)
//                .build();
//        idleLegacyAsset = legacyAssetRepository.save(idleLegacyAsset);

        // guard
        Move guard = Move.builder()
                .type(MoveType.GUARD_DEFAULT)
                .name("guard")
                .info("guard")
                .elementType(character.getElementType())
                .damageRate(0.0)
                .coolDown(0)
                .actor(character)
                .build();
        moveRepository.save(guard);

//        LegacyAsset guardLegacyAsset = LegacyAsset.builder()
//                .move(guard)
//                .build();
//        guardLegacyAsset = legacyAssetRepository.save(guardLegacyAsset);

        // single attack
        Move singleAttack = Move.builder()
                .type(MoveType.SINGLE_ATTACK)
                .name("single-attack")
                .info("single attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .hitCount(1)
                .actor(character)
                .build();
        singleAttack = moveRepository.save(singleAttack);

//        LegacyAsset singleAttackLegacyAsset = LegacyAsset.builder()
//                .effectVideoSrc(getEffectVideoSrc(nameEn, MoveType.SINGLE_ATTACK))
//                .motionVideoSrc(character.isLeaderCharacter() ? getMotionVideoSrc(nameEn, MoveType.SINGLE_ATTACK) : null) // 주인공은 모션 별도
//                .seAudioSrc(getSeAudioSrc(nameEn, MoveType.SINGLE_ATTACK))
//                .move(singleAttack)
//                .build();
//        singleAttackLegacyAsset = legacyAssetRepository.save(singleAttackLegacyAsset);

        // double attack
        Move doubleAttack = Move.builder()
                .type(MoveType.DOUBLE_ATTACK)
                .name("double-attack")
                .info("double attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .hitCount(2)
                .actor(character)
                .build();
        doubleAttack = moveRepository.save(doubleAttack);

//        LegacyAsset doubleAttackLegacyAsset = LegacyAsset.builder()
//                .effectVideoSrc(getEffectVideoSrc(nameEn, MoveType.DOUBLE_ATTACK))
//                .motionVideoSrc(character.isLeaderCharacter() ? getMotionVideoSrc(nameEn, MoveType.DOUBLE_ATTACK) : null) // 주인공은 모션 별도
//                .seAudioSrc(getSeAudioSrc(nameEn, MoveType.DOUBLE_ATTACK))
//                .move(doubleAttack)
//                .build();
//        doubleAttackLegacyAsset = legacyAssetRepository.save(doubleAttackLegacyAsset);

        // triple attack
        Move tripleAttack = Move.builder()
                .type(MoveType.TRIPLE_ATTACK)
                .name("triple-attack")
                .info("triple attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .hitCount(3)
                .actor(character)
                .build();
        tripleAttack = moveRepository.save(tripleAttack);

//        LegacyAsset tripleAttackLegacyAsset = LegacyAsset.builder()
//                .effectVideoSrc(getEffectVideoSrc(nameEn, MoveType.TRIPLE_ATTACK))
//                .motionVideoSrc(character.isLeaderCharacter() ? getMotionVideoSrc(nameEn, MoveType.TRIPLE_ATTACK) : null) // 주인공은 모션 별도
//                .seAudioSrc(getSeAudioSrc(nameEn, MoveType.TRIPLE_ATTACK))
//                .move(tripleAttack)
//                .build();
//        tripleAttackLegacyAsset = legacyAssetRepository.save(tripleAttackLegacyAsset);

        return ResponseEntity.ok(InsertResponse.ok(character.getId()));
    }

    @PostMapping("/insert/charge-attack")
    public ResponseEntity<InsertResponse> insertChargeAttack(@RequestBody ChargeAttackRequest request) {
        log.info("chargeAttackReuqest: {}", request);

        Character character = characterRepository.findById(request.getCharacterId()).orElseThrow();
        Move chargeAttack = Move.builder()
                .name(request.getName())
                .type(MoveType.CHARGE_ATTACK_DEFAULT)
                .info(request.getInfo())
                .elementType(character.getElementType())
                .damageRate(4.5) // 일단 극대 캐릭의 경우 따로 수정
                .hitCount(1)
                .actor(character)
                .build();
        chargeAttack = moveRepository.save(chargeAttack);

//        LegacyAsset chargeAttackLegacyAsset = LegacyAsset.builder()
//                .effectVideoSrc(getEffectVideoSrc(character.getNameEn(), MoveType.CHARGE_ATTACK_DEFAULT))
//                .seAudioSrc(getSeAudioSrc(character.getNameEn(), MoveType.CHARGE_ATTACK_DEFAULT)) // 보이스 포함
//                .move(chargeAttack)
//                .build();
//        chargeAttackLegacyAsset = legacyAssetRepository.save(chargeAttackLegacyAsset);

        // 스테이터스
        final Move chargeAttackFinal = chargeAttack;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            // 스테이터스
            Status statusEntity = Status.builder()
                    .type(StatusType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .target(StatusTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getStatusIconSrcs(character.getNameEn(), MoveType.CHARGE_ATTACK_DEFAULT, statusOrder, status.getMaxLevel()))
                    .move(chargeAttackFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(character.getId()));
    }

    @PostMapping("/insert/ability")
    public ResponseEntity<InsertResponse> insertAbility(@RequestBody AbilityInsertRequest request) {
        log.info("request: {}", request);
        MoveType moveType = MoveType.valueOf(request.getType());
        boolean hasMotion = Boolean.parseBoolean(request.getHasMotion());
        boolean hasSupportAbilityEffect = Boolean.parseBoolean(request.getHasSupportAbilityEffect());
        boolean hasEffect = moveType.getParentType() != MoveType.SUPPORT_ABILITY || hasSupportAbilityEffect; // 서포아비가 아니거나, 서포아비임에도 이펙트가 있는 경우는 이펙트 존재

        Character character = characterRepository.findById(request.getCharacterId()).orElseThrow();
        String nameEn = character.getNameEn();

        Move ability = Move.builder()
                .type(moveType)
                .name(request.getName())
                .info(request.getInfo())
                .elementType(character.getElementType())
                .damageRate(request.getDamageRate())
                .hitCount(request.getHitCount())
                .coolDown(request.getCoolDown())
                .actor(character)
                .build();
        ability = moveRepository.save(ability);
        log.info("ability = {}", ability);

//        LegacyAsset abilityLegacyAsset = LegacyAsset.builder()
//                .move(ability)
//                .effectVideoSrc(hasEffect ? getEffectVideoSrc(nameEn, moveType) : null)
//                .motionVideoSrc(hasMotion ? getMotionVideoSrc(nameEn, moveType) : null)
//                .seAudioSrc(hasEffect ? getSeAudioSrc(nameEn, moveType) : null)
//                .voiceAudioSrc(hasEffect ? getVoiceAudioSrc(nameEn, moveType) : null)
//                .iconImageSrc(moveType.getParentType() != MoveType.SUPPORT_ABILITY ? getAbilityIconSrc(nameEn, moveType) : null)
//                .build();
//        abilityLegacyAsset = legacyAssetRepository.save(abilityLegacyAsset);

        // Status 저장
        final Move abilityFinal = ability;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            Status statusEntity = Status.builder()
                    .type(StatusType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .target(StatusTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getStatusIconSrcs(nameEn, moveType, statusOrder, status.getMaxLevel()))
                    .move(abilityFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(character.getId()));
    }

    @PostMapping("/insert-character-asset")
    public ResponseEntity<InsertResponse> insertCharacterAsset(@RequestBody CharacterAssetInsertRequest request) {
        log.info("characterAssetRequest: {}", request);
        Long inputCharacterId = request.getCharacterId();
        Character character = characterRepository.findById(inputCharacterId).orElseThrow(() -> new IllegalArgumentException("character 없음, id = " + inputCharacterId));
        Long characterId = character.getId();

        String assetName = request.getAssetName();
        String cjsName = request.getCjsName();
        int chargeAttackStartFrame = request.getChargeAttackStartFrame();

        AssetType assetType = request.getAssetType();
        Long moveId = null;
        if (assetType.isAbility()) {
            Move move = character.getMoves().get(MoveType.valueOf(assetType.name()));
            if (move == null) throw new IllegalArgumentException("어빌리티에 대응하는 move 없음, assetType = " + assetType);
            moveId = move.getId();
        }

        String rootCjsName = request.getRootCjsName();
        if (!StringUtils.hasText(rootCjsName)) { // rootCjsName 입력 안됬을때,
            if (assetType == AssetType.ACTOR) throw new IllegalArgumentException("rootCjsName 이 입력되지 않음"); // ACTOR 아니면 오류
        } else {
            rootCjsName = cjsName; // ACTOR 면 자신의 cjsName 이 곧 rootCjsName
        }

        Asset asset = Asset.builder()
                .actorId(characterId)
                .type(assetType)
                .moveId(moveId)
                .name(assetName)
                .cjsName(cjsName)
                .rootCjsName(rootCjsName)
                .chargeAttackSkipStartFrame(chargeAttackStartFrame)
                .build();
        assetRepository.save(asset);

        return ResponseEntity.ok(InsertResponse.ok(rootCjsName));
    }

    @PostMapping("/insert/summon")
    public ResponseEntity<InsertResponse> insertSummon(@RequestBody SummonInsertRequest request) {
        log.info("summonRequest: {}", request);
        String nameEn = request.getNameEn();

        // 소환용 캐릭터 ID = 6 으로 고정됨
        Character character = characterRepository.findById(request.getCharacterId()).orElseThrow();
        Move summon = Move.builder()
                .name(request.getName())
                .type(MoveType.SUMMON_DEFAULT)
                .info(request.getInfo())
                .elementType(request.getElementType())
                .damageRate(request.getDamageRate())
                .coolDown(request.getCoolDown())
                .hitCount(request.getHitCount())
                .actor(character)
                .build();
        summon = moveRepository.save(summon);

        String attackCjsName = request.getCjsName() + "_attack";
        String damageCjsName = request.getCjsName() + "_damage";
        Asset attackAsset = Asset.builder()
                .type(AssetType.SPECIAL)
                .moveId(summon.getId())
                .name(request.getName())
                .cjsName(attackCjsName)
                .rootCjsName(attackCjsName)
                .build();
        Asset damageAsset = Asset.builder()
                .type(AssetType.SPECIAL)
                .moveId(summon.getId())
                .name(request.getName())
                .cjsName(damageCjsName)
                .rootCjsName(attackCjsName) // root 는 attack 으로
                .build();
        assetRepository.saveAll(List.of(attackAsset, damageAsset));

//        LegacyAsset summonLegacyAsset = LegacyAsset.builder()
//                .iconImageSrc(getBattlePortraitSrc(nameEn)) // 얘는 portrait 가 이걸로
//                .effectVideoSrc(getEffectVideoSrc(nameEn, MoveType.SUMMON_DEFAULT))
//                .seAudioSrc(getSeAudioSrc(nameEn, MoveType.SUMMON_DEFAULT))
//                .move(summon)
//                .build();
//        summonLegacyAsset = legacyAssetRepository.save(summonLegacyAsset);

        // 스테이터스
        final Move summonFinal = summon;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            // 스테이터스
            Status statusEntity = Status.builder()
                    .type(StatusType.valueOf(status.getType()))
                    .name(status.getName())
                    .target(StatusTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getSummonStatusIconSrcs(nameEn, statusOrder, status.getMaxLevel()))
                    .move(summonFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(character.getId()));
    }

}
