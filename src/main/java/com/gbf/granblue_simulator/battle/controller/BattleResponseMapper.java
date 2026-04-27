package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.controller.dto.response.BattleResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.VisualInfo;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.damage.DamageCalcLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.ForMemberAbilityInfo;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Component
public class BattleResponseMapper {

    private final BattleContext battleContext;
    private final MoveService moveService;
    private final DamageCalcLogic damageCalcLogic;

    public List<BattleResponse> toBattleResponse(List<MoveLogicResult> results) {
        if (battleContext.getMember() == null) {
            log.error("[toBattleResponse] battleContext 가 초기화 되지 않았습니다. results = \n  {}", results.stream().map(MoveLogicResult::toString).collect(Collectors.joining("\n  ")));
            throw new IllegalArgumentException("battleContext 가 초기화되지 않았습니다");
        }
        // results.forEach(result -> log.debug("result = {}", result));
        List<Integer> estimatedEnemyAtk = getEstimatedEnemyDamage();

        return results.stream().map(result -> {
                    Actor mainActor = result.getMainActor();
                    Move move = result.getMove();
                    BaseMove baseMove = move.getBaseMove();

                    OmenResult omenResult = result.getOmenResult();

                    Map<Long, MoveLogicResult.Snapshot> snapshots = result.getSnapshots();
                    Integer fatalChainGauge = null;
                    int enemyMaxChargeGauge = 0;
                    int attackMultiHitCount = 0;
                    int mainActorOrder = -1;

                    List<Integer> hps = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> hpRates = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> barriers = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> chargeGauges = new ArrayList<>(Collections.nCopies(5, null));
                    List<List<Integer>> abilityCoolDowns = Stream.generate(ArrayList<Integer>::new).limit(5).collect(Collectors.toList());
                    List<List<Boolean>> abilitySealeds = Stream.generate(ArrayList<Boolean>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusEffectDto>> currentStatusEffectsList = Stream.generate(ArrayList<StatusEffectDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusEffectDto>> addedStatusEffectsList = Stream.generate(ArrayList<StatusEffectDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusEffectDto>> removedStatusEffectsList = Stream.generate(ArrayList<StatusEffectDto>::new).limit(5).collect(Collectors.toList());
                    List<List<StatusEffectDto>> levelDownedStatusEffectsList = Stream.generate(ArrayList<StatusEffectDto>::new).limit(5).collect(Collectors.toList());
                    List<Integer> heals = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> effectDamages = new ArrayList<>(Collections.nCopies(5, null));
                    List<Boolean> canChargeAttacks = new ArrayList<>(Collections.nCopies(5, false));
                    List<Integer> doubleAttackRates = new ArrayList<>(Collections.nCopies(5, null));
                    List<Integer> tripleAttackRates = new ArrayList<>(Collections.nCopies(5, null));

                    for (Map.Entry<Long, MoveLogicResult.Snapshot> entry : snapshots.entrySet()) {
                        MoveLogicResult.Snapshot snapshot = entry.getValue();
                        fatalChainGauge = snapshot.getFatalChainGauge(); // 스냅샷 모두 동일값
                        Integer currentOrder = snapshot.getCurrentOrder();
                        hps.set(currentOrder, snapshot.getHp() < 0 ? 0 : snapshot.getHp());
                        hpRates.set(currentOrder, snapshot.getHpRate() < 0 ? 0 : snapshot.getHpRate());
                        barriers.set(currentOrder, snapshot.getBarrier());
                        chargeGauges.set(currentOrder, snapshot.getChargeGauge());
                        canChargeAttacks.set(currentOrder, snapshot.getCanChargeAttack());
                        abilityCoolDowns.set(currentOrder, snapshot.getAbilityCooldowns());
                        abilitySealeds.set(currentOrder, snapshot.getAbilitySealeds());
                        currentStatusEffectsList.set(currentOrder, snapshot.getCurrentStatusEffects());
                        addedStatusEffectsList.set(currentOrder, snapshot.getAddedStatusEffects());
                        levelDownedStatusEffectsList.set(currentOrder, snapshot.getLevelDownedStatusEffects());
                        doubleAttackRates.set(currentOrder, (int) (snapshot.getStatus().getDoubleAttackRate() * 100));
                        tripleAttackRates.set(currentOrder, (int) (snapshot.getStatus().getTripleAttackRate() * 100));

                        if (result.getMove().getType() != MoveType.TURN_FINISH) { // 턴 진행으로 사라진 효과는 제외
                            removedStatusEffectsList.set(currentOrder, snapshot.getRemovedStatusEffects());
                        }
                        heals.set(currentOrder, snapshot.getHeal());
                        effectDamages.set(currentOrder, snapshot.getEffectDamage());

                        if (result.getMainActor().getId().equals(snapshot.getActorId())) {
                            // mainActor 추가필드
                            attackMultiHitCount = result.getMultiHitCount();
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

                    MoveInfo unionSummonInfo = null;
                    if (move.getType() == MoveType.SYNC) {
                        Room room = battleContext.getMember().getRoom();
                        final boolean isTutorial = room.getRoomStatus() == RoomStatus.TUTORIAL;
                        if (room.getUnionSummonId() != null) {
                            unionSummonInfo = moveService.findById(room.getUnionSummonId())
                                    .map(unionSummonMove -> {
                                        if (unionSummonMove.getActor().getId().equals(battleContext.getLeaderCharacter().getId()) && !isTutorial)
                                            return null; // 내 소환은 합체소환 불가 (튜토리얼 제외)
                                        BaseMove unionSummonBaseMove = unionSummonMove.getBaseMove();
                                        EffectVisual visual = unionSummonBaseMove.getDefaultVisual();
                                        String summonMemberName = isTutorial ? "다른 참전자" : unionSummonMove.getActor().getMember().getUser().getUsername();
                                        return MoveInfo.builder()
                                                .cjsName(visual.getCjsName())
                                                .type(unionSummonBaseMove.getType().name())
                                                .name(unionSummonBaseMove.getName() + " ( " + summonMemberName + " )")
                                                .info(unionSummonBaseMove.getInfo())
                                                .portraitImageSrc(visual.getPortraitImageSrc())
                                                .cutinImageSrc(visual.getCutinImageSrc())
                                                .statusEffects(unionSummonBaseMove.getBaseStatusEffects().stream()
                                                        .filter(BaseStatusEffect::isDisplayable)
                                                        .map(StatusEffectDto::of)
                                                        .toList())
                                                .build();
                                    })
                                    .orElse(null);
                        }
                    }

                    VisualInfo visualInfo = null;
                    EffectVisual moveVisual = move.getBaseMove().getDefaultVisual();
                    // 기본 비주얼
                    if (moveVisual != null) {
                        visualInfo = VisualInfo.builder()
                                .moveCjsName(moveVisual.getCjsName())
                                .isTargetedEnemy(moveVisual.isTargetedEnemy())
                                .voiceLabel(moveVisual.getVoiceLabel())
                                .build();
                    }

                    // 참전자 효과, 비주얼
                    ForMemberAbilityInfo forMemberAbilityInfo = result.getForMemberAbilityInfo();
                    if (result.getMove().getType() == MoveType.SYNC && forMemberAbilityInfo != null) {
                        visualInfo = VisualInfo.builder()
                                .moveCjsName(forMemberAbilityInfo.getCjsName())
                                .isTargetedEnemy(forMemberAbilityInfo.getIsTargetedEnemy())
                                .build();
                    }

                    // Move 변경
                    Long changedMoveId = result.getChangedMoveId();
                    MoveInfo changedMoveInfo = null;
                    if (changedMoveId != null) {
                        changedMoveInfo = result.getMainActor().getMoves().stream()
                                .filter(actorMove -> actorMove.getId().equals(changedMoveId))
                                .findAny()
                                .map(MoveInfo::from)
                                .orElse(null);
                    }


                    return BattleResponse.builder()
                            // actor
                            .actorId(mainActor.getId())
                            .actorName(mainActor.getName())

                            // move
                            .moveId(move.getId())
                            .moveName(baseMove.getName())
                            .moveType(move.getType())
                            .motion(baseMove.getMotionType().getMotion())
                            .isAllTarget(baseMove.isAllTarget())

                            // damageResult
                            .damages(result.getDamages().stream().map(damage -> damage != -1 ? damage + "" : "MISS").toList()) // 회피시 -1, 데미지 감소시 0, 나머지 음수는 오류이나 표출
                            .additionalDamages(result.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage.stream().map(damage -> damage != -1 ? damage + "" : "MISS").toList()).toList())
                            .elementTypes(result.getDamageElementTypes())
                            .damageTypes(result.getDamageTypes())
                            .totalHitCount(result.getTotalHitCount())
                            .normalAttackCount(result.getNormalAttackCount())
                            // damageResult from snapshot
                            .attackMultiHitCount(attackMultiHitCount)

                            .enemyAttackTargetIds(result.getEnemyAttackTargets().stream().map(Actor::getId).toList())

                            // omen
                            .omen(omenResult)

                            .forMemberAbilityInfo(forMemberAbilityInfo)

                            // snapshot
                            .actorOrder(mainActorOrder)
                            .hps(hps)
                            .hpRates(hpRates)
                            .barriers(barriers)
                            .chargeGauges(chargeGauges)
                            .canChargeAttacks(canChargeAttacks)
                            .fatalChainGauge(fatalChainGauge)
                            .enemyMaxChargeGauge(enemyMaxChargeGauge)
                            .abilityCoolDowns(abilityCoolDowns)
                            .abilitySealeds(abilitySealeds)
                            .doubleAttackRates(doubleAttackRates)
                            .tripleAttackRates(tripleAttackRates)
                            .currentBattleStatusesList(currentStatusEffectsList)

                            .addedBattleStatusesList(addedStatusEffectsList)
                            .removedBattleStatusesList(removedStatusEffectsList)
                            .levelDownedBattleStatusesList(levelDownedStatusEffectsList)
                            .heals(heals)
                            .effectDamages(effectDamages)

                            // visual
                            .visualInfo(visualInfo)

                            //honor
                            .resultHonor(result.getHonor())

                            .changedMoveInfo(changedMoveInfo)
                            .deletedMoveId(result.getDeletedMoveId())

                            // etc
                            .summonCooldowns(result.getSummonCooldowns())
                            .estimatedEnemyAtk(estimatedEnemyAtk)
                            .isEnemyFormChange(result.isEnemyFormChange())
                            .unionSummonInfo(unionSummonInfo)
                            .forMemberAbilityInfo(forMemberAbilityInfo)

                            .build();
                }
        ).collect(Collectors.toList());

    }

    public List<Integer> getEstimatedEnemyDamage() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        if (partyMembers.isEmpty()) return Collections.emptyList();

        Enemy enemy = (Enemy) battleContext.getEnemy();
        Move enemyMove = enemy.getOmen() != null ? enemy.getFirstMove(enemy.getOmen().getStandbyType().getChargeAttackType()) : enemy.getFirstMove(MoveType.NORMAL_ATTACK);
        if (!(enemyMove.getType() == MoveType.NORMAL_ATTACK || enemyMove.getType().getParentType() == MoveType.CHARGE_ATTACK))
            return Collections.emptyList();
        List<Integer> estimateEnemyDamage = damageCalcLogic.getEstimateEnemyDamage(enemy, partyMembers, enemyMove);

        estimateEnemyDamage.sort(Comparator.naturalOrder());

        List<Integer> results = new ArrayList<>();
        results.add(Math.max(0, estimateEnemyDamage.getFirst()));
        if (estimateEnemyDamage.size() > 1) results.add(Math.max(0, estimateEnemyDamage.getLast()));

        return results;
    }

}
