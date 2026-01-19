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
import com.gbf.granblue_simulator.battle.logic.statuseffect.TurnEndStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.SummonLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
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
import static java.util.function.Predicate.not;

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
    public List<ActorLogicResult> processAbility(BaseMove ability) {
        Actor mainCharacter = battleContext.getMainActor();
        Actor enemy = battleContext.getEnemy();

        // 어빌리티 사용
        ActorLogicResult abilityResult = callCharacterLogic((logic) -> logic.processAbility(ability.getType()), mainCharacter);
        List<ActorLogicResult> results = new ArrayList<>();

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

    public List<ActorLogicResult> processFatalChain(BaseMove fatalChain) {
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
    public List<ActorLogicResult> processSummon(BaseMove summon, boolean doUnionSummon) {
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
                BaseMove unionSummonMove = moveRepository.findById(unionSummonId).orElseThrow(() -> new IllegalArgumentException("없는 합체 소환석"));
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
                    .filter(not(ActorLogicResult::isEmpty)).toList();

            // 턴종 결과는 여러개라, 모두 반응처리
            characterTurnEndResults.forEach(characterTurnEndResult -> turnEndResults.addAll(postProcessToMove(characterTurnEndResult)));
        });

        // 적 턴종료 처리
        List<ActorLogicResult> enemyTurnEndResults = callEnemyLogic(EnemyLogic::processTurnEnd, enemy).stream()
                .filter(not(ActorLogicResult::isEmpty)).toList();
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
        if (firstBaseResult.isEmpty()) return Collections.emptyList();
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
        enemyReactions = enemyReactions.stream().filter(not(ActorLogicResult::isEmpty)).toList();
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
            List<Actor> currentPartyMembers = battleContext.getFrontCharacters(); // 반응할 아군을 갱신
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
                        if (!reactResult.isEmpty()) {
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
}
