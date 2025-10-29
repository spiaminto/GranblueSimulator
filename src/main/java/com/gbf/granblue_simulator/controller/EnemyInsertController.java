package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.request.insert.character.EnemyAssetInsertRequest;
import com.gbf.granblue_simulator.controller.request.insert.enemy.*;
import com.gbf.granblue_simulator.controller.response.insert.EnemyInsertResponse;
import com.gbf.granblue_simulator.controller.response.insert.InsertResponse;
import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.asset.Asset;
import com.gbf.granblue_simulator.domain.asset.AssetType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.repository.AssetRepository;
import com.gbf.granblue_simulator.repository.actor.EnemyRepository;
import com.gbf.granblue_simulator.repository.move.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EnemyInsertController {

    private final EnemyRepository enemyRepository;
    private final MoveRepository moveRepository;
    private final OmenRepository omenRepository;
    private final StatusRepository statusRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final OmenCancelCondRepository omenCancelCondRepository;
    private final AssetRepository assetRepository;

    @PostMapping("/insert/enemy")
    public EnemyInsertResponse insertEnemy(@RequestBody EnemyInsertRequest request) {
        log.info("enemyInsertRequest: {}", request);

        Enemy enemy = Enemy.builder()
                .name(request.getName())
                .nameEn(request.getNameEn())
                .elementType(request.getElementType())
                .backgroundImageSrc(request.getBackgroundImageSrc())
                .hpTriggers(Arrays.stream(request.getHpTriggers().split(",")).map(String::trim).map(Integer::parseInt).toList())
                .bgmTriggers(Arrays.stream(request.getBgmTriggers().split(",")).map(String::trim).map(Integer::parseInt).toList())
                .bgmSrcs(request.getBgmSrcs().lines().map(String::trim).toList())
                .build();
        enemyRepository.save(enemy);

        Move dead = Move.builder()
                .name(null)
                .type(MoveType.DEAD)
                .info("dead")
                .actor(enemy)
                .build();
        moveRepository.save(dead);

        Move formChange = Move.builder()
                .name("폼 체인지")
                .type(MoveType.FORM_CHANGE_DEFAULT)
                .info("form change")
                .actor(enemy)
                .build();
        moveRepository.save(formChange);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-attack")
    public EnemyInsertResponse insertAttack(@RequestBody EnemyAttackRequest request) {
        log.info("enemyAttackRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move singleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.SINGLE_ATTACK)
                .info("single attack")
                .hitCount(1)
                .isAllTarget(request.isAllTarget())
                .actor(enemy)
                .build();
        moveRepository.save(singleAttack);

        Move doubleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.DOUBLE_ATTACK)
                .info("double attack")
                .hitCount(2)
                .isAllTarget(request.isAllTarget())
                .actor(enemy)
                .build();
        moveRepository.save(doubleAttack);

        Move tripleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.TRIPLE_ATTACK)
                .info("triple attack")
                .hitCount(3)
                .isAllTarget(request.isAllTarget())
                .actor(enemy)
                .build();
        moveRepository.save(tripleAttack);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-idle")
    public EnemyInsertResponse insertIdle(@RequestBody EnemyIdleRequest request) {
        log.info("enemyIdleRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move idle = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("idle")
                .actor(enemy)
                .build();
        moveRepository.save(idle);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-damaged")
    public EnemyInsertResponse insertDamaged(@RequestBody EnemyDamagedRequest request) {
        log.info("enemyDamagedRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move damaged = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("damaged")
                .actor(enemy)
                .build();
        moveRepository.save(damaged);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-break")
    public EnemyInsertResponse insertBreak(@RequestBody EnemyBreakRequest request) {
        log.info("enemyBreakRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move breakMove = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("break")
                .actor(enemy)
                .build();
        moveRepository.save(breakMove);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-standby")
    public EnemyInsertResponse insertStandby(@RequestBody EnemyStandbyInsertRequest request) {
        log.info("enemyStandbyRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move standby = Move.builder()
                .name(request.getOmen().getName())
                .type(MoveType.valueOf(request.getType()))
                .info(request.getOmen().getInfo())
                .actor(enemy)
                .build();
        moveRepository.save(standby);

        Omen omen = Omen.builder()
                .name(request.getOmen().getName())
                .omenType(OmenType.valueOf(request.getOmen().getType()))
                .info(request.getOmen().getInfo())
                .triggerHps(Arrays.stream(request.getOmen().getTriggerHps().split(","))
                                .map(String::trim)
                                .map(Integer::parseInt)
                                .toList())
                .move(standby)
                .build();
        omenRepository.save(omen);
        // 해제조건
        request.getOmen().getCancelConditions().lines().forEach(cancelCondition -> {
            String[] splitCancelCondition = cancelCondition.split(",");
            OmenCancelType omenCancelType = OmenCancelType.valueOf(splitCancelCondition[0].trim());
            String omenCancelPresentInfo = splitCancelCondition[1].trim();
            Integer omenCancelValue = Integer.valueOf(splitCancelCondition[2].trim());
            OmenCancelCond omenCancel = OmenCancelCond.builder()
                    .type(omenCancelType)
                    .info(omenCancelPresentInfo)
                    .initValue(omenCancelValue)
                    .omen(omen).build();
            omenCancelCondRepository.save(omenCancel);
        });

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-charge-attack")
    public EnemyInsertResponse insertChargeAttack(@RequestBody EnemyChargeAttackRequest request) {
        log.info("chargeAttackReuqest: {}", request);

        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move chargeAttack = Move.builder()
                .name(request.getName())
                .type(MoveType.valueOf(request.getType()))
                .info(request.getInfo())
                .elementType(request.getElementType())
                .hitCount(request.getHitCount())
                .isAllTarget(Boolean.parseBoolean(request.getIsAllTarget()))
                .randomStatusCount(request.getRandomStatusCount())
                .damageRate(request.getDamageRate() + 0.0)
                .damageConstant(request.getDamageConstant())
                .actor(enemy)
                .build();
        chargeAttack = moveRepository.save(chargeAttack);

        // 스테이터스
        final Move chargeAttackFinal = chargeAttack;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            // 스테이터스
            Status statusEntity = Status.builder()
                    .type(StatusType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .effectText(status.getEffectText())
                    .target(StatusTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
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
                if (splitStatusEffect.length < 2) return;
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

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-ability")
    public ResponseEntity<InsertResponse> insertAbility(@RequestBody EnemyAbilityRequest request) {
        log.info("request: {}", request);

        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move ability = Move.builder()
                .type(MoveType.valueOf(request.getType()))
                .name(request.getName())
                .info(request.getInfo())
                .elementType(request.getElementType())
                .damageRate(request.getDamageRate())
                .damageConstant(request.getDamageConstant())
                .hitCount(request.getHitCount())
                .isAllTarget(Boolean.parseBoolean(request.getIsAllTarget()))
                .coolDown(request.getCoolDown())
                .actor(enemy)
                .build();
        ability = moveRepository.save(ability);
        log.info("ability = {}", ability);

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

    @PostMapping("/insert-enemy-asset")
    public ResponseEntity<InsertResponse> insertCharacterAsset(@RequestBody EnemyAssetInsertRequest request) {
        log.info("characterAssetRequest: {}", request);
        Long inputActorId = request.getActorId();
        Enemy enemy = enemyRepository.findById(inputActorId).orElseThrow(() -> new IllegalArgumentException("enemy 없음, id = " + inputActorId));
        Long characterId = enemy.getId();

        String assetName = request.getAssetName();
        String cjsName = request.getCjsName();

        AssetType assetType = request.getAssetType();
        Long moveId = null;
        if (assetType.isAbility()) {
            Move move = enemy.getMoves().get(MoveType.valueOf(assetType.name()));
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
                .build();
        assetRepository.save(asset);

        return ResponseEntity.ok(InsertResponse.ok(rootCjsName));
    }

}
