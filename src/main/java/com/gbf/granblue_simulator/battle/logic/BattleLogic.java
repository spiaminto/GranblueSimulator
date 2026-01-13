package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.actor.character.CharacterLogic;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.TurnEndStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.SummonLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final Map<String, CharacterLogic> characterLogicMap;
    private final Map<String, EnemyLogic> enemyLogicMap;

    private final BattleContext battleContext;
    private final SummonLogic summonLogic;
    private final TurnEndStatusLogic turnEndStatusLogic;

    private final MoveRepository moveRepository;
    private final BattleLogService battleLogService;

    /**
     * 방 생성 또는 입장시 실행
     */
    public void startBattle() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        partyMembers.forEach(partyMember -> {
            callCharacterLogic(CharacterLogic::processBattleStart, partyMember);
        });

        Actor enemy = battleContext.getEnemy();
        callEnemyLogic(EnemyLogic::processBattleStart, enemy);
    }

    /**
     * partyMembers 전원이 enemy 를 대상으로 공격행동 수행
     * 1캐릭 공격 -> 적 반응
     *
     * @return
     */
    public List<ActorLogicResult> processStrike() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemy = battleContext.getEnemy();
        List<ActorLogicResult> results = new ArrayList<>();

        for (Actor mainCharacter : partyMembers) {
            // 공격행동 횟수 결정
            int multiStrikeCount = mainCharacter.getStatus().getStatusDetails().getCalcedStrikeCount();
            int totalStrikeCount = multiStrikeCount == 0 || mainCharacter.isGuardOn() ? 1 : multiStrikeCount; // 공격횟수버프 0 이거나 가드시 1
            mainCharacter.getStatus().getStatusDetails().initEndStrikeCount(totalStrikeCount);
            // 자신의 공격행동 시작후 붙은 공격행동횟수 버프는 현재 공격행동에 영향을 끼치지 않음 (ex 허사 다이버 주인공 오의시 재행동 부여 등)

            int strikeCount = 0; // 공격행동 카운트
            boolean isNextMoveChargeAttack = false;

            while (isNextMoveChargeAttack || strikeCount < totalStrikeCount) {
                if (mainCharacter.isAlreadyDead() || enemy.isAlreadyDead()) break;

                ActorLogicResult strikeResult = null;
                if (isNextMoveChargeAttack) { // 오의 재발동
                    strikeResult = callCharacterLogic(CharacterLogic::processChargeAttack, mainCharacter);
                } else { // 공격 행동
                    strikeResult = callCharacterLogic(CharacterLogic::processStrike, mainCharacter);
                    isNextMoveChargeAttack = strikeResult.executeChargeAttack();
                    strikeCount++;
                }

                // 반응
                results.addAll(postProcessToMove(strikeResult));

                if (strikeCount > 5)
                    throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
            }
        }
        return results;
    }

    public List<ActorLogicResult> processEnemyStrike() {
        List<ActorLogicResult> results = new ArrayList<>();
        Actor enemy = battleContext.getEnemy();
        
        // 공격행동 횟수 결정
        int multiStrikeCount = enemy.getStatus().getStatusDetails().getCalcedStrikeCount();
        int totalStrikeCount = multiStrikeCount == 0 ? 1 : multiStrikeCount;
        enemy.getStatus().getStatusDetails().initEndStrikeCount(totalStrikeCount);

        int strikeCount = 0; // 공격행동 카운트

        while (strikeCount < totalStrikeCount) {
            if (enemy.isAlreadyDead() || battleContext.getFrontCharacters().isEmpty()) break;

            ActorLogicResult strikeResult = callEnemyLogic(EnemyLogic::processStrike, enemy);
            
            // 반응
            results.addAll(postProcessToMove(strikeResult));

            strikeCount++;
            if (strikeCount > 5)
                throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
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
    public List<ActorLogicResult> processAbility(Move ability) {
        Actor mainCharacter = battleContext.getMainActor();
        Actor enemy = battleContext.getEnemy();

        // 어빌리티 사용
        ActorLogicResult abilityResult = callCharacterLogic((logic) -> logic.processAbility(ability.getType()), mainCharacter);
        List<ActorLogicResult> results = new ArrayList<>();
        // 커맨드 필드 초기화
        mainCharacter.updateCommandType(null);

        // 반응
        results.addAll(postProcessToMove(abilityResult));

        // 어빌리티 후행동 - 턴 진행 없이 일반공격
        if (abilityResult.getExecuteAttackTargetType() != null) {
            List<Actor> executeAttackActors = abilityResult.getExecuteAttackTargetType() ==
                    StatusEffectTargetType.SELF ? List.of(mainCharacter) : battleContext.getFrontCharacters();
            executeAttackActors.forEach(actor -> {
                if (enemy.isAlreadyDead()) return;
                ActorLogicResult executeAttackResult = callCharacterLogic(CharacterLogic::processAttack, actor);
                results.addAll(postProcessToMove(executeAttackResult));
            });
        }

        return results;
    }

    public List<ActorLogicResult> processFatalChain(Move fatalChain) {
        Actor mainActor = battleContext.getMainActor(); // fatalChain 실행시, 프론트에서 전열 멤버중 첫번째 캐릭터를 mainActor 로 등록해서 가져옴

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult fatalChainResult = callCharacterLogic((logic) -> logic.processFatalChain(fatalChain), mainActor);

        // 반응
        results.addAll(postProcessToMove(fatalChainResult));

        return results;
    }

    /**
     * 커맨드 소환석 사용 진입점
     *
     * @param summon
     * @param doUnionSummon
     * @return
     */
    public List<ActorLogicResult> processSummon(Move summon, boolean doUnionSummon) {
        // 기본 소환
        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult summonResult = summonLogic.processSummon(summon, false);

        // 반응
        results.addAll(postProcessToMove(summonResult));

        // 합체 소환
        Room room = battleContext.getLeaderCharacter().getMember().getRoom();
        Long unionSummonId = room.getUnionSummonId();
        if (unionSummonId != null) {
            if (doUnionSummon) {
                // 합체 소환 처리
                Move unionSummonMove = moveRepository.findById(unionSummonId).orElseThrow(() -> new IllegalArgumentException("없는 합체 소환석"));
                ActorLogicResult unionSummonResult = summonLogic.processSummon(unionSummonMove, true);
                // 반응
                results.addAll(postProcessToMove(unionSummonResult));
                room.updateUnionSummonId(null);
            } else {
                // 합체소환 x, 합체소환 등록 x
            }
        } else {
            // 합체 소환 등록
            room.updateUnionSummonId(summon.getId());
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
     * 현재 포션사용은 언데드, 강압 효과 등 스테이터스 효과 관계없이 무조건 회복하도록 설정됨.
     *
     * @param targetType SELF, PARTY_MEMBERS
     * @return
     */
    public PotionResult processPotion(StatusEffectTargetType targetType) {
        Member member = battleContext.getMember();

        List<Actor> potionTargets = new ArrayList<>();
        if (targetType == StatusEffectTargetType.SELF && battleContext.getMainActor() != null) {
            // 일반 포션
            int potionCount = member.getPotionCount();
            if (potionCount <= 0)
                throw new MoveValidationException("포션 검증에러, targetType = " + targetType + " potionCount = " + potionCount);
            member.addPotionCount(-1);
            potionTargets = List.of(battleContext.getMainActor());
        } else if (targetType == StatusEffectTargetType.PARTY_MEMBERS) {
            // 올 포션
            int allPotionCount = member.getAllPotionCount();
            if (allPotionCount <= 0)
                throw new MoveValidationException("포션 검증에러, targetType = " + targetType + " potionCount = " + allPotionCount);
            member.addAllPotionCount(-1);
            potionTargets = battleContext.getFrontCharacters();
        } else
            // 에릭실(미구현) 및 기타
            throw new MoveValidationException("지원하지 않는 포션사용 타입, targetType = " + targetType);

        List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null));
        potionTargets.forEach(potionTarget -> {
            Integer beforeHp = potionTarget.getHp();
            potionTarget.updateHp(potionTarget.getHp() + potionTarget.getMaxHp() / 2); // 최대 HP 의 절반 회복
            Integer afterHp = potionTarget.getHp();
            healValues.set(potionTarget.getCurrentOrder(), afterHp - beforeHp); // CHECK 현재 포션은 스테이터스 효과와 관계없이 회복하므로 이렇게 구현.
        });

        List<Integer> hps = new ArrayList<>(Collections.nCopies(5, 0));
        List<Integer> hpRates = new ArrayList<>(Collections.nCopies(5, 0));
        battleContext.getCurrentFieldActors().forEach(actor -> {
            hps.set(actor.getCurrentOrder(), actor.getHp());
            hpRates.set(actor.getCurrentOrder(), actor.getHpRate());
        });

        return PotionResult.builder()
                .heals(healValues)
                .hps(hps)
                .hpRates(hpRates)
                .potionCount(member.getPotionCount())
                .allPotionCount(member.getAllPotionCount())
                .build();
    }


    /**
     * 턴 종료처리
     *
     * @return
     */
    public List<ActorLogicResult> processTurnEnd() {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemy = battleContext.getEnemy();
        if (enemy.isAlreadyDead()) return new ArrayList<>(); // 적이 이미 사망했을경우 턴종처리 없음
        LocalDateTime turnEndProcessStartTime = LocalDateTime.now(); // 턴종처리 시작시간 : 턴종시 발생한 상태효과는 턴종 상태효과 진행시 duration 을 차감하지 않음

        List<ActorLogicResult> turnEndResults = new ArrayList<>();

        // 턴종 스테이터스 효과 처리
        List<ActorLogicResult> turnEndStatusResults = turnEndStatusLogic.processTurnEnd(enemy, partyMembers);
        // 턴종 스테이터스 효과 반응 처리
        turnEndStatusResults.forEach(turnEndResult -> turnEndResults.addAll(postProcessToMove(turnEndResult)));

        // 사망 여부 처리, CHECK 턴종 스테이터스가 턴종힐 -> 턴종데미지 순서로 처리되므로, 사망처리 순서는 여기로 ok
        List<ActorLogicResult> deadResults = partyMembers.stream()
                .filter(partyMember -> partyMember.isNowDead() || enemy.isNowDead())
                .map(partyMember -> this.processDead(partyMember, enemy)).filter(Objects::nonNull).toList();
        battleLogService.saveBattleLogAll(deadResults);
        turnEndResults.addAll(deadResults);

        // 아군 턴종 처리
        partyMembers.forEach(partyMember -> {
            partyMember.changeGuard(false); // 가드 off
            List<ActorLogicResult> characterTurnEndResults = callCharacterLogic(CharacterLogic::processTurnEnd, partyMember).stream()
                    .filter(ActorLogicResult::notEmpty).toList();

            // 턴종 결과는 여러개라, 모두 반응처리
            characterTurnEndResults.forEach(characterTurnEndResult -> turnEndResults.addAll(postProcessToMove(characterTurnEndResult)));
        });

        // 적 턴종료 처리
        List<ActorLogicResult> enemyTurnEndResults = callEnemyLogic(EnemyLogic::processTurnEnd, enemy).stream()
                .filter(ActorLogicResult::notEmpty).toList();
        // 적 턴종료 반응 처리
        enemyTurnEndResults.forEach(enemyTurnEndResult -> turnEndResults.addAll(postProcessToMove(enemyTurnEndResult)));

        //전조 발동
        Enemy concreteEnemy = (Enemy) enemy;
        boolean isEnemyOmenSuspended = concreteEnemy.getCurrentStandbyType() != null; // 남아있는 전조가 있다 = 브레이크 되거나, 특수기를 사용해서 해제하지 못했다 => 일반적으로 행동불가로 인해 특수기 사용이 막혔다 => 전조유지
        if (!isEnemyOmenSuspended) {
            List<ActorLogicResult> activateOmenResults = callEnemyLogic(EnemyLogic::activateOmen, enemy);
            battleLogService.saveBattleLogAll(activateOmenResults);
            turnEndResults.addAll(activateOmenResults);
        }

        // 턴종 후 스테이터스, 상태 진행 처리
        partyMembers.forEach(Actor::progressAbilityCoolDown); // 어빌리티 쿨다운 진행
        battleContext.getLeaderCharacter().progressSummonCoolDown(); // 소환석 쿨다운 진행
        partyMembers.forEach(Actor::resetAbilityUseCount); // 어빌리티 사용횟수 초기화
        partyMembers.forEach(Actor::resetStrikeCount); // 공격 행동 횟수 초기화
        ActorLogicResult turnFinishResult = turnEndStatusLogic.progressStatusEffect(turnEndProcessStartTime); // 상태효과 진행처리
        battleLogService.saveBattleLog(turnFinishResult);
        turnEndResults.add(turnFinishResult);

        return turnEndResults;
    }

    /**
     * 발생한 행동에 대한 아군 / 적 반응처리 + 캐릭터 단위로 로그 저장 <br>
     * 모든 커맨드 ( 공격(턴 진행), 어빌리티 사용, 페이탈 체인, 소환석 ) 이 반드시 해당 메서드로 후처리 해야함 <br>
     * 추가로 턴 종료 행동 후에도 반응처리
     *
     * @param firstBaseResult 반응 처리를 할 첫 행동 결과
     * @return 파라미터로 넘어온 결과를 포함한 모든 반응처리 결과
     */
    protected List<ActorLogicResult> postProcessToMove(ActorLogicResult firstBaseResult) {
        List<ActorLogicResult> allResults = new ArrayList<>();
        Queue<ActorLogicResult> reactionQueue = new ArrayDeque<>();

        allResults.add(firstBaseResult);
        battleLogService.saveBattleLog(firstBaseResult);
        reactionQueue.add(firstBaseResult);

        int depth = 0; // 디버깅을 위한 depth 추적, 아래의 for 루프도 depth 추적을 위해 사용
        while (!reactionQueue.isEmpty()) {
            log.info("[postProcessToMove] \ndepth = {} \nqueue = {}", depth, reactionQueue.stream().map(ActorLogicResult::toString).collect(Collectors.joining("\n  ")));
            int queueSize = reactionQueue.size();

            for (int i = 0; i < queueSize; i++) {
                ActorLogicResult currentBaseResult = reactionQueue.remove();

                List<ActorLogicResult> currentDepthResults;
                Actor baseResultActor = battleContext.getAllActors().stream()
                        .filter(currentBaseResult::isFromActor)
                        .findAny().orElseThrow(() -> new IllegalArgumentException("이전 actor 없음, actorId = " + currentBaseResult.getMainActor().getId()));

                currentDepthResults = processReactions(baseResultActor, currentBaseResult);

                // 너무 많이 터지면 방어 (무한루프 방지)
                if (allResults.size() + currentDepthResults.size() > 50) {
                    throw new IllegalArgumentException("반응 메서드의 결과가 50개를 초과, 마지막 결과: "
                            + (currentDepthResults.isEmpty() ? currentBaseResult : currentDepthResults.getLast()));
                }

                allResults.addAll(currentDepthResults);
                reactionQueue.addAll(currentDepthResults);
            }
            depth++; // 해당 depth 의 queueSize 만큼 다돌렸으면 depth 증가

        }

        return allResults;
    }

    protected List<ActorLogicResult> processReactions(Actor beforeResultActor, ActorLogicResult baseResult) {

        List<ActorLogicResult> results = new ArrayList<>();
        Actor enemy = battleContext.getEnemy();
        boolean fromEnemy = baseResult.isFromActor(enemy);

        // 1. 적이 우선 반응
        List<ActorLogicResult> enemyReactions = fromEnemy
                ? callEnemyLogic(logic -> logic.postProcessToEnemyMove(baseResult), enemy)
                : callEnemyLogic(logic -> logic.postProcessToPartyMove(baseResult), enemy);
        enemyReactions = enemyReactions.stream().filter(ActorLogicResult::notEmpty).toList();
        results.addAll(enemyReactions);
        battleLogService.saveBattleLogAll(enemyReactions);

        // 2. 아군 전체의 반응 순서 결정
        List<Integer> currentOrderList = new ArrayList<>(List.of(1,2,3,4));
        if (!fromEnemy) { // 아군 행동에 대한 반응인 경우, 이전 행동한 아군의 순서를 첫번째로 변경
            currentOrderList.remove(beforeResultActor.getCurrentOrder());
            currentOrderList.addFirst(beforeResultActor.getCurrentOrder());
        }

        // 3. 아군 전체가 반응
        for (int currentOrder : currentOrderList) {
            // 반응할 아군을 갱신
            List<Actor> currentPartyMembers = battleContext.getFrontCharacters();
            currentPartyMembers.stream()
                    .filter(reactingPartyMember -> !reactingPartyMember.isAlreadyDead()) // 반응 중 사망시 반응 x
                    .filter(reactingPartyMember -> reactingPartyMember.getCurrentOrder() == currentOrder).findAny() // 변경된 순서대로 (사망시 currentOrder += 100)
                    .ifPresent(reactingPartyMember -> {
                        // 사망여부 판단
                        if (enemy.isAlreadyDead()) return; // 적이 이미 사망처리 되었을시 반응 중지
                        if (enemy.isNowDead() || reactingPartyMember.isNowDead()) { // 적 또는 처리할 아군이 사망했을 경우 사망처리
                            ActorLogicResult deadResult = processDead(reactingPartyMember, enemy);
                            if (deadResult != null) {
                                battleLogService.saveBattleLog(deadResult);
                                results.add(deadResult);
                                return; // 어느하나가 사망했을시 반응 중지
                            }
                        }

                        // 반응
                        ActorLogicResult reactResult = fromEnemy
                                ? callCharacterLogic(logic -> logic.postProcessToEnemyMove(baseResult), reactingPartyMember)
                                : callCharacterLogic(logic -> logic.postProcessToPartyMove(baseResult), reactingPartyMember);
                        // 저장
                        if (!reactResult.getMove().getType().isNone()) { // none null, none empty when added
                            log.info("[processReactions] 반응 발생 \n baseResult = {}\n  reactResult = {}", baseResult, reactResult);
                            battleLogService.saveBattleLog(reactResult);
                            results.add(reactResult);
                        }
                    });
        }

        return results;
    }

    /**
     * 캐릭터와 적 사망 처리 <br>
     * 1. 캐릭터 사망여부 판별 및 '지금' 사망했을시 사망처리 <br>
     * 2. 캐릭터가 이미 사망햇는지 여부 판멸 및 이미 사망했을시 null 반환
     *
     * @param character 현재 reactingActor (반응 처리를 수행할 actor)
     * @param enemy     현재 context 의 enemy
     * @return 사망시 MoveType.DEAD_DEFAULT, 사망 없을시 null
     */
    protected ActorLogicResult processDead(Actor character, Actor enemy) {
        log.info("[checkAndProcessDead] checkAndProcessDead, character.isNowDead() = {} character.isAlreadyDead = {} enemy.isNowDead() = {} enemyIsAlreadyDead={} character = {}, enemy = {}", character.isNowDead(), character.isAlreadyDead(), enemy.isNowDead(), enemy.isAlreadyDead(), character, enemy);
        ActorLogicResult deadResult = null; // 캐릭터가 불사 등으로 버틸경우 null 반환 가능
        if (character.isNowDead()) {
            ActorLogicResult characterDeadResult = callCharacterLogic((logic) -> logic.defaultDead(character), character);
            deadResult = characterDeadResult.getMove().getType() == MoveType.DEAD_DEFAULT ? characterDeadResult : null;
        } else {
            deadResult = callEnemyLogic((logic) -> logic.defaultDead(enemy), enemy);
        }
        return deadResult;
    }

    /**
     * CharacterLogic 을 직접 호출
     * 특히 battleContext.setMainActor 는 여기서만 설정하도록 함
     *
     * @param logicFunction
     * @param actor
     * @param <R>           ActorLogicResult 또는 List<ActorLogicResult>
     * @return
     */
    public <R> R callCharacterLogic(Function<CharacterLogic, R> logicFunction, Actor actor) {
        if (!actor.isCharacter()) throw new IllegalArgumentException("[callCharacterLogic] Not a character");
        battleContext.setCurrentMainActor(actor);
        return logicFunction.apply(characterLogicMap.get(actor.getBaseActor().getNameEn() + "Logic"));
    }

    /**
     * EnemyLogic 을 직접 호출
     * 특히 battleContext.setMainActor 는 여기서만 설정하도록 함
     *
     * @param logicFunction
     * @param actor
     * @param <R>
     * @return
     */
    public <R> R callEnemyLogic(Function<EnemyLogic, R> logicFunction, Actor actor) {
        if (actor.isCharacter()) throw new IllegalArgumentException("[callEnemyLogic] Not an enemy");
        battleContext.setCurrentMainActor(actor);
        return logicFunction.apply(enemyLogicMap.get(actor.getBaseActor().getNameEn() + "Logic"));
    }


    // ==== 참고용, 나중에 삭제 ======================================================================
//
//
//    /**
//     * 발생한 행동에 대한 아군 / 적 반응처리 + 캐릭터 단위로 로그 저장 <br>
//     * 모든 커맨드 ( 공격(턴 진행), 어빌리티 사용, 페이탈 체인, 소환석 ) 이 반드시 해당 메서드로 후처리 해야함 <br>
//     * 추가로 턴 종료 행동 후에도 반응처리
//     *
//     * @param beforeLogicResult 발생한 행동 결과
//     * @return 파라미터로 넘어온 결과를 포함한 모든 반응처리 결과
//     */
//    protected List<ActorLogicResult> postProcessToMoveLegacy(ActorLogicResult beforeLogicResult) {
//        List<ActorLogicResult> results = new ArrayList<>();
//        results.add(beforeLogicResult);
//
//        // 이전 행동에 따른 분기처리
//        boolean toEnemy = beforeLogicResult.getMainActorId().equals(battleContext.getEnemy().getId()); // 이전 행동 주체가 적
//        if (toEnemy) {
//            // 적이 우선 자신의 행동에 대한 반응
//            List<ActorLogicResult> enemySelfReactionResults = callEnemyLogic(enemyLogic -> enemyLogic.postProcessToEnemyMove(beforeLogicResult), battleContext.getEnemy())
//                    .stream().filter(ActorLogicResult::notEmpty).toList();
//            enemySelfReactionResults.forEach(result -> results.addAll(processReactionsToEnemyResult(result)));
//
//            results.addAll(processReactionsToEnemyResult(beforeLogicResult)); // 이쪽은 재귀처리 안함
//        } else {
//            Actor beforeResultActor = battleContext.getAllActors().stream()
//                    .filter(battleActor -> battleActor.getId().equals(beforeLogicResult.getMainActorId()))
//                    .findFirst().orElseThrow(() -> new IllegalArgumentException("이전 actor 없음, actorId = " + beforeLogicResult.getMainActorId()));
//
//            results.addAll(processReactionsToPartyResult(beforeResultActor, beforeLogicResult));
//        }
//
//        return results;
//    }
//
//    /**
//     * 적의 행동에 대한 아군들의 반응 처리
//     * 적 행동 → 아군A 반응 → 아군B 반응 → ... → 각 반응에 대한 재귀
//     */
//    private List<ActorLogicResult> processReactionsToEnemyResultLegacy(ActorLogicResult beforeEnemyResult) {
//        List<ActorLogicResult> results = new ArrayList<>();
//        battleLogService.saveBattleLog(beforeEnemyResult);
//
//        if (beforeEnemyResult.getMoveType() == MoveType.DEAD_DEFAULT) {
//            // CHECK 적 사망시 반응하지 않음.
//            return results;
//        }
//
//        // 적이 먼저 자신의 행동에 대해 반응
//        List<ActorLogicResult> enemySelfReactionResults = callEnemyLogic(enemyLogic -> enemyLogic.postProcessToEnemyMove(beforeEnemyResult), battleContext.getEnemy())
//                .stream().filter(ActorLogicResult::notEmpty).toList();
//        battleLogService.saveBattleLogAll(enemySelfReactionResults);
//        results.addAll(enemySelfReactionResults);
//
//        int[] currentOrderArray = IntStream.range(1, 5).toArray();
//        for (int currentOrder : currentOrderArray) {
//            List<Actor> currentPartyMembers = battleContext.getFrontCharacters(); // CHECK 갱신을 반영하기 위해 반드시 새로가져옴
//            currentPartyMembers.stream()
//                    .filter(partyMember -> partyMember.getCurrentOrder() == currentOrder)
//                    .findFirst().ifPresent(reactingPartyMember -> {
//                        log.info("[processReactionsToEnemyResult] reaction Active, reactingPartyMember = {}", reactingPartyMember);
//                        // 이미 사망한 경우
//                        if (battleContext.getEnemy().isAlreadyDead() || reactingPartyMember.isAlreadyDead()) return;
//
//                        // 사망 여부 처리
//                        ActorLogicResult deadResult = processDead(reactingPartyMember, battleContext.getEnemy());
//                        if (deadResult != null) {
//                            battleLogService.saveBattleLog(deadResult);
//                            results.add(deadResult);
//                            return; // 사망시 즉시반환
//                        }
//
//                        // 1. 이전 적의 행동에 따른 아군 반응을 순서대로 처리
//                        ActorLogicResult reactResult = callCharacterLogic((logic) -> logic.postProcessToEnemyMove(beforeEnemyResult), reactingPartyMember);
//
//                        // 1-2. 이전 적의 행동에 따른 아군 반응이 실제로 발생시, 해당 아군 반응에 대해 추가 아군의 반응 재귀처리
//                        if (reactResult.getMoveType() != MoveType.NONE) {
//                            log.info("[processReactionsToEnemyResult] 반응발생 \n beforeResult = {}\n  reactResult = {}", beforeEnemyResult, reactResult);
//                            results.add(reactResult);
//                            battleLogService.saveBattleLog(reactResult);
//                            results.addAll(processReactionsToPartyResult(reactingPartyMember, reactResult));
//                        }
//
//                        // 1-4. 이전 적의 행동에 대한 아군반응에 대한 추가 아군 반응이 없을시, 다음 순서의 아군 반응을 처리
//                    });
//        }
//
//        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
//    }
//
//
//    /**
//     * 아군의 행동에 대한 반응 처리
//     * 아군 행동 → 적 반응 (+ BREAK 특수처리) → 아군들 반응 → 각 반응에 대한 재귀
//     *
//     * @param beforeResultActor: 이전 행동 결과의 mainActor
//     * @param beforeResult:      이전 행동 결과
//     */
//    private List<ActorLogicResult> processReactionsToPartyResultLegacy(Actor beforeResultActor, ActorLogicResult beforeResult) {
//        List<ActorLogicResult> results = new ArrayList<>();
//        battleLogService.saveBattleLog(beforeResult);
//
//        Actor enemy = battleContext.getEnemy();
//
//        if (beforeResult.getMoveType() == MoveType.DEAD_DEFAULT) {
//            // CHECK 아군이 사망시 반응하지 않음.
//            // 아군이 사망시, partyMembers 에서 즉시 제거됨
//            // 그렇게 되면 이전 결과에 반응할때, 해당 캐릭터의 id 로 partyMembers 에서 더이상 캐릭터를 조회할수없게됨
//            // 캐릭터 로직에서 캐릭터 없는 오류가 발생함 -> 하지만 캐릭터 로직은 파라미터로 넘어오는 partyMemebrs 를 필터링 없이 신뢰하고 싶음.
//            // 이렇게 전파되는걸 막기위해, DEFAULT_DEAD 의 경우 반응하지 않도록 미리설정하고
//            // 필요한경우, CharacterLogic 에 postProcessDead 생성후, emptyResult 를 반환시키고, 필요한 캐릭터 로직이 override 해서 사용하도록 해야할듯?
//            return results;
//        }
//
//        // 1-1. 적이 우선, 아군의 행동에 반응
//        List<ActorLogicResult> enemyReactions = callEnemyLogic((logic) -> logic.postProcessToPartyMove(beforeResult), enemy);
//        results.addAll(enemyReactions);
//
//        // 1-2. 적의 반응으로 BREAK 발생시, 적의 추가 반응
////        enemyReactions.stream()
////                .filter(reaction -> reaction.getMoveType().getParentType() == MoveType.BREAK)
////                .findFirst()
////                .ifPresent(breakReaction -> {
////                    results.addAll(callEnemyLogic((logic) -> logic.postProcessToEnemyMove(breakReaction), enemy));
////                }); // CHECK 적의 BREAK 에 대해서만 발동함, 예조 해제에 따른 추가효과 적용을 위한 특수케이스임. (원래 적의 반응에 대한 추가반응은 구현 X)
//
//        // 2-0. 아군의 반응을 위해, 행동주체의 순서를 맨 앞으로 당김
//        List<Integer> currentOrderList = IntStream.range(1, 5).boxed().collect(Collectors.toList()); // CHECK 파티 멤버는 갱신되거나 없을수 있기 때문에 currentOrder 로 매칭되는 파티원만 실행하도록 함.
//        currentOrderList.remove(beforeResultActor.getCurrentOrder());
//        currentOrderList.addFirst(beforeResultActor.getCurrentOrder());
//
//        for (int currentOrder : currentOrderList) {
//            List<Actor> currentPartyMembers = battleContext.getFrontCharacters(); // CHECK 갱신을 반영하기 위해 반드시 새로가져옴
//            currentPartyMembers.stream()
//                    .filter(partyMember -> partyMember.getCurrentOrder() == currentOrder)
//                    .findFirst().ifPresent(reactingPartyMember -> {
//                        // 이미 사망한 경우
//                        if (enemy.isAlreadyDead() || reactingPartyMember.isAlreadyDead()) return;
//
//                        // 사망 여부 처리
//                        ActorLogicResult deadResult = processDead(reactingPartyMember, enemy);
//                        if (deadResult != null) {
//                            results.add(deadResult);
//                            battleLogService.saveBattleLog(deadResult);
//                            return; // 사망시 즉시반환
//                        }
//
//                        // 2-1. 아군의 행동에 대해 아군이 순서대로(주체먼저) 반응
//                        ActorLogicResult reactResult = callCharacterLogic((logic) -> logic.postProcessToPartyMove(beforeResult), reactingPartyMember);
//
//                        // 2-2. 이전 아군의 행동에 따른 아군 반응이 실제로 발생시, 해당 아군 반응에 대해 추가 아군의 반응 재귀처리
//                        if (reactResult.getMoveType() != MoveType.NONE) {
//                            log.info("[processReactionsToPartyResult] 반응발생 \n beforeResult = {}\n  reactResult = {}", beforeResult, reactResult);
//                            results.add(reactResult);
//                            battleLogService.saveBattleLog(reactResult);
//                            results.addAll(processReactionsToPartyResult(reactingPartyMember, reactResult));
//                        }
//
//                        // 2-3. 혹시모를 무한재귀를 방지
//                        if (results.size() > 50)
//                            throw new IllegalArgumentException("반응 메서드의 결과가 50개를 초과, 마지막 결과: " + results.getLast());
//
//                        // 2-4. 이전 아군의 행동에 대한 아군반응에 대한 추가 아군 반응이 없을시, 다음 순서의 아군 반응을 처리
//                    });
//        }
//
//        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
//    }
}
