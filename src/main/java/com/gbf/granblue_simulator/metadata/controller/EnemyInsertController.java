package com.gbf.granblue_simulator.metadata.controller;

import com.gbf.granblue_simulator.metadata.controller.character.EnemyAssetInsertRequest;
import com.gbf.granblue_simulator.metadata.controller.response.EnemyInsertResponse;
import com.gbf.granblue_simulator.metadata.controller.response.InsertResponse;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisualType;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelType;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import com.gbf.granblue_simulator.metadata.repository.*;
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

    private final BaseEnemyRepository baseEnemyRepository;
    private final MoveRepository moveRepository;
    private final OmenRepository omenRepository;
    private final BaseStatusEffectRepository baseStatusEffectRepository;
    private final StatusModifierRepository statusModifierRepository;
    private final OmenCancelCondRepository omenCancelCondRepository;
    private final MoveVisualRepository moveVisualRepository;

    @PostMapping("/insert/enemy")
    public EnemyInsertResponse insertEnemy(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyInsertRequest request) {
        log.info("enemyInsertRequest: {}", request);

        BaseEnemy baseEnemy = BaseEnemy.builder()
                .name(request.getName())
                .nameEn(request.getNameEn())
                .elementType(request.getElementType())
//                .hpTriggers(Arrays.stream(request.getHpTriggers().split(",")).map(String::trim).map(Integer::parseInt).toList())
                .build();
        baseEnemyRepository.save(baseEnemy);

        Move dead = Move.builder()
                .name(null)
                .type(MoveType.DEAD)
                .info("dead")
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(dead);

        Move formChange = Move.builder()
                .name("폼 체인지")
                .type(MoveType.FORM_CHANGE_DEFAULT)
                .info("form change")
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(formChange);

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-attack")
    public EnemyInsertResponse insertAttack(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyAttackRequest request) {
        log.info("enemyAttackRequest: {}", request);
        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move singleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.SINGLE_ATTACK)
                .info("single attack")
                .hitCount(1)
                .isAllTarget(request.isAllTarget())
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(singleAttack);

        Move doubleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.DOUBLE_ATTACK)
                .info("double attack")
                .hitCount(2)
                .isAllTarget(request.isAllTarget())
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(doubleAttack);

        Move tripleAttack = Move.builder()
                .name(null)
                .elementType(request.getElementType())
                .type(MoveType.TRIPLE_ATTACK)
                .info("triple attack")
                .hitCount(3)
                .isAllTarget(request.isAllTarget())
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(tripleAttack);

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-idle")
    public EnemyInsertResponse insertIdle(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyIdleRequest request) {
        log.info("enemyIdleRequest: {}", request);
        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move idle = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("idle")
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(idle);

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-damaged")
    public EnemyInsertResponse insertDamaged(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyDamagedRequest request) {
        log.info("enemyDamagedRequest: {}", request);
        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move damaged = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("damaged")
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(damaged);

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-break")
    public EnemyInsertResponse insertBreak(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyBreakRequest request) {
        log.info("enemyBreakRequest: {}", request);
        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move breakMove = Move.builder()
                .name(null)
                .type(MoveType.valueOf(request.getType()))
                .info("break")
                .baseActor(baseEnemy)
                .build();
        moveRepository.save(breakMove);

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-standby")
    public EnemyInsertResponse insertStandby(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyStandbyInsertRequest request) {
        log.info("enemyStandbyRequest: {}", request);
        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move standby = Move.builder()
                .name(request.getOmen().getName())
                .type(MoveType.valueOf(request.getType()))
                .info(request.getOmen().getInfo())
                .baseActor(baseEnemy)
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

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-charge-attack")
    public EnemyInsertResponse insertChargeAttack(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyChargeAttackRequest request) {
        log.info("chargeAttackReuqest: {}", request);

        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
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
                .baseActor(baseEnemy)
                .build();
        chargeAttack = moveRepository.save(chargeAttack);

        // 스테이터스
        final Move chargeAttackFinal = chargeAttack;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            // 스테이터스
            BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                    .type(StatusEffectType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .effectText(status.getEffectText())
                    .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
//                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(chargeAttackFinal)
                    .build();
            log.info("statusEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                if (splitStatusEffect.length < 2) return;
                StatusModifierType statusModifierType = StatusModifierType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusModifier statusModifierEntity = StatusModifier.builder()
                        .baseStatusEffect(baseStatusEffectEntity)
                        .type(statusModifierType)
                        .value(statusEffectValue)
                        .build();
                statusModifierRepository.save(statusModifierEntity);
            });
        });

        return EnemyInsertResponse.ok(baseEnemy.getId());
    }

    @PostMapping("/insert/enemy-ability")
    public ResponseEntity<InsertResponse> insertAbility(@RequestBody com.gbf.granblue_simulator.metadata.controller.enemy.EnemyAbilityRequest request) {
        log.info("request: {}", request);

        BaseEnemy baseEnemy = baseEnemyRepository.findById(request.getEnemyId()).orElseThrow();
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
                .baseActor(baseEnemy)
                .build();
        ability = moveRepository.save(ability);
        log.info("ability = {}", ability);

        // Status 저장
        final Move abilityFinal = ability;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
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
//                    .iconSrcs(status.getIconSrcs().lines().map(String::trim).toList())
                    .move(abilityFinal)
                    .build();
            log.info("statusEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusEffects().lines().forEach(statusEffect -> {
                String[] splitStatusEffect = statusEffect.split(",");
                StatusModifierType statusModifierType = StatusModifierType.valueOf(splitStatusEffect[0].trim());
                Double statusEffectValue = Double.valueOf(splitStatusEffect[1].trim());
                StatusModifier statusModifierEntity = StatusModifier.builder()
                        .baseStatusEffect(baseStatusEffectEntity)
                        .type(statusModifierType)
                        .value(statusEffectValue)
                        .build();
                statusModifierRepository.save(statusModifierEntity);
            });
        });

        return ResponseEntity.ok(InsertResponse.ok(1L));
    }

    @PostMapping("/insert-enemy-asset")
    public ResponseEntity<InsertResponse> insertCharacterAsset(@RequestBody EnemyAssetInsertRequest request) {
        log.info("characterAssetRequest: {}", request);
        Long inputActorId = request.getActorId();
        BaseEnemy baseEnemy = baseEnemyRepository.findById(inputActorId).orElseThrow(() -> new IllegalArgumentException("enemy 없음, id = " + inputActorId));
        Long characterId = baseEnemy.getId();

        String assetName = request.getAssetName();
        String cjsName = request.getCjsName();

        EffectVisualType effectVisualType = request.getEffectVisualType();
//        Long moveId = null;
//        if (effectVisualType.isAbility()) {
//            Move move = baseEnemy.getMoves().get(MoveType.valueOf(effectVisualType.name()));
//            if (move == null) throw new IllegalArgumentException("어빌리티에 대응하는 move 없음, assetType = " + effectVisualType);
//            moveId = move.getId();
//        }

//        String rootCjsName = request.getRootCjsName();
//        if (!StringUtils.hasText(rootCjsName)) { // rootCjsName 입력 안됬을때,
//            if (moveVisualType == MoveVisualType.ACTOR) throw new IllegalArgumentException("rootCjsName 이 입력되지 않음"); // ACTOR 아니면 오류
//        } else {
//            rootCjsName = cjsName; // ACTOR 면 자신의 cjsName 이 곧 rootCjsName
//        }
//
//        MoveVisual moveVisual = MoveVisual.builder()
//                .actorId(characterId)
//                .type(moveVisualType)
//                .moveId(moveId)
//                .name(assetName)
//                .cjsName(cjsName)
//                .rootCjsName(rootCjsName)
//                .build();
//        moveVisualRepository.save(moveVisual);

//        return ResponseEntity.ok(InsertResponse.ok(rootCjsName));
        return null;

    }

}
