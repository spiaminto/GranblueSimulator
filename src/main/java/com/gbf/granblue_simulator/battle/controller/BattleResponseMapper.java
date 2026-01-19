package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.response.BattleResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.OmenDto;
import com.gbf.granblue_simulator.battle.controller.dto.response.StatusDto;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Component
public class BattleResponseMapper {

    private final BattleContext battleContext;

    public List<BattleResponse> toBattleResponse(List<ActorLogicResult> results) {
        results.forEach(result -> log.info("result = {}", result));
        boolean containsUnionSummon = results.stream().filter(result -> result.getMove().getType() == MoveType.SUMMON_DEFAULT)
                .count() > 1;
        List<Integer> estimatedEnemyAtk = getEstimatedEnemyAtk();

        return results.stream().map(result -> {
                    Actor mainActor = result.getMainActor();
                    BaseMove move = result.getMove();
                    String moveCjsName = move.getDefaultVisual() != null ? move.getDefaultVisual().getCjsName() : "";

                    OmenResult omenResult = result.getOmenResult();
                    OmenDto omenDto = null;
                    if (omenResult != null) {
                        omenDto = OmenDto.builder()
                                .type(omenResult.getType())
                                .standbyMoveType(omenResult.getStandbyMoveType())
                                .remainValue(omenResult.getRemainValue())
                                .cancelCondition(omenResult.getCancelCond().getInfo())
                                .updateTiming(omenResult.getCancelCond().getType().getUpdateTiming())
                                .name(omenResult.getName())
                                .info(omenResult.getInfo())
                                .motion(omenResult.getMotion())
                                .build();
                    }

                    Map<Long, ActorLogicResult.Snapshot> snapshots = result.getSnapshots();
                    Integer fatalChainGauge = null;
                    int enemyMaxChargeGauge = 0;
                    int attackMultiHitCount = 0;
                    int mainActorOrder = -1;

                    List<Integer> hps = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> hpRates = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> chargeGauges = new ArrayList<>(Collections.nCopies(5, null));
                    List<List<Integer>> abilityCoolDowns = Stream.generate(ArrayList<Integer>::new).limit(5).collect(Collectors.toList());
                    List<List<Boolean>> abilitySealeds = Stream.generate(ArrayList<Boolean>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusDto>> currentStatusEffectsList = Stream.generate(ArrayList<StatusDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusDto>> addedStatusEffectsList = Stream.generate(ArrayList<StatusDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusDto>> removedStatusEffectsList = Stream.generate(ArrayList<StatusDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusDto>> levelDownedStatusEffectsList = Stream.generate(ArrayList<StatusDto>::new).limit(5).collect(Collectors.toList());
                    List<Integer> heals = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> effectDamages = new ArrayList<>(Collections.nCopies(5, null));

                    for (Map.Entry<Long, ActorLogicResult.Snapshot> entry : snapshots.entrySet()) {
                        ActorLogicResult.Snapshot snapshot = entry.getValue();
                        fatalChainGauge = snapshot.getFatalChainGauge(); // 스냅샷 모두 동일값
                        Integer currentOrder = snapshot.getCurrentOrder();
                        hps.set(currentOrder, snapshot.getHp() < 0 ? 0 : snapshot.getHp());
                        hpRates.set(currentOrder, snapshot.getHpRate() < 0 ? 0 : snapshot.getHpRate());
                        chargeGauges.set(currentOrder, snapshot.getChargeGauge());
                        abilityCoolDowns.set(currentOrder, snapshot.getAbilityCooldowns());
                        abilitySealeds.set(currentOrder, snapshot.getAbilitySealeds());
                        currentStatusEffectsList.set(currentOrder, snapshot.getCurrentStatusEffects().stream().map(StatusDto::of).toList());
                        addedStatusEffectsList.set(currentOrder, snapshot.getAddedStatusEffects().stream().map(StatusDto::of).toList());
                        removedStatusEffectsList.set(currentOrder, snapshot.getRemovedStatusEffects().stream().map(StatusDto::of).toList());
                        levelDownedStatusEffectsList.set(currentOrder, snapshot.getLevelDownedStatusEffects().stream().map(StatusDto::of).toList());
                        heals.set(currentOrder, snapshot.getHeal());
                        effectDamages.set(currentOrder, snapshot.getEffectDamage());

                        if (result.getMainActor().getId().equals(snapshot.getActorId())) {
                            // mainActor 추가필드
                            attackMultiHitCount = snapshot.getStatusDetails().getCalcedAttackMultiHitCount();
                            mainActorOrder = currentOrder;
                        }

                        if (battleContext.getEnemy().getId().equals(snapshot.getActorId())) {
                            // 적 추가필드
                            enemyMaxChargeGauge = snapshot.getMaxChargeGauge();
                        }
                    }
                    
                    // 의미없는 null 제거
                    heals = heals.stream().allMatch(Objects::isNull) ? new ArrayList<>() : heals;
                    effectDamages = effectDamages.stream().allMatch(Objects::isNull) ? new ArrayList<>() : effectDamages;

                    // 사망시 frontCharacter 에서 제거되어 snapshot 이 생성되지 않음
                    if (mainActorOrder == -1) mainActorOrder = mainActor.getCurrentOrder();

                    Long unionSummonId = battleContext.getMember().getRoom().getUnionSummonId();
                    boolean hasUnionSummon = containsUnionSummon && move.getType() == MoveType.SUMMON_DEFAULT;

                    return BattleResponse.builder()
                            // actor
                            .actorId(mainActor.getId())
                            .actorName(mainActor.getName())

                            // move
                            .moveId(move.getId())
                            .moveName(move.getName())
                            .moveType(move.getType())
                            .motion(move.getMotionType().getMotion())
                            .isAllTarget(move.isAllTarget())
                            .moveCjsName(moveCjsName)

                            // damageResult
                            .damages(result.getDamages().stream().map(damage -> damage != -1 ? damage + "" : "MISS").toList()) // 회피시 -1, 데미지 감소시 0, 나머지 음수는 오류이나 표출
                            .additionalDamages(result.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage.stream().map(damage -> damage != -1 ? damage + "" : "MISS").toList()).toList())
                            .elementTypes(result.getDamageElementTypes())
                            .damageTypes(result.getDamageTypes())
                            .totalHitCount(result.getTotalHitCount())
                            // damageResult from snapshot
                            .attackMultiHitCount(attackMultiHitCount)

                            .enemyAttackTargetIds(result.getEnemyAttackTargets().stream().map(Actor::getId).toList())

                            // omen
                            .omen(omenDto)

                            // snapshot
                            .actorOrder(mainActorOrder)
                            .hps(hps)
                            .hpRates(hpRates)
                            .chargeGauges(chargeGauges)
                            .fatalChainGauge(fatalChainGauge)
                            .enemyMaxChargeGauge(enemyMaxChargeGauge)
                            .abilityCoolDowns(abilityCoolDowns)
                            .abilitySealeds(abilitySealeds)
                            .currentBattleStatusesList(currentStatusEffectsList)

                            .addedBattleStatusesList(addedStatusEffectsList)
                            .removedBattleStatusesList(removedStatusEffectsList)
                            .levelDownedBattleStatusesList(levelDownedStatusEffectsList)
                            .heals(heals)
                            .effectDamages(effectDamages)

                            //honor
                            .resultHonor(result.getHonor())

                            // etc
                            .summonCooldowns(result.getSummonCooldowns())
                            .hasUnionSummon(hasUnionSummon)
                            .unionSummonId(unionSummonId)
                            .isUnionSummon(result.isUnionSummon())
                            .estimatedEnemyAtk(estimatedEnemyAtk)

                            .build();
                }
        ).toList();

    }

    public List<List<StatusDto>> toStatusEffectDtosList(List<List<ResultStatusEffectDto>> statusEffectDtosList) {
        return statusEffectDtosList.stream()
                .map(statusEffects ->
                        statusEffects.isEmpty() ? new ArrayList<StatusDto>() : statusEffects.stream()
                                .map(statusEffect ->
                                        StatusDto.builder()
                                                .type(statusEffect.getStatusEffectType().name())
                                                .name(statusEffect.getName())
                                                .imageSrc(statusEffect.getIconSrc())
                                                .effectText(statusEffect.getEffectText())
                                                .statusText(statusEffect.getStatusText())
                                                .displayPriority(statusEffect.getDisplayPriority())
                                                .durationType(statusEffect.getDurationType())
                                                .duration(statusEffect.getDuration())
                                                .remainingDuration(statusEffect.getRemainingDuration())
                                                .build()
                                ).toList()
                ).toList();
    }

    /**
     * 적의 '기준 공격력' 을 반환하기위한 메서드 <br>
     * 기준공격력은, 아군의 방어력만 적용된, 최소한의 적 공격력 정보 <br>
     * 원본 게임은 관련 정보를 전혀 보여주지 않지만, 최소한의 정보 제공을 위해 일단 임시로 만들어봄
     *
     * @return {최솟값, 최댓값}, 프론트 멤버가 없으면 빈 배열
     */
    public List<Integer> getEstimatedEnemyAtk() {
        List<Actor> frontCharacters = battleContext.getFrontCharacters();
        List<Integer> result = new ArrayList<>();
        if (frontCharacters.isEmpty()) return result;

        Actor enemy = battleContext.getEnemy();

        int enemyAtk = enemy.getStatus().getAtk();
        List<Double> sortedDefs = frontCharacters.stream()
                .map(Actor::getStatus)
                .map(Status::getDef)
                .sorted(Comparator.reverseOrder())
                .toList();
        double minDef = sortedDefs.getFirst();
        double maxDef = sortedDefs.getLast();

        int minEstimatedEnemyDamage = (int) (enemyAtk / minDef);
        result.add(minEstimatedEnemyDamage);
        int maxEstimatedEnemyDamage = (int) (enemyAtk / maxDef);
        result.add(maxEstimatedEnemyDamage);

        return result;
    }

}
