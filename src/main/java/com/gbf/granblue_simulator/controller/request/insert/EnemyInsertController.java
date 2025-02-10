package com.gbf.granblue_simulator.controller.request.insert;

import com.gbf.granblue_simulator.controller.request.insert.enemy.*;
import com.gbf.granblue_simulator.controller.response.EnemyInsertResponse;
import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.asset.Asset;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.repository.actor.EnemyRepository;
import com.gbf.granblue_simulator.repository.move.*;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AssetRepository assetRepository;
    private final OmenRepository omenRepository;
    private final StatusRepository statusRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final OmenCancelCondRepository omenCancelCondRepository;

    @PostMapping("/insert/enemy")
    public EnemyInsertResponse insertEnemy(@RequestBody EnemyInsertRequest request) {
        log.info("enemyInsertRequest: {}", request);

        Enemy enemy = Enemy.builder()
                .name(request.getName())
                .backgroundImageSrc(request.getBackgroundImageSrc())
                .hpTriggers(Arrays.stream(request.getHpTriggers().split(",")).map(String::trim).map(Integer::parseInt).toList())
                .bgmTriggers(Arrays.stream(request.getBgmTriggers().split(",")).map(String::trim).map(Integer::parseInt).toList())
                .bgmSrcs(request.getBgmSrcs().lines().map(String::trim).toList())
                .build();
        enemyRepository.save(enemy);

        Move dead = Move.builder()
                .name("dead")
                .type(MoveType.ENEMY_DEAD)
                .info("dead")
                .actor(enemy)
                .build();
        moveRepository.save(dead);

        Asset deadAsset = Asset.builder()
                .effectVideoSrc(request.getDeadEffectVideoSrc())
                .seAudioSrc(request.getDeadSeAudioSrc())
                .move(dead)
                .build();
        assetRepository.save(deadAsset);

        Move phaseChange = Move.builder()
                .name("phase change")
                .type(MoveType.ENEMY_PHASE_CHANGE)
                .info("phase change")
                .actor(enemy)
                .build();
        moveRepository.save(phaseChange);

        Asset phaseChangeAsset = Asset.builder()
                .effectVideoSrc(request.getPhaseChangeEffectVideoSrc())
                .seAudioSrc(request.getPhaseChangeSeAudioSrc())
                .move(phaseChange)
                .build();
        assetRepository.save(phaseChangeAsset);

        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-attack")
    public EnemyInsertResponse insertAttack(@RequestBody EnemyAttackRequest request) {
        log.info("enemyAttackRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move attack = Move.builder()
                .name("attack")
                .type(MoveType.ENEMY_ATTACK)
                .info("attack")
                .actor(enemy)
                .build();
        moveRepository.save(attack);
        Asset attackAsset = Asset.builder()
                .effectVideoSrc(request.getAttackEffectVideoSrc())
                .seAudioSrc(request.getAttackSeAudioSrc())
                .move(attack)
                .build();
        assetRepository.save(attackAsset);
        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-idle")
    public EnemyInsertResponse insertIdle(@RequestBody EnemyIdleRequest request) {
        log.info("enemyIdleRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move idle = Move.builder()
                .name("idle")
                .type(MoveType.valueOf(request.getType()))
                .info("idle")
                .actor(enemy)
                .build();
        moveRepository.save(idle);
        Asset idleAsset = Asset.builder()
                .effectVideoSrc(request.getIdleEffectVideoSrc())
                .move(idle)
                .build();
        assetRepository.save(idleAsset);
        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-damaged")
    public EnemyInsertResponse insertDamaged(@RequestBody EnemyDamagedRequest request) {
        log.info("enemyDamagedRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move damaged = Move.builder()
                .name("damaged")
                .type(MoveType.valueOf(request.getType()))
                .info("damaged")
                .actor(enemy)
                .build();
        moveRepository.save(damaged);
        Asset damagedAsset = Asset.builder()
                .effectVideoSrc(request.getDamagedEffectVideoSrc())
                .move(damaged)
                .build();
        assetRepository.save(damagedAsset);
        return EnemyInsertResponse.ok(enemy.getId());
    }

    @PostMapping("/insert/enemy-break")
    public EnemyInsertResponse insertBreak(@RequestBody EnemyBreakRequest request) {
        log.info("enemyBreakRequest: {}", request);
        Enemy enemy = enemyRepository.findById(request.getEnemyId()).orElseThrow();
        Move breakMove = Move.builder()
                .name("break")
                .type(MoveType.valueOf(request.getType()))
                .info("break")
                .actor(enemy)
                .build();
        moveRepository.save(breakMove);
        Asset breakAsset = Asset.builder()
                .effectVideoSrc(request.getBreakEffectVideoSrc())
                .move(breakMove)
                .build();
        assetRepository.save(breakAsset);
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
        Asset standbyAsset = Asset.builder()
                .effectVideoSrc(request.getStandbyEffectVideoSrc())
                .seAudioSrc(request.getStandbySeAudioSrc())
                .move(standby)
                .build();
        assetRepository.save(standbyAsset);
        Omen omen = Omen.builder()
                .name(request.getOmen().getName())
                .omenType(OmenType.valueOf(request.getOmen().getType()))
                .info(request.getOmen().getInfo())
                .triggerHp(request.getOmen().getTriggerHp())
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
                    .presentInfo(omenCancelPresentInfo)
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
                .damageRate(request.getDamageRate() + 0.0)
                .coolDown(null)
                .duration(null)
                .actor(enemy)
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
                    .effectText(status.getEffectText())
                    .target(StatusTargetType.valueOf(status.getTargetType()))
                    .maxLevel(status.getMaxLevel())
                    .statusText(status.getStatusText())
                    .duration(status.getDuration())
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

}
