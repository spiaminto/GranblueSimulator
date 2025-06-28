package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.request.insert.character.*;
import com.gbf.granblue_simulator.controller.request.insert.character.AbilityRequest;
import com.gbf.granblue_simulator.controller.response.InsertResponse;
import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.Character;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.asset.Asset;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.repository.actor.CharacterRepository;
import com.gbf.granblue_simulator.repository.move.AssetRepository;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsertController {

    private final CharacterRepository characterRepository;
    private final AssetRepository assetRepository;
    private final MoveRepository moveRepository;
    private final StatusRepository statusRepository;
    private final StatusEffectRepository statusEffectRepository;

    @PostMapping("/insert/character")
    public ResponseEntity<InsertResponse> insertCharacter(@RequestBody CharacterInsertRequest characterInsertRequest) {
        log.info("characterInsertRequest: {}", characterInsertRequest);
        Character character = Character.builder()
                .name(characterInsertRequest.getName())
                .nameEn(characterInsertRequest.getNameEn())
                .battlePortraitSrc(characterInsertRequest.getBattlePortraitSrc())
                .elementType(characterInsertRequest.getElementType())
                .isMainCharacter(Boolean.parseBoolean(characterInsertRequest.getIsMainCharacter()))
                .build();
        character = characterRepository.save(character);
        log.info("savedChar = {}", character);

        return ResponseEntity.ok(InsertResponse.ok(character.getId()));
    }

    @PostMapping("/insert/idle-and-attack")
    public ResponseEntity<InsertResponse> insertIdleAndAttack(@RequestBody IdleAndAttackRequest idleAndAttackRequest) {
        log.info("idleAttackREquest: {}", idleAndAttackRequest);
        Character character = characterRepository.findById(idleAndAttackRequest.getCharacterId()).orElseThrow();

        // idle
        Move idle = Move.builder()
                .type(MoveType.IDLE_DEFAULT)
                .name("idle")
                .info("idle")
                .elementType(character.getElementType())
                .damageRate(0.0)
                .coolDown(0)
                .duration(null)
                .actor(character)
                .build();
        idle = moveRepository.save(idle);

        Asset idleAsset = Asset.builder()
                .motionVideoSrc(idleAndAttackRequest.getIdleEffectVideoSrc())
                .move(idle)
                .build();
        idleAsset = assetRepository.save(idleAsset);

        // guard
        Move guard = Move.builder()
                .type(MoveType.GUARD)
                .name("guard")
                .info("guard")
                .elementType(character.getElementType())
                .damageRate(0.0)
                .coolDown(0)
                .duration(null)
                .actor(character)
                .build();
        moveRepository.save(guard);

        Asset guardAsset = Asset.builder()
                .move(guard)
                .build();
        guardAsset = assetRepository.save(guardAsset);

        // single attack
        Move singleAttack = Move.builder()
                .type(MoveType.SINGLE_ATTACK)
                .name("single-attack")
                .info("single attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .coolDown(null)
                .duration(null)
                .actor(character)
                .build();
        singleAttack = moveRepository.save(singleAttack);

        Asset singleAttackAsset = Asset.builder()
                .effectVideoSrc(idleAndAttackRequest.getSingleAttackEffectVideoSrc())
                .seAudioSrc(idleAndAttackRequest.getSingleAttackSeAudioSrc())
                .move(singleAttack)
                .build();
        singleAttackAsset = assetRepository.save(singleAttackAsset);

        // double attack
        Move doubleAttack = Move.builder()
                .type(MoveType.DOUBLE_ATTACK)
                .name("double-attack")
                .info("double attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .coolDown(null)
                .duration(null)
                .actor(character)
                .build();
        doubleAttack = moveRepository.save(doubleAttack);

        Asset doubleAttackAsset = Asset.builder()
                .effectVideoSrc(idleAndAttackRequest.getDoubleAttackEffectVideoSrc())
                .seAudioSrc(idleAndAttackRequest.getDoubleAttackSeAudioSrc())
                .move(doubleAttack)
                .build();
        doubleAttackAsset = assetRepository.save(doubleAttackAsset);

        // triple attack
        Move tripleAttack = Move.builder()
                .type(MoveType.TRIPLE_ATTACK)
                .name("triple-attack")
                .info("triple attack")
                .elementType(character.getElementType())
                .damageRate(1.0)
                .coolDown(null)
                .duration(null)
                .actor(character)
                .build();
        tripleAttack = moveRepository.save(tripleAttack);

        Asset tripleAttackAsset = Asset.builder()
                .effectVideoSrc(idleAndAttackRequest.getTripleAttackEffectVideoSrc())
                .seAudioSrc(idleAndAttackRequest.getTripleAttackSeAudioSrc())
                .move(tripleAttack)
                .build();
        tripleAttackAsset = assetRepository.save(tripleAttackAsset);

        return ResponseEntity.ok(InsertResponse.ok(1L));
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
                .coolDown(null)
                .duration(null)
                .actor(character)
                .build();
        chargeAttack = moveRepository.save(chargeAttack);

        Asset chargeAttackAsset = Asset.builder()
                .effectVideoSrc(request.getEffectVideoSrc())
                .seAudioSrc(request.getSeAudioSrc())
                .move(chargeAttack)
                .build();
        chargeAttackAsset = assetRepository.save(chargeAttackAsset);

        // 스테이터스
        final Move chargeAttackFinal = chargeAttack;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
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
                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(chargeAttackFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(1L));
    }

    @PostMapping("/insert/ability")
    public ResponseEntity<InsertResponse> insertAbility(@RequestBody AbilityRequest request) {
        log.info("request: {}", request);

        Character character = characterRepository.findById(request.getCharacterId()).orElseThrow();
        Move ability = Move.builder()
                .type(MoveType.valueOf(request.getType()))
                .name(request.getName())
                .info(request.getInfo())
                .elementType(character.getElementType())
                .damageRate(request.getDamageRate())
                .hitCount(request.getHitCount())
                .coolDown(request.getCoolDown())
                .duration(request.getDuration())
                .damageRate(null)
                .actor(character)
                .build();
        ability = moveRepository.save(ability);
        log.info("ability = {}", ability);

        Asset abilityAsset = Asset.builder()
                .move(ability)
                .effectVideoSrc(request.getEffectVideoSrc())
                .motionVideoSrc(request.getMotionVideoSrc())
                .motionVideoFull(request.isMotionVideoFull())
                .seAudioSrc(request.getSeAudioSrc())
                .voiceAudioSrc(request.getVoiceAudioSrc())
                .iconImageSrc(request.getIconSrc())
                .build();
        abilityAsset = assetRepository.save(abilityAsset);

        // Status 저장
        final Move abilityFinal = ability;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
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
                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(abilityFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(1L));
    }

    @PostMapping("/insert/summon")
    public ResponseEntity<InsertResponse> insertSummon(@RequestBody SummonInsertRequest request) {
        log.info("summonRequest: {}", request);

        // 소환용 캐릭터 ID = 6 으로 고정됨
        Character character = characterRepository.findById(request.getCharacterId()).orElseThrow();
        Move summon = Move.builder()
                .name(request.getName())
                .type(MoveType.SUMMON)
                .info(request.getInfo())
                .elementType(request.getElementType())
                .damageRate(request.getDamageRate())
                .coolDown(request.getCoolDown())
                .hitCount(request.getHitCount())
                .duration(null)
                .actor(character)
                .build();
        summon = moveRepository.save(summon);

        Asset summonAsset = Asset.builder()
                .iconImageSrc(request.getIconSrc())
                .effectVideoSrc(request.getEffectVideoSrc())
                .seAudioSrc(request.getSeAudioSrc())
                .move(summon)
                .build();
        summonAsset = assetRepository.save(summonAsset);

        // 스테이터스
        final Move summonFinal = summon;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
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
                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(summonFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(1L));
    }

//    @RequestMapping("/insert/fatalchain")
    // for manual insert
    public void insertFatalChain () {
        // 소환용 캐릭터 ID = 6 으로 고정됨
        Character character = characterRepository.findById(6L).orElseThrow();
        SummonInsertRequest request = new SummonInsertRequest();
        request.setCharacterId(6L);
        request.setName("페이탈체인");
        request.setInfo("적에게 20만 고정데미지, 페이탈 체인 효과 부여");
        request.setDamageRate(0.0);
        request.setCoolDown(0);
        request.setHitCount(1);

        request.setEffectVideoSrc("/static/assets/video/gl/gl-fatal-dark.webm");
        request.setSeAudioSrc("/static/assets/audio/gl/gl-fatal-dark.mp3");
        request.setElementType(ElementType.DARK);
        
        Move summon = Move.builder()
                .name(request.getName())
                .type(MoveType.FATAL_CHAIN)
                .info(request.getInfo())
                .elementType(request.getElementType())
                .damageRate(request.getDamageRate())
                .coolDown(request.getCoolDown())
                .hitCount(request.getHitCount())
                .duration(null)
                .actor(character)
                .damageConstant(200000)
                .build();
        summon = moveRepository.save(summon);

        Asset summonAsset = Asset.builder()
                .iconImageSrc(request.getIconSrc())
                .effectVideoSrc(request.getEffectVideoSrc())
                .seAudioSrc(request.getSeAudioSrc())
                .move(summon)
                .build();
        summonAsset = assetRepository.save(summonAsset);

        // 스테이터스

        moveRepository.findById(summon.getId()).orElseThrow();
        SummonInsertRequest.SummonStatus fatalStatus = new SummonInsertRequest.SummonStatus();
        fatalStatus.setType("DEBUFF");
        fatalStatus.setName("페이탈체인");
        fatalStatus.setTargetType("ENEMY");
        fatalStatus.setMaxLevel(0);
        fatalStatus.setEffectText("페이탈체인");
        fatalStatus.setStatusText("받는데미지가 증가한 상태 (별항, 해제불가, 필중)");
        fatalStatus.setDuration(1);
        fatalStatus.setRemovable("false");
        fatalStatus.setIsResistible("false");
        fatalStatus.setIconSrcs("/static/assets/img/gl/status-fatal-dark.png");
        fatalStatus.setStatusEffects("TAKEN_AMPLIFY_DAMAGE_UP_UNIQUE, 0.2");
        request.setStatuses(List.of(fatalStatus));

        final Move summonFinal = summon;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
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
                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(summonFinal)
                    .build();
            log.info("statusEntity = {}", statusEntity);
            statusRepository.save(statusEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusEffectType statusEffectType = StatusEffectType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusEffect statusEffectEntity = StatusEffect.builder()
                        .status(statusEntity)
                        .type(statusEffectType)
                        .value(statusEffectValue)
                        .build();
                statusEffectRepository.save(statusEffectEntity);
            });
        });

    }

}
