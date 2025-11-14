package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.dto.request.insert.character.*;
import com.gbf.granblue_simulator.controller.dto.response.insert.InsertResponse;
import com.gbf.granblue_simulator.domain.base.actor.BaseCharacter;
import com.gbf.granblue_simulator.domain.base.asset.Asset;
import com.gbf.granblue_simulator.domain.base.asset.AssetType;
import com.gbf.granblue_simulator.domain.base.statuseffect.*;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.repository.AssetRepository;
import com.gbf.granblue_simulator.repository.actor.BaseCharacterRepository;
import com.gbf.granblue_simulator.repository.move.BaseStatusEffectRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import com.gbf.granblue_simulator.repository.move.StatusModifierRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gbf.granblue_simulator.controller.dto.request.insert.InsertSrcMapper.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsertController {

    private final BaseCharacterRepository baseCharacterRepository;
    private final MoveRepository moveRepository;
    private final BaseStatusEffectRepository baseStatusEffectRepository;
    private final StatusModifierRepository statusModifierRepository;
    private final AssetRepository assetRepository;

    @PostMapping("/insert/character")
    public ResponseEntity<InsertResponse> insertCharacter(@RequestBody CharacterInsertRequest characterInsertRequest) {
        log.info("characterInsertRequest: {}", characterInsertRequest);
        String nameEn = characterInsertRequest.getNameEn();

        BaseCharacter baseCharacter = BaseCharacter.builder()
                .name(characterInsertRequest.getName())
                .nameEn(nameEn)
                .battlePortraitSrc(getBattlePortraitSrc(nameEn))
                .elementType(characterInsertRequest.getElementType())
                .isLeaderCharacter(Boolean.parseBoolean(characterInsertRequest.getIsLeaderCharacter()))
                .build();
        baseCharacter.initCharacterBaseStatus();
        baseCharacter = baseCharacterRepository.save(baseCharacter);
        log.info("savedChar = {}", baseCharacter);

        // idle
        Move idle = Move.builder()
                .type(MoveType.IDLE_DEFAULT)
                .name(null)
                .info("idle")
                .elementType(baseCharacter.getElementType())
                .baseActor(baseCharacter)
                .build();
        idle = moveRepository.save(idle);

        // guard
        Move guard = Move.builder()
                .type(MoveType.GUARD_DEFAULT)
                .name(null)
                .info("guard")
                .elementType(baseCharacter.getElementType())
                .damageRate(0.0)
                .coolDown(0)
                .baseActor(baseCharacter)
                .build();
        moveRepository.save(guard);

        // single attack
        Move singleAttack = Move.builder()
                .type(MoveType.SINGLE_ATTACK)
                .name(null)
                .info("single attack")
                .elementType(baseCharacter.getElementType())
                .damageRate(1.0)
                .hitCount(1)
                .baseActor(baseCharacter)
                .build();
        singleAttack = moveRepository.save(singleAttack);

        // double attack
        Move doubleAttack = Move.builder()
                .type(MoveType.DOUBLE_ATTACK)
                .name(null)
                .info("double attack")
                .elementType(baseCharacter.getElementType())
                .damageRate(1.0)
                .hitCount(2)
                .baseActor(baseCharacter)
                .build();
        doubleAttack = moveRepository.save(doubleAttack);

        // triple attack
        Move tripleAttack = Move.builder()
                .type(MoveType.TRIPLE_ATTACK)
                .name(null)
                .info("triple attack")
                .elementType(baseCharacter.getElementType())
                .damageRate(1.0)
                .hitCount(3)
                .baseActor(baseCharacter)
                .build();
        tripleAttack = moveRepository.save(tripleAttack);

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

    @PostMapping("/insert/charge-attack")
    public ResponseEntity<InsertResponse> insertChargeAttack(@RequestBody ChargeAttackRequest request) {
        log.info("chargeAttackReuqest: {}", request);

        BaseCharacter baseCharacter = baseCharacterRepository.findById(request.getCharacterId()).orElseThrow();
        Move chargeAttack = Move.builder()
                .name(request.getName())
                .type(MoveType.CHARGE_ATTACK_DEFAULT)
                .info(request.getInfo())
                .elementType(baseCharacter.getElementType())
                .damageRate(4.5) // 일단 극대 캐릭의 경우 따로 수정
                .hitCount(1)
                .baseActor(baseCharacter)
                .build();
        chargeAttack = moveRepository.save(chargeAttack);

        // 스테이터스
        final Move chargeAttackFinal = chargeAttack;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            // 스테이터스
            BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                    .type(StatusEffectType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getStatusIconSrcs(baseCharacter.getNameEn(), MoveType.CHARGE_ATTACK_DEFAULT, statusOrder, status.getMaxLevel()))
                    .move(chargeAttackFinal)
                    .build();
            log.info("statusEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusModifierType statusModifierType = StatusModifierType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusModifier statusModifierEntity = StatusModifier.builder()
                        .baseStatusEffect(baseStatusEffectEntity)
                        .type(statusModifierType)
                        .value(statusEffectValue)
                        .build();
                statusModifierRepository.save(statusModifierEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

    @PostMapping("/insert/ability")
    public ResponseEntity<InsertResponse> insertAbility(@RequestBody AbilityInsertRequest request) {
        log.info("request: {}", request);
        MoveType moveType = MoveType.valueOf(request.getType());
        boolean hasMotion = Boolean.parseBoolean(request.getHasMotion());
        boolean hasSupportAbilityEffect = Boolean.parseBoolean(request.getHasSupportAbilityEffect());
        boolean hasEffect = moveType.getParentType() != MoveType.SUPPORT_ABILITY || hasSupportAbilityEffect; // 서포아비가 아니거나, 서포아비임에도 이펙트가 있는 경우는 이펙트 존재

        BaseCharacter baseCharacter = baseCharacterRepository.findById(request.getCharacterId()).orElseThrow();
        String nameEn = baseCharacter.getNameEn();

        Move ability = Move.builder()
                .type(moveType)
                .name(request.getName())
                .info(request.getInfo())
                .elementType(baseCharacter.getElementType())
                .damageRate(request.getDamageRate())
                .hitCount(request.getHitCount())
                .coolDown(request.getCoolDown())
                .baseActor(baseCharacter)
                .build();
        ability = moveRepository.save(ability);
        log.info("ability = {}", ability);

        // Status 저장
        final Move abilityFinal = ability;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                    .type(StatusEffectType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getStatusIconSrcs(nameEn, moveType, statusOrder, status.getMaxLevel()))
                    .move(abilityFinal)
                    .build();
            log.info("statusEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusModifierType statusModifierType = StatusModifierType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusModifier statusModifierEntity = StatusModifier.builder()
                        .baseStatusEffect(baseStatusEffectEntity)
                        .type(statusModifierType)
                        .value(statusEffectValue)
                        .build();
                statusModifierRepository.save(statusModifierEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

    @PostMapping("/insert-character-asset")
    public ResponseEntity<InsertResponse> insertCharacterAsset(@RequestBody CharacterAssetInsertRequest request) {
        log.info("characterAssetRequest: {}", request);
        Long inputCharacterId = request.getCharacterId();
        BaseCharacter baseCharacter = baseCharacterRepository.findById(inputCharacterId).orElseThrow(() -> new IllegalArgumentException("character 없음, id = " + inputCharacterId));
        Long characterId = baseCharacter.getId();

        String assetName = request.getAssetName();
        String cjsName = request.getCjsName();
        int chargeAttackStartFrame = request.getChargeAttackStartFrame();

        AssetType assetType = request.getAssetType();
        Long moveId = null;
        if (assetType.isAbility()) {
            Move move = baseCharacter.getMoves().get(MoveType.valueOf(assetType.name()));
            if (move == null) throw new IllegalArgumentException("어빌리티에 대응하는 move 없음, assetType = " + assetType);
            moveId = move.getId();
        }

        String rootCjsName = request.getRootCjsName();
        if (!StringUtils.hasText(rootCjsName)) { // rootCjsName 입력 안됬을때,
            if (assetType == AssetType.ACTOR)
                throw new IllegalArgumentException("rootCjsName 이 입력되지 않음"); // ACTOR 아니면 오류
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
                .chargeAttackStartFrame(chargeAttackStartFrame)
                .build();
        assetRepository.save(asset);

        return ResponseEntity.ok(InsertResponse.ok(rootCjsName));
    }

    @PostMapping("/insert/summon")
    public ResponseEntity<InsertResponse> insertSummon(@RequestBody SummonInsertRequest request) {
        log.info("summonRequest: {}", request);
        String nameEn = request.getNameEn();

        // 소환용 캐릭터 ID = 6 으로 고정됨
        BaseCharacter baseCharacter = baseCharacterRepository.findById(request.getCharacterId()).orElseThrow();
        Move summon = Move.builder()
                .name(request.getName())
                .type(MoveType.SUMMON_DEFAULT)
                .info(request.getInfo())
                .elementType(request.getElementType())
                .damageRate(request.getDamageRate())
                .coolDown(request.getCoolDown())
                .hitCount(request.getHitCount())
                .baseActor(baseCharacter)
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

        // 스테이터스
        final Move summonFinal = summon;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            int statusOrder = request.getStatuses().indexOf(status) + 1;
            // 스테이터스
            BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                    .type(StatusEffectType.valueOf(status.getType()))
                    .name(status.getName())
                    .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .iconSrcs(getSummonStatusIconSrcs(nameEn, statusOrder, status.getMaxLevel()))
                    .move(summonFinal)
                    .build();
            log.info("statusEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusModifierType statusModifierType = StatusModifierType.valueOf(splitStatusEffect[0].trim());
                double statusEffectValue = Double.parseDouble(splitStatusEffect[1].trim());
                StatusModifier statusModifierEntity = StatusModifier.builder()
                        .baseStatusEffect(baseStatusEffectEntity)
                        .type(statusModifierType)
                        .value(statusEffectValue)
                        .build();
                statusModifierRepository.save(statusModifierEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

}
