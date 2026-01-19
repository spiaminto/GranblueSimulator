package com.gbf.granblue_simulator.metadata.controller;

import com.gbf.granblue_simulator.metadata.controller.request.character.*;
import com.gbf.granblue_simulator.metadata.controller.response.InsertResponse;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisualType;
import com.gbf.granblue_simulator.metadata.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsertController {

    private final BaseCharacterRepository baseCharacterRepository;
    private final MoveRepository moveRepository;
    private final BaseStatusEffectRepository baseStatusEffectRepository;
    private final StatusModifierRepository statusModifierRepository;
    private final ActorVisualRepository actorVisualRepository;
    private final EffectVisualRepository effectVisualRepository;

    @PostMapping("/insert/actor-visual")
    public ResponseEntity<Map<String, Object>> insertActorVisual(@RequestBody ActorVisualInsertRequest request) {
        log.info("[insertActorVisual] request = {}", request);
        String additionalCjsName = StringUtils.hasText(request.getAdditionalCjsName()) ? request.getAdditionalCjsName() : null;
        String weaponId = StringUtils.hasText(request.getWeaponId()) ? request.getWeaponId() : null;
        ActorVisual actorVisual = ActorVisual.builder()
                .name(request.getName())
                .cjsName(request.getCjsName())
                .additionalCjsName(additionalCjsName)
                .weaponId(weaponId)
                .gid(request.getGid())
                .build();
        ActorVisual savedActorVisual = actorVisualRepository.save(actorVisual);
        return ResponseEntity.ok(Map.of(
                "message", "success",
                "actorVisualId", savedActorVisual.getId(),
                "actorCjsName", savedActorVisual.getCjsName()
        ));
    }

    @PostMapping("/insert/effect-visual")
    public ResponseEntity<Map<String, Object>> insertEffectVisuals(@RequestBody List<EffectVisualInsertRequest> requests) {
        log.info("[insertEffectVisuals] requests: {}", requests);
        List<EffectVisual> effectVisuals = new ArrayList<>();
        for (EffectVisualInsertRequest request : requests) {
            ActorVisual actorVisual = actorVisualRepository.findById(request.getActorVisualId()).orElseThrow(() -> new IllegalArgumentException("없는 actorVisualId, id = " + request.getActorVisualId()));
            effectVisuals.add(EffectVisual.builder()
                    .type(EffectVisualType.valueOf(request.getType()))
                    .cjsName(request.getCjsName())
                    .actorVisual(actorVisual)
                    .chargeAttackStartFrame(request.getChargeAttackStartFrame())
                    .isTargetedEnemy(Boolean.parseBoolean(request.getIsTargetedEnemy()))
                    .build());
        }
        List<EffectVisual> savedEffectVisuals = effectVisualRepository.saveAll(effectVisuals);
        return ResponseEntity.ok(Map.of(
                "message", "success",
                "effectVisualIds", savedEffectVisuals.stream().map(EffectVisual::getId).toList()
        ));
    }

    @PostMapping("/insert/character")
    public ResponseEntity<InsertResponse> insertCharacter(@RequestBody CharacterInsertRequest request) {
        log.info("[insertCharacter] request: {}", request);
        boolean isLeaderCharacter = Boolean.parseBoolean(request.getIsLeaderCharacter());
        Long actorVisualId = request.getActorVisualId();
        ActorVisual actorVisual = actorVisualRepository.findById(actorVisualId).orElseThrow(() -> new IllegalArgumentException("actorVisual 없음, actorVisualId = " + actorVisualId));

        BaseCharacter baseCharacter = BaseCharacter.builder()
                .name(request.getName())
                .nameEn(request.getNameEn())
                .elementType(request.getElementType())
                .isLeaderCharacter(isLeaderCharacter)
                .defaultVisual(actorVisual)
                .build();
        baseCharacter.initCharacterBaseStatus(isLeaderCharacter);
        baseCharacter = baseCharacterRepository.save(baseCharacter);
        log.info("savedChar = {}", baseCharacter);

        boolean isAttackAllTarget = Boolean.parseBoolean(request.getIsAttackAllTarget());
        List<MoveType> attackTypes = List.of(MoveType.SINGLE_ATTACK, MoveType.DOUBLE_ATTACK, MoveType.TRIPLE_ATTACK);
        List<String> infos = List.of("싱글어택", "더블어택", "트리플어택");
        List<BaseMove> attackMoves = new ArrayList<>();
        for (int i = 0; i < attackTypes.size(); i++) {
            MoveType attackType = attackTypes.get(i);
            String info = infos.get(i);
            int count = i + 1;
            attackMoves.add(BaseMove.builder()
                    .type(attackType)
                    .name("일반공격")
                    .info(info)
                    .elementType(baseCharacter.getElementType())
                    .damageRate(1.0)
                    .hitCount(count)
                    .baseActor(baseCharacter)
                    .isAllTarget(isAttackAllTarget)
                    .build());
        }
        moveRepository.saveAll(attackMoves);

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

    @PostMapping("/insert/charge-attack")
    public ResponseEntity<Map<String, Object>> insertChargeAttack(@RequestBody MoveInsertRequest request) {
        log.info("[insertChargeAttack] request: {}", request);

        request.setType("CHARGE_ATTACK_DEFAULT");
        request.setHitCount(1);
        request.setCoolDown(0);

        BaseMove savedMove = saveMove(request);
        Long baseActorId = savedMove.getBaseActor().getId();

        return ResponseEntity.ok(Map.of(
                "message", "success",
                "actorId", baseActorId,
                "moveId", savedMove.getId()
        ));
    }

    @PostMapping("/insert/ability")
    public ResponseEntity<Map<String, Object>> insertAbility(@RequestBody MoveInsertRequest request) {
        log.info("[insertAbility] request: {}", request);

        BaseMove savedMove = saveMove(request);
        Long baseActorId = savedMove.getBaseActor().getId();

        return ResponseEntity.ok(Map.of(
                "message", "success",
                "actorId", baseActorId,
                "moveId", savedMove.getId()
        ));
    }

    @PostMapping("/insert/summon")
    public ResponseEntity<Map<String, Object>> insertSummon(@RequestBody MoveInsertRequest request) {
        log.info("[insertSummon] summonRequest: {}", request);

        MoveType moveType = MoveType.valueOf(request.getType());
        if (moveType != MoveType.SUMMON_DEFAULT)
            throw new IllegalArgumentException("[insertSummon] 소환석이 아님 type = " + moveType);

        BaseCharacter baseCharacter = baseCharacterRepository.findById(6L).get(); // 소환용 캐릭터 ID = 6 으로 고정됨
        request.setActorId(baseCharacter.getId());

        // visual 저장
        String cjsName = request.getCjsName();
        String attackCjsName = cjsName + "_attack";
        String damageCjsName = cjsName + "_damage";
        List<EffectVisual> effectVisuals = new ArrayList<>();
        effectVisuals.add(EffectVisual.builder()
                .type(EffectVisualType.valueOf(request.getType()))
                .cjsName(attackCjsName)
                .actorVisual(baseCharacter.getDefaultVisual())
                .build());
        effectVisuals.add(EffectVisual.builder()
                .type(EffectVisualType.valueOf(request.getType()))
                .cjsName(damageCjsName)
                .actorVisual(baseCharacter.getDefaultVisual())
                .build());
        effectVisualRepository.saveAll(effectVisuals);

        BaseMove savedMove = saveMove(request);

        return ResponseEntity.ok(Map.of(
                "message", "success",
                "moveId", savedMove.getId()
        ));
    }

    protected BaseMove saveMove(MoveInsertRequest request) {
        BaseCharacter baseCharacter = baseCharacterRepository.findById(request.getActorId()).orElseThrow();
        ElementType elementType = StringUtils.hasText(request.getElementType()) ? ElementType.valueOf(request.getElementType()) : baseCharacter.getElementType();
        BaseMove move = BaseMove.builder()
                .type(MoveType.valueOf(request.getType()))
                .name(request.getName())
                .info(request.getInfo())
                .elementType(elementType)
                .damageRate(request.getDamageRate())
                .hitCount(request.getHitCount())
                .coolDown(request.getCoolDown())
                .baseActor(baseCharacter)
                .build();
        move = moveRepository.save(move);

        // 스테이터스
        final BaseMove moveFinal = move;
        request.getStatuses().forEach(status -> {
            if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴
            // 스테이터스
            BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                    .type(StatusEffectType.valueOf(status.getType()))
                    .name(status.getEffectText())
                    .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                    .duration(status.getDuration())
                    .durationType(StatusDurationType.valueOf(status.getDurationType()))
                    .effectText(status.getEffectText())
                    .statusText(status.getStatusText())
                    .maxLevel(status.getMaxLevel())
                    .removable(Boolean.parseBoolean(status.getRemovable()))
                    .resistible(Boolean.parseBoolean(status.getIsResistible()))
                    .applyOrder(status.getApplyOrder())
                    .gid(status.getGid())
                    .move(moveFinal)
                    .build();
            log.info("[saveMove] baseStatusEffectEntity = {}", baseStatusEffectEntity);
            baseStatusEffectRepository.save(baseStatusEffectEntity);

            // 스테이터스 효과 ("type, value \n ...")
            status.getStatusModifiers().lines().forEach(statusEffect -> {
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

        return move;
    }


}
