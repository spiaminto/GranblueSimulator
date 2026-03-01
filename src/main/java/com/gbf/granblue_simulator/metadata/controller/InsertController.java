package com.gbf.granblue_simulator.metadata.controller;

import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.metadata.controller.request.character.ActorVisualInsertRequest;
import com.gbf.granblue_simulator.metadata.controller.request.character.CharacterInsertRequest;
import com.gbf.granblue_simulator.metadata.controller.request.character.EffectVisualInsertRequest;
import com.gbf.granblue_simulator.metadata.controller.request.character.MoveInsertRequest;
import com.gbf.granblue_simulator.metadata.controller.response.InsertResponse;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.actor.MappedMove;
import com.gbf.granblue_simulator.metadata.domain.move.*;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisualType;
import com.gbf.granblue_simulator.metadata.repository.*;
import com.gbf.granblue_simulator.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.NumberUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InsertController {

    private final BaseCharacterRepository baseCharacterRepository;
    private final BaseMoveRepository baseMoveRepository;
    private final BaseStatusEffectRepository baseStatusEffectRepository;
    private final StatusModifierRepository statusModifierRepository;
    private final ActorVisualRepository actorVisualRepository;
    private final EffectVisualRepository effectVisualRepository;
    private final UserService userService;

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
                "effectVisuals", savedEffectVisuals.stream().collect(Collectors.groupingBy(EffectVisual::getType))
        ));
    }

    @GetMapping("/testt")
    public ResponseEntity<Map<String, Object>> testt() {
        List<EffectVisual> savedEffectVisuals = effectVisualRepository.findByActorVisual(actorVisualRepository.findById(23L).get());
        return ResponseEntity.ok(Map.of(
                "message", "success",
                "effectVisuals", savedEffectVisuals.stream().collect(Collectors.groupingBy(EffectVisual::getType))
        ));
    }

    @PostMapping("/insert/character")
    public ResponseEntity<InsertResponse> insertCharacter(@RequestBody CharacterInsertRequest request) {
        log.info("[insertCharacter] request: {}", request);

        // 일반공격 비주얼 삽입
        ActorVisual actorVisual = actorVisualRepository.findById(request.getActorVisualId()).orElseThrow(() -> new IllegalArgumentException("없는 actorVisualId, id = " + request.getActorVisualId()));
        String[] attackCjsNames = request.getAttackCjsNames().replaceAll("\\s+", "").split(",");
        List<EffectVisual> effectVisuals = new ArrayList<>();
        for (String attackCjsName : attackCjsNames) {
            effectVisuals.add(EffectVisual.builder()
                    .type(EffectVisualType.valueOf(request.getVisualType()))
                    .cjsName(attackCjsName)
                    .actorVisual(actorVisual)
                    .chargeAttackStartFrame(0)
                    .isTargetedEnemy(false)
                    .build());
        }
        effectVisualRepository.saveAll(effectVisuals);

        // 일반공격 삽입
        ElementType normalAttackElementType = StringUtils.hasText(request.getNormalAttackElementType())
                ? ElementType.valueOf(request.getNormalAttackElementType())
                : request.getElementType();

        BaseMove attackMove = BaseMove.builder()
                .type(MoveType.NORMAL_ATTACK)
                .motionType(MotionType.ATTACK)
                .name("일반공격")
                .info("일반공격-" + request.getName())
                .elementType(normalAttackElementType)
                .damageRate(1.0)
                .hitCount(1)
                .isAllTarget(false)
                .logicId(request.getAttackLogicId())
                .conditionTracker(Collections.emptyMap())
                .build();
        baseMoveRepository.save(attackMove);

        // 매핑
        String abilityIdString = request.getAbilityIds();
        List<Long> abilityIds = Arrays.stream(abilityIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList();
        String supportAbilityIdString = request.getSupportAbilityIds();
        List<Long> supportAbilityIds = Arrays.stream(supportAbilityIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList();
        String allAbilityIdString = request.getAllAbilityIds();
        List<Long> allAbilityIds = StringUtils.hasText(allAbilityIdString)
                ? Arrays.stream(allAbilityIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList()
                : Collections.emptyList();
        String allSupportAbilityIdString = request.getAllSupportAbilityIds();
        List<Long> allSupportAbilityIds = StringUtils.hasText(allSupportAbilityIdString)
                ? Arrays.stream(allSupportAbilityIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList()
                : Collections.emptyList();

        String chargeAttackIdString = request.getChargeAttackIds();
        List<Long> chargeAttackIds = List.of(Long.valueOf(chargeAttackIdString));

        String changingMoveIdString = request.getChangingMoveIds();
        List<Long> changingMoveIds = StringUtils.hasText(changingMoveIdString)
                ? Arrays.stream(changingMoveIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList()
                : Collections.emptyList();
        String triggeredAbilityIdString = request.getTriggeredAbilityIds();
        List<Long> triggeredAbilityIds = StringUtils.hasText(triggeredAbilityIdString)
                ? Arrays.stream(triggeredAbilityIdString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList()
                : Collections.emptyList();
        MappedMove mappedMove = MappedMove.builder()
                .abilityIds(abilityIds)
                .supportAbilityIds(supportAbilityIds)
                .allAbilityIds(allAbilityIds)
                .allSupportAbilityIds(allSupportAbilityIds)
                .chargeAttackIds(chargeAttackIds)
                .triggeredAbilityIds(triggeredAbilityIds)
                .changingMoveIds(changingMoveIds)
                .normalAttackId(attackMove.getId())
                .build();

        // 캐릭터 삽입
        boolean isLeaderCharacter = Boolean.parseBoolean(request.getIsLeaderCharacter());
        BaseCharacter baseCharacter = BaseCharacter.builder()
                .name(request.getName())
                .nameEn(request.getNameEn())
                .elementType(request.getElementType())
                .isLeaderCharacter(isLeaderCharacter)
                .defaultVisual(actorVisual)
                .mappedMove(mappedMove)
                .build();
        baseCharacter.initCharacterBaseStatus(isLeaderCharacter);
        baseCharacter = baseCharacterRepository.save(baseCharacter);
        log.info("savedChar = {}", baseCharacter);

        // userCharacter 추가
        userService.createUserCharactersFromInsert(baseCharacter);

        return ResponseEntity.ok(InsertResponse.ok(baseCharacter.getId()));
    }

    @PostMapping("/insert/charge-attack")
    public ResponseEntity<Map<String, Object>> insertChargeAttack(@RequestBody MoveInsertRequest request) {
        log.info("[insertChargeAttack] request: {}", request);

        request.setType(MoveType.CHARGE_ATTACK.name());
        request.setHitCount(1);
        request.setCoolDown(0);

        BaseMove savedMove = saveMove(request);

        return ResponseEntity.ok(Map.of(
                "message", "success",
                "moveName", savedMove.getType(),
                "moveId", savedMove.getId()
        ));
    }

    @PostMapping("/insert/ability")
    public ResponseEntity<Map<String, Object>> insertAbility(@RequestBody MoveInsertRequest request) {
        log.info("[insertAbility] request: {}", request);

        BaseMove savedMove = saveMove(request);

        return ResponseEntity.ok(Map.of(
                "message", "success",
                "moveName", savedMove.getName(),
                "moveId", savedMove.getId()
        ));
    }

    @PostMapping("/insert/summon")
    public ResponseEntity<Map<String, Object>> insertSummon(@RequestBody MoveInsertRequest request) {
        log.info("[insertSummon] summonRequest: {}", request);

        MoveType moveType = MoveType.valueOf(request.getType());
        if (moveType != MoveType.SUMMON)
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
        // 비주얼 삽입
        EffectVisual savedEffectVisual = null;
        if (StringUtils.hasText(request.getCjsName()) && request.getDefaultVisualId() <= 0) {
            ActorVisual actorVisual = actorVisualRepository.findById(request.getActorVisualId()).orElseThrow(() -> new IllegalArgumentException("없는 actorVisualId, id = " + request.getActorVisualId()));
            EffectVisual effectVisual = EffectVisual.builder()
                    .type(EffectVisualType.valueOf(request.getVisualType()))
                    .cjsName(request.getCjsName())
                    .actorVisual(actorVisual)
                    .chargeAttackStartFrame(request.getChargeAttackStartFrame())
                    .isTargetedEnemy(Boolean.parseBoolean(request.getIsTargetedEnemy()))
                    .build();
            savedEffectVisual = effectVisualRepository.save(effectVisual);
        }

        // Move 삽입
        BaseMove move = null;
        if (StringUtils.hasText(request.getName())) {
            EffectVisual defaultEffectVisual = null;
            if (savedEffectVisual == null && request.getDefaultVisualId() > 0) {
                defaultEffectVisual = effectVisualRepository.findById(request.getDefaultVisualId()).orElseThrow(() -> new IllegalArgumentException("없는 defaultVisualId, id = " + request.getDefaultVisualId()));
            }

            ElementType elementType = ElementType.valueOf(request.getElementType());
            MoveType moveType = MoveType.valueOf(request.getType());

            MotionType motionType = MotionType.NONE;
            if (StringUtils.hasText(request.getMotionType())) {
                motionType = MotionType.valueOf(request.getMotionType());
            } else if (moveType == MoveType.CHARGE_ATTACK) {
                motionType = MotionType.MORTAL_A;
            }

            AbilityType abilityType = null;
            if (StringUtils.hasText(request.getAbilityType())) {
                abilityType = AbilityType.valueOf(request.getAbilityType());
            }

            String conditionTrackerString = request.getConditionTrackerString();
            Map<TrackingCondition, Object> conditionTracker = new HashMap<>();
            if (StringUtils.hasText(conditionTrackerString)) {
                conditionTrackerString.lines().forEach(line -> {
                    String[] splitLine = line.replaceAll("\\s+", "").split(",");
                    TrackingCondition trackingCondition = TrackingCondition.valueOf(splitLine[0]);
                    Object value;
                    try {
                        value = Long.parseLong(splitLine[1]);
                    } catch (NumberFormatException e) {
                        value = splitLine[1]; // 실패시 문자열 그대로
                    }
                    conditionTracker.put(trackingCondition, value);
                });
            }


            TriggerType triggerType = StringUtils.hasText(request.getTriggerType()) ? TriggerType.valueOf(request.getTriggerType()) : TriggerType.NONE;
            TriggerPhase triggerPhase = StringUtils.hasText(request.getTriggerPhase()) ? TriggerPhase.valueOf(request.getTriggerPhase()) : TriggerPhase.NONE;

            move = BaseMove.builder()
                    .type(moveType)
                    .abilityType(abilityType)
                    .name(request.getName())
                    .info(request.getInfo())
                    .elementType(elementType)
                    .damageRate(request.getDamageRate())
                    .hitCount(request.getHitCount())
                    .coolDown(request.getCoolDown())
                    .defaultVisual(defaultEffectVisual != null ? defaultEffectVisual : savedEffectVisual) // nullable
                    .motionType(motionType)
                    .logicId(request.getLogicId())
                    .triggerType(triggerType)
                    .triggerPhase(triggerPhase)
                    .conditionTracker(conditionTracker)
                    .build();
            move = baseMoveRepository.save(move);

            // 스테이터스
            final BaseMove moveFinal = move;
            if (request.getStatuses() != null) {
                request.getStatuses().forEach(status -> {
                    if (!StringUtils.hasText(status.getType())) return; // status type 없으면 리턴

                    String effectText = StringUtils.hasText(status.getEffectText()) ? status.getEffectText() : status.getName();

                    // 스테이터스
                    BaseStatusEffect baseStatusEffectEntity = BaseStatusEffect.builder()
                            .type(StatusEffectType.valueOf(status.getType()))
                            .name(status.getName())
                            .targetType(StatusEffectTargetType.valueOf(status.getTargetType()))
                            .duration(status.getDuration())
                            .durationType(StatusDurationType.valueOf(status.getDurationType()))
                            .effectText(effectText)
                            .statusText(status.getStatusText())
                            .maxLevel(status.getMaxLevel())
                            .removable(Boolean.parseBoolean(status.getRemovable()))
                            .resistible(Boolean.parseBoolean(status.getIsResistible()))
                            .uniqueFrame(Boolean.parseBoolean(status.getIsUniqueFrame()))
                            .conditionalModifier(Boolean.parseBoolean(status.getConditionalModifier()))
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
            }
        }

        return move;
    }

    @GetMapping("/status-modifiers")
    public ResponseEntity<Map<String, Object>> getStatusModifierInfos() {
        Map<String, String> modifiers = Arrays.stream(StatusModifierType.values())
                .collect(Collectors.toMap(
                        (StatusModifierType::name),
                        StatusModifierType::getValueInfo,
                        (beforeKey, afterKey) -> beforeKey, // 겹치는 사항 없음
                        LinkedHashMap::new
                ));
        return ResponseEntity.ok(Map.of("modifiers", modifiers));
    }

    @GetMapping("/basic-status-effects")
    public ResponseEntity<Map<String, Object>> getBasicStatusEffects() {
        List<StatusEffectDto> statusEffects = baseStatusEffectRepository.findAll().stream()
                .filter(baseStatusEffect -> !baseStatusEffect.isUniqueFrame() && baseStatusEffect.isDisplayable() && baseStatusEffect.getId() >= 60000)
                .collect(Collectors.toMap(
                        BaseStatusEffect::getName,
                        StatusEffectDto::of,
                        (existing, replacement) -> existing,  // 중복 시 이전값 유지
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(StatusEffectDto::getName))
                .toList();
        return ResponseEntity.ok(Map.of("basicStatusEffects", statusEffects));
    }

}
