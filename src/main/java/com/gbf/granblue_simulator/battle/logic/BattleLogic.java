package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.*;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.enemy.DefaultEnemyMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.summon.SummonDefaultLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.TurnEndStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.NORMAL_ATTACK;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final BattleContext battleContext;

    private final ReactionLogic reactionLogic;
    private final DefaultCharacterMoveLogic characterMoveLogic;
    private final DefaultEnemyMoveLogic enemyMoveLogic;
    private final SummonDefaultLogic summonDefaultLogic;
    private final TurnEndStatusLogic turnEndStatusLogic;

    private final BattleLogService battleLogService;
    private final TrackingConditionLogic trackingConditionLogic;
    private final SetStatusLogic setStatusLogic;
    private final MoveService moveService;
    private final RoomService roomService;


    /**
     * 방 생성 또는 입장시 실행
     */
    public List<MoveLogicResult> processBattleStart() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemy = battleContext.getEnemy();
        List<MoveLogicResult> results = new ArrayList<>();
        // 1인도전 적용 / 해제
        enemyMoveLogic.applyMemberCount();
        // 아군 배틀 시작
        results.addAll(processWithPostProcessByTriggers(partyMembers, null, TriggerType.BATTLE_START));
        // 적 배틀 시작 (적 페이즈 변화등이 아군효과보다 나중에 나오는게 더 나은듯)
        results.addAll(processWithPostProcessByTriggers(List.of(enemy), null, TriggerType.BATTLE_START));

        return results;
    }

    /**
     * partyMembers 전원이 enemy 를 대상으로 턴 진행에 의한 공격행동 수행
     */
    public List<MoveLogicResult> processStrike() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemy = battleContext.getEnemy();
        List<MoveLogicResult> results = new ArrayList<>();

        // '캐릭터 공격 개시시' 트리거 수행
        results.addAll(processWithPostProcessByTriggers(battleContext.getCurrentFieldActors(), null, TriggerType.CHARACTER_TURN_START));

        for (Actor mainCharacter : partyMembers) {

            // 공격행동 횟수 결정
            int multiStrikeCount = mainCharacter.getStatus().getStatusDetails().getCalcedStrikeCount();
            int totalStrikeCount = multiStrikeCount == 0 || mainCharacter.isGuardOn() ? 1 : multiStrikeCount;
            mainCharacter.getStatus().getStatusDetails().initEndStrikeCount(totalStrikeCount);

            int strikeCount = 0;
            int loopThreshold = 0;
            boolean reactiveChargeAttack = false;
            boolean canChargeAttackMore = false;
            MoveLogicResult strikeResult = null;

            while (strikeCount < totalStrikeCount) {
                int beforeStrikeCount = strikeCount;

                // 보험
                loopThreshold++;
                if (loopThreshold >= 10)
                    throw new MoveProcessingException("[processEnemyStrike] loopThreshold exceeded, loopThreshold = " + loopThreshold);

                // 사망
                if (mainCharacter.isAlreadyDead() || enemy.isAlreadyDead()) break;

                // 가드
                if (mainCharacter.isGuardOn()) {
                    strikeResult = characterMoveLogic.guard(mainCharacter);
                    results.add(strikeResult);
                    break;
                }

                // 공격행동 봉인
                double calcedStrikeSealed = mainCharacter.getStatus().getStatusDetails().getCalcedStrikeSealed();
                boolean isStrikeSealed = Math.random() < calcedStrikeSealed;
                if (isStrikeSealed) {
                    strikeResult = characterMoveLogic.strikeSealed(mainCharacter);
                    results.add(strikeResult);
                    break;
                }

                // '캐릭터 공격행동 시작시' 트리거 수행
                if (!reactiveChargeAttack && !canChargeAttackMore) {
                    results.addAll(processWithPostProcessByTriggers(List.of(mainCharacter), null, TriggerType.SELF_STRIKE_START));
                    results.addAll(processWithPostProcessByTriggers(partyMembers, null, TriggerType.CHARACTER_STRIKE_START));
                }

                // 공격행동 결정 및 수행
                if (reactiveChargeAttack) {
                    // 1. 오의 재발동
                    strikeResult = characterMoveLogic.processMove(mainCharacter.getFirstMove(MoveType.CHARGE_ATTACK_DEFAULT), strikeResult);
                    mainCharacter.getStatusDetails().increaseExecutedChargeAttackCount();
                    canChargeAttackMore = mainCharacter.canCharacterChargeAttack();

                    reactiveChargeAttack = false; // 오의 재발동시 다시 재발동 할수 없음
                    mainCharacter.getStatusDetails().updateIsExecutingReactivatedChargeAttack(false);

                } else if (mainCharacter.canCharacterChargeAttack()) {
                    // 2. 오의 발동
                    strikeResult = characterMoveLogic.processMove(mainCharacter.getFirstMove(MoveType.CHARGE_ATTACK_DEFAULT), strikeResult);
                    mainCharacter.getStatusDetails().increaseExecutedChargeAttackCount();
                    canChargeAttackMore = mainCharacter.canCharacterChargeAttack();

                    // 2.1 오의 재발동 확인
                    reactiveChargeAttack = getEffectByModifierType(mainCharacter, StatusModifierType.REACTIVATE_CHARGE_ATTACK_ONCE)
                            .map(statusEffect -> Objects.nonNull(setStatusLogic.removeStatusEffect(mainCharacter, statusEffect)))
                            .orElseGet(() -> getEffectByModifierType(mainCharacter, StatusModifierType.REACTIVATE_CHARGE_ATTACK).isPresent());
                    mainCharacter.getStatusDetails().updateIsExecutingReactivatedChargeAttack(reactiveChargeAttack); // 재발동 중 마킹

                } else {
                    // 3. 일반공격
                    strikeResult = characterMoveLogic.processMove(mainCharacter.getFirstMove(NORMAL_ATTACK));
                    if (!strikeResult.isEmpty()) { // 일반공격 중지시 NONE
                        strikeCount++;
                        mainCharacter.increaseExecutedStrikeCount();
                    }
                }

                // REACT_ 반응
                results.addAll(reactionLogic.processReaction(strikeResult));

                // 반응로직처리 후 오의 사용가능여부 재확인 (이미 일반공격 등으로 카운트 증가한 경우 제외)
                if (beforeStrikeCount == strikeCount && !reactiveChargeAttack) { // 오의 재발동 가능시 스킵 (오의 - 재발동 - 재사용 - 재발동 - ...)
                    if (canChargeAttackMore) {
                        if (!mainCharacter.canCharacterChargeAttack()) {
                            // 사용가능 -> 사용불가능 으로 변경 (일부 조건부 오의): 공격행동 종료
                            strikeCount++;
                            mainCharacter.increaseExecutedStrikeCount();
                            canChargeAttackMore = false;
                        } // else 사용가능 -> 사용가능: 아무것도 하지 않음

                    } else {
                        if (mainCharacter.canCharacterChargeAttack()) {
                            // 사용 불가능 -> 사용가능으로 변경 (반응로직 등으로 오의 게이지 주유받았을 경우)
                            canChargeAttackMore = true;
                        } else {
                            // 사용불가능 -> 여전히 사용불가능: 공격행동 종료
                            strikeCount++;
                            mainCharacter.increaseExecutedStrikeCount();
                        }
                    }
                }

                // '캐릭터 공격 행동 후' 트리거 수행
                if (beforeStrikeCount < strikeCount && !strikeResult.isEmpty()) {
                    results.addAll(processWithPostProcessByTriggers(List.of(mainCharacter), strikeResult, TriggerType.SELF_STRIKE_END));
                    results.addAll(processWithPostProcessByTriggers(List.of(enemy), strikeResult, TriggerType.CHARACTER_STRIKE_END));
                    results.addAll(processWithPostProcessByTriggers(partyMembers, strikeResult, TriggerType.CHARACTER_STRIKE_END));
                }
            }

            // '캐릭터 모든 공격 행동 후' 트리거 수행
            if (strikeResult != null && strikeCount >= 1) {
                results.addAll(processWithPostProcessByTriggers(List.of(mainCharacter), strikeResult, TriggerType.SELF_STRIKE_ALL_END));
                results.addAll(processWithPostProcessByTriggers(List.of(enemy), strikeResult, TriggerType.CHARACTER_STRIKE_ALL_END));
                results.addAll(processWithPostProcessByTriggers(partyMembers, strikeResult, TriggerType.CHARACTER_STRIKE_ALL_END));
            }

            // 정리
            mainCharacter.getStatusDetails().updateIsExecutingReactivatedChargeAttack(false);
        }

        // 캐릭터 공격 턴 종료시 트리거 실행
        if (!results.isEmpty()) {
            results.addAll(processWithPostProcessByTriggers(battleContext.getCurrentFieldActors(), results.getLast(), TriggerType.CHARACTER_TURN_END));
        }

        return results;
    }

    public List<MoveLogicResult> processEnemyStrike() {
        List<MoveLogicResult> results = new ArrayList<>();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = battleContext.getFrontCharacters();

        List<Actor> allActors = new ArrayList<>(); // 적 우선 트리거를 위한 리스트 (적이 첫번째)
        allActors.add(enemy);
        allActors.addAll(partyMembers);

        // 공격행동 횟수 결정
        int multiStrikeCount = enemy.getStatusDetails().getCalcedStrikeCount();
        int totalStrikeCount = multiStrikeCount == 0 ? 1 : multiStrikeCount;
        enemy.getStatusDetails().initEndStrikeCount(totalStrikeCount);

        // '적 공격 턴 시작시' [ENEMY_TURN_START] 트리거 처리
        results.addAll(processWithPostProcessByTriggers(allActors, null, TriggerType.ENEMY_TURN_START));

        int strikeCount = 0; // 공격행동 카운트

        MoveLogicResult strikeResult = null;
        int loopThreshold = 0;
        while (strikeCount < totalStrikeCount) {
            if (enemy.isAlreadyDead() || battleContext.getFrontCharacters().isEmpty()) break;
            int beforeStrikeCount = strikeCount;

            loopThreshold++;
            if (loopThreshold > 5)
                throw new MoveProcessingException("[processEnemyStrike] loopThreshold exceeded, loopThreshold = " + loopThreshold);

            // '적 공격행동 시작시' [ENEMY_STRIKE_START] 트리거 수행
            results.addAll(processWithPostProcessByTriggers(allActors, null, TriggerType.ENEMY_STRIKE_START));

            strikeResult = enemyMoveLogic.processStrike();
            strikeCount++;

            // '적 또는 캐릭터가 행동시' [REACT_XXX] 트리거 처리
            results.addAll(reactionLogic.processReaction(strikeResult));

            // '적 공격 행동 후' [ENEMY_STRIKE_END] 트리거 수행
            if (beforeStrikeCount < strikeCount) {
                results.addAll(processWithPostProcessByTriggers(allActors, strikeResult, TriggerType.ENEMY_STRIKE_END));
            }
        }

        // '적 모든 공격 행동 후' [ENEMY_STRIKE_ALL_END] 트리거 수행
        if (strikeResult != null) {
            results.addAll(processWithPostProcessByTriggers(allActors, strikeResult, TriggerType.ENEMY_STRIKE_ALL_END));
        }

        return results;
    }

    /**
     * 커맨드 어빌리티 처리 <br>
     * 자동발동 어빌리티는 postProcessMove 쪽에서 처리함
     *
     * @param ability
     * @return
     */
    public List<MoveLogicResult> processAbility(Move ability) {
        Actor mainCharacter = battleContext.getMainActor();
        Actor enemy = battleContext.getEnemy();

        // 어빌리티 사용
        MoveLogicResult abilityResult = characterMoveLogic.processMove(ability);
        List<MoveLogicResult> results = new ArrayList<>();

        // 반응
        results.addAll(reactionLogic.processReaction(abilityResult));

        // 어빌리티 후행동 - 턴 진행 없이 일반공격
        if (abilityResult.getExecuteAttackTargetType() != null) {
            battleContext.setCurrentMainActor(mainCharacter); // 위의 reaction 에서 mainActor 가 바뀐경우 원상복구해야 getDefaultTargets 에서 정상적으로 mainActor 인식
            List<Actor> executeAttackActors = setStatusLogic.getDefaultTargets(abilityResult.getExecuteAttackTargetType(), null);
            executeAttackActors.forEach(actor -> {
                if (enemy.isAlreadyDead()) return;
                MoveLogicResult executeAttackResult = characterMoveLogic.processMove(actor.getFirstMove(MoveType.NORMAL_ATTACK), abilityResult);
                results.addAll(reactionLogic.processReaction(executeAttackResult));
            });
        }

        return results;
    }

    public List<MoveLogicResult> processFatalChain() {
        Actor mainActor = battleContext.getMainActor(); // fatalChain 실행시, 프론트에서 전열 멤버중 첫번째 캐릭터를 mainActor 로 등록해서 가져옴
        Move fatalChain = mainActor.getFirstMove(MoveType.FATAL_CHAIN_DEFAULT);

        List<MoveLogicResult> results = new ArrayList<>();
        MoveLogicResult fatalChainResult = characterMoveLogic.processMove(fatalChain);

        // 반응
        results.addAll(reactionLogic.processReaction(fatalChainResult));

        return results;
    }

    /**
     * 커맨드 소환석 사용 진입점
     *
     * @param summon
     * @param doUnionSummon
     * @return
     */
    public List<MoveLogicResult> processSummon(Move summon, boolean doUnionSummon) {
        Member member = battleContext.getLeaderCharacter().getMember();

        // 기본 소환
        List<MoveLogicResult> results = new ArrayList<>();
        MoveLogicResult summonResult = summonDefaultLogic.processSummon(summon);
        // TEST ==========================
         member.updateUsedSummon(true);

        // 반응처리
        List<MoveLogicResult> reactionResults = reactionLogic.processReaction(summonResult);
        results.addAll(reactionResults);

        // 합체 소환
        Room room = member.getRoom();
        Long unionSummonId = room.getUnionSummonId();
        if (unionSummonId != null) {
            if (doUnionSummon) {
                // 합체 소환 처리
                Move unionSummonMove = moveService.findById(unionSummonId).orElseThrow(() -> {
                    roomService.forceUpdateUnionSummonId(room.getId(), null); // 유효하지 않은 합체소환석 id 강제 삭제
                    return new MoveProcessingException("합체 소환석 정보가 없습니다. 합체 소환 정보가 초기화 되었습니다.", "UNION_SUMMON_RESET");
                });

                if (room.getRoomStatus() == RoomStatus.TUTORIAL
                        || !unionSummonMove.getActor().getId().equals(summon.getActor().getId())) { // 내 소환석은 합체소환 불가 (튜토리얼 제외)
                    // 소환하는 쪽으로 변경 및 타입 변경
                    Move convertedUnionSummonMove = Move.fromBaseMove(unionSummonMove.getBaseMove()).setActor(summon.getActor());
                    convertedUnionSummonMove.mapType(MoveType.UNION_SUMMON);
                    // 소환, 반응 처리
                    MoveLogicResult unionSummonResult = summonDefaultLogic.processSummon(convertedUnionSummonMove);
                    List<MoveLogicResult> unionSummonReactionResults = reactionLogic.processReaction(unionSummonResult);
                    results.addAll(unionSummonReactionResults);

                    // 합체소환 소모
                    room.updateUnionSummonId(null);
                }

            } else {
                // 합체소환 가능한상태에서 합체소환하지 않음 -> 합체소환 처리 없고, 합체소환 등록을 갱신하지 않음
            }

        } else {
            List<Long> cannotUnionSummonBaseIds = List.of(41700L, 42000L); // 트리제로, 흑기린. 일단 이렇게 관리
            if (!cannotUnionSummonBaseIds.contains(summon.getBaseMove().getId())) {
                // 합체 소환 가능한경우 등록
                room.updateUnionSummonId(summon.getId());
            }
        }

        return results;
    }

    /**
     * 커맨드 "가드" 진입점 및 처리
     *
     * @param targetType
     * @return List boolean guardStates
     */
    public List<Boolean> processGuard(StatusEffectTargetType targetType) {
        boolean mainActorIsGuardOn = battleContext.getMainActor().isGuardOn(); // 가드 누른 캐릭터의 이전 가드 여부

        List<Actor> partyMembers = battleContext.getFrontCharacters();
        List<Actor> guardTargets = targetType == StatusEffectTargetType.SELF // guard 는 SELF / PARTY 밖에없음
                ? List.of(battleContext.getMainActor())
                : partyMembers;
        guardTargets.forEach(guardTarget -> {
            getEffectByModifierType(guardTarget, StatusModifierType.GUARD_DISABLED).ifPresentOrElse(
                    guardDisableStatusEffect -> guardTarget.changeGuard(false), // 가드불가면 무조건 false 로 변경
                    () -> {
                        // 가드상태를 토글, 가드 누른 캐릭터와 동일한 이전 상태의 가드만 토글
                        if (mainActorIsGuardOn == guardTarget.isGuardOn())
                            guardTarget.changeGuard(!guardTarget.isGuardOn());
                    }
            );
        });

        List<Boolean> guardStates = new ArrayList<>(Collections.nCopies(5, null));
        partyMembers.forEach(partyMember -> guardStates.set(partyMember.getCurrentOrder(), partyMember.isGuardOn()));

        return guardStates;
    }

    /**
     * 커맨드 "포션사용" 진입점 및 처리
     *
     * @see DefaultCharacterMoveLogic#processDead(Actor)
     * @see #processTurnEnd()
     */
    public PotionResult processPotion(PotionType potionType, Long targetActorId) {
        Member member = battleContext.getMember();

        // 갯수감소
        switch (potionType) {
            case POTION -> member.updatePotionCount(Math.max(0, member.getPotionCount() - 1));
            case ALL_POTION -> member.updateAllPotionCount(Math.max(0, member.getAllPotionCount() - 1));
            case ELIXIR -> member.updateElixirCount(Math.max(0, member.getElixirCount() - 1));
            default -> throw new MoveProcessingException("포션 타입이 잘못되었습니다. 타입: " + potionType.name());
        }

        // 타겟설정
        List<Actor> potionTargets;
        if (potionType == PotionType.ALL_POTION) {
            potionTargets = battleContext.getFrontCharacters();
        } else {
            potionTargets = List.of(battleContext.getAllCharacters().stream()
                    .filter(actor -> actor.getId().equals(targetActorId))
                    .findFirst().orElseThrow(() -> new MoveProcessingException("포션 사용 대상이 잘못되었습니다."))
            );
        }

        // 효과적용
        List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null));

        Actor revivedActor = null;
        if (potionType == PotionType.ELIXIR) {
            // 에릭실
            Actor target = potionTargets.getFirst();

            if (target.isAlreadyDead()) {
                //부활시작
                // 1. 상태효과 처리
                setStatusLogic.removeStatusEffects(target, target.getStatusEffects().stream()
                        .filter(statusEffect -> statusEffect.getBaseStatusEffect().isRemovable())
                        .toList()); // 삭제불가 제외 효과 전부 삭제
                turnEndStatusLogic.progressStatusEffect(List.of(target), LocalDateTime.now()); // 1턴 진행 (사망한 턴 종료시 사망캐릭터는 턴진행 안됨)
                // 2. 슬롯 복구
                target.updateCurrentOrder(target.getCurrentOrder() - 100);
                battleContext.reviveCharacter(target);
                revivedActor = target;
            }

            // 약화효과 모두 해제
            setStatusLogic.removeStatusEffects(target, target.getStatusEffects().stream()
                    .filter(statusEffect -> statusEffect.getBaseStatusEffect().getType() == StatusEffectType.DEBUFF)
                    .toList()); // 삭제불가 약화효과도 전부 해제

            // 체력 100% 회복
            target.updateHp(target.getMaxHp());
            healValues.set(target.getCurrentOrder(), target.getMaxHp());

        } else {
            // 일반 회복
            potionTargets.forEach(potionTarget -> {
                if (potionTarget.isAlreadyDead())
                    throw new MoveValidationException("이미 사망한 캐릭터는 일반 포션으로 회복이 불가능합니다.", true);

                // 회복관련 효과(상한상승, 강압), 언데드 적용
                boolean hasUndeadEffect = getEffectByModifierType(potionTarget, StatusModifierType.UNDEAD).isPresent();
                double healRate = hasUndeadEffect ? -1 : potionTarget.getStatusDetails().getCalcedHealRate();
                // 최종적용
                int healValue = (int) ((double) potionTarget.getMaxHp() / 2 * healRate); // 최대 HP 의 절반 회복
                potionTarget.updateHp(potionTarget.getHp() + healValue);
                healValues.set(potionTarget.getCurrentOrder(), healValue);
            });
        }

        List<Integer> hps = new ArrayList<>(Collections.nCopies(5, 0));
        List<Integer> hpRates = new ArrayList<>(Collections.nCopies(5, 0));
        battleContext.getCurrentFieldActors().forEach(actor -> {
            hps.set(actor.getCurrentOrder(), actor.getHp());
            hpRates.set(actor.getCurrentOrder(), actor.getHpRateInt());
        });

        // mainActor 회복된 캐릭터 (중에 첫번째) 로 변경 - 전원 사망후 1명 복귀 등에서 적으로 임시지정되는 경우가 있음.
        battleContext.setCurrentMainActor(potionTargets.getFirst());

        return PotionResult.builder()
                .heals(healValues)
                .hps(hps)
                .hpRates(hpRates)
                .potionCount(member.getPotionCount())
                .allPotionCount(member.getAllPotionCount())
                .elixirCount(member.getElixirCount())
                .revivedActor(revivedActor)
                .build();
    }


    /**
     * 턴 종료처리
     *
     * @return
     */
    public List<MoveLogicResult> processTurnEnd() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemy = battleContext.getEnemy();
        if (enemy.isAlreadyDead()) return new ArrayList<>(); // 적이 이미 사망했을경우 턴종처리 없음
        LocalDateTime turnEndProcessStartTime = LocalDateTime.now(); // 턴종처리 시작시간 : 턴종시 발생한 상태효과는 턴종 상태효과 진행시 duration 을 차감하지 않음

        List<MoveLogicResult> turnEndResults = new ArrayList<>();

        // 턴종 스테이터스 효과 + 반응 처리
        List<MoveLogicResult> turnEndStatusResults = turnEndStatusLogic.processTurnEnd(enemy, partyMembers);
        turnEndResults.addAll(turnEndStatusResults);

        // 아군 턴종 트리거 처리
        List<MoveLogicResult> partyTurnEndResults = processWithPostProcessByTriggers(partyMembers, null, TriggerType.TURN_END);
        turnEndResults.addAll(partyTurnEndResults);

        // 적 턴종료 트리거 처리
        List<MoveLogicResult> enemyTurnEndResults = processWithPostProcessByTriggers(List.of(enemy), null, TriggerType.TURN_END);
        turnEndResults.addAll(enemyTurnEndResults);

        // 가드 해제
        partyMembers.forEach(partyMember -> partyMember.changeGuard(false));

        //전조 발동
        Enemy concreteEnemy = (Enemy) enemy;
        boolean isEnemyOmenSuspended = concreteEnemy.getOmen() != null; // 남아있는 전조가 있다 = 브레이크 되거나, 특수기를 사용해서 해제하지 못했다 => 일반적으로 행동불가로 인해 특수기 사용이 막혔다 => 전조유지
        if (!isEnemyOmenSuspended) {
            // CHECK TriggerType.TURN_END_OMEN 인 캐릭터별 마킹용 Move인 MoveType.STANDBY Move 를 실행
            MoveLogicResult standbyResult = enemyMoveLogic.triggerOmen(enemy.getFirstMove(MoveType.STANDBY));
            if (!standbyResult.isEmpty()) {
                battleLogService.saveBattleLog(standbyResult);
                turnEndResults.add(standbyResult);
            }
        }

        // 턴종 후 스테이터스, 상태 진행 처리 (TURN_FINISH 로 통합)
        battleContext.getLeaderCharacter().progressSummonCoolDown(); // 소환석 쿨다운 진행
        battleContext.getMember().updateUsedSummon(false); // 소환가능여부 초기화
        battleContext.getAllCharacters().forEach(partyMember -> { // 캐릭터 전원
            partyMember.progressAbilityCoolDown(); // 어빌리티 쿨다운 진행
            partyMember.resetAbilityUseCount(); // 어빌리티 사용횟수 초기화
            partyMember.resetStrikeCount(); // 공격 행동 횟수 초기화
            partyMember.getMoves().stream().map(Move::getConditionTracker).forEach(TrackingConditionUtil::resetAllConditionsNotAcc); // trackingCondition 초기화
        });
        MoveLogicResult turnFinishResult = turnEndStatusLogic.progressStatusEffect(battleContext.getCurrentFieldActors(), turnEndProcessStartTime); // 상태효과 진행처리
        List<MoveLogicResult> turnFinishResultWithReactions = processWithPostProcessByTriggers(battleContext.getCurrentFieldActors(), turnFinishResult, TriggerType.TURN_FINISH);
        turnEndResults.addAll(turnFinishResultWithReactions);

        return turnEndResults;
    }

    /**
     * 특정 트리거 타입에 대한 캐릭터 전체의 행동 -> 반응체인 까지 한번에 수행 <br>
     * 주로 로직 내에서 트리거를 직접 정해서 호출할때 사용
     *
     * @param otherLogicResult     직접 지정한 트리거에 필요한 otherLogicResult
     * @param selectedTriggerTypes 직접 지정한 트리거
     */
    protected List<MoveLogicResult> processWithPostProcessByTriggers(List<Actor> actors, MoveLogicResult otherLogicResult, TriggerType... selectedTriggerTypes) {
        if (battleContext.getEnemy().isAlreadyDead()) return Collections.emptyList(); // 적이 죽으면 처리 없음

        if (otherLogicResult != null && otherLogicResult.getMove().getType() == MoveType.TURN_FINISH) {
            // otherLogicResult 는 [이미 반응 처리가 되엇거나 / 직접 반응 처리 하지 않는] 결과가 들어오는데, 이 중에서 일부 trackingCondition 에 영향을 주는 경우 직접 호출
            // CHECK 나중에 조정 필요할듯
            trackingConditionLogic.updateCommonConditions(otherLogicResult);
        }

        List<MoveLogicResult> allResults = new ArrayList<>();
        for (TriggerType triggerType : selectedTriggerTypes) { // 트리거 별로
            for (Actor actor : actors) { // 행동 하는 액터 순으로
                if (actor.isAlreadyDead()) continue;
                List<Move> triggeredMoves = new ArrayList<>(actor.getMoves(triggerType).stream()
                        .sorted(Comparator.comparing(move -> move.getBaseMove().getTriggerPhase().getOrder())).toList()); // triggerMoves 가 가변이기 때문에, 별도로 초기화 후 사용
                // log.info("[processWithPostProcessByTriggers] trigger = {}, actor = {}, moveNames = {}", triggerType.name(), actor.getName(), triggeredMoves.stream().map(move -> move.getBaseMove().getName()).collect(Collectors.joining(", ")));

                ProcessTriggerMovesByActorResult byActorResult = processTriggerMovesByActor(actor, triggeredMoves, otherLogicResult);
                allResults.addAll(byActorResult.getResults());

                if (byActorResult.isBaseActorChanged()) {
                    // 적의 폼체인지로 baseActor 바뀌면 재조회 하여 다시 트리거
                    log.info("[processWithPostProcessByTriggers] byActorResult.isBaseActorChanged, actor = {}", actor);
                    List<Move> changedTriggeredMoves = new ArrayList<>(actor.getMoves(triggerType).stream()
                            .sorted(Comparator.comparing(move -> move.getBaseMove().getTriggerPhase().getOrder())).toList());
                    log.info("processWith moveNames = {}", changedTriggeredMoves.stream().map(move -> move.getBaseMove().getName()).collect(Collectors.joining(", ")));
                    ProcessTriggerMovesByActorResult changedResult = processTriggerMovesByActor(actor, changedTriggeredMoves, otherLogicResult);

                    if (changedResult.isBaseActorChanged())
                        throw new MoveProcessingException("메타데이터가 2회이상 변경, actorId = " + actor.getId() + ", baseActorId = " + actor.getBaseActor().getId() + ", triggerType = " + triggerType);
                    allResults.addAll(changedResult.getResults());
                }
            }
        }
        return allResults;
    }

    private ProcessTriggerMovesByActorResult processTriggerMovesByActor(Actor actor, List<Move> triggeredMoves, MoveLogicResult otherLogicResult) {
        List<MoveLogicResult> allResults = new ArrayList<>();
        Long startedBaseActorId = actor.getBaseActor().getId(); // 적 처리시, 폼체인지 등으로 baseActor 가 변경될경우 남은 triggeredMoves 의 처리를 수행하지 않고 즉시반환, 이후 재조회하여 처리
        for (Move triggeredMove : triggeredMoves) { // 트리거된 행동 별로
            MoveLogicResult triggeredResult = actor.isEnemy()
                    ? enemyMoveLogic.processMove(triggeredMove, otherLogicResult)
                    : characterMoveLogic.processMove(triggeredMove, otherLogicResult);
            List<MoveLogicResult> triggeredAllResults = reactionLogic.processReaction(triggeredResult); // 트리거 된 행동에 대한 반응 (+ 트리거된 행동)
            allResults.addAll(triggeredAllResults);

            // 행동 결과로 baseActor 변경시 즉시 반환
            if (!Objects.equals(actor.getBaseActor().getId(), startedBaseActorId)) {
                return new ProcessTriggerMovesByActorResult(allResults, true);
            }
        }
        return new ProcessTriggerMovesByActorResult(allResults, false);
    }

    @Data
    static class ProcessTriggerMovesByActorResult {
        private final List<MoveLogicResult> results;
        private final boolean isBaseActorChanged;
    }

}
