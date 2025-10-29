package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleContext;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.exception.MoveValidationException;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import com.gbf.granblue_simulator.logic.common.TurnEndStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import com.gbf.granblue_simulator.logic.common.dto.PotionResult;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getBattleStatusByEffectType;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getStatusEffectMap;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final Map<String, CharacterLogic> characterLogicMap;
    private final Map<String, EnemyLogic> enemyLogicMap;

    private final BattleContext battleContext;
    private final SummonLogic summonLogic;
    private final SyncLogic syncLogic;

    private final MoveRepository moveRepository;
    private final BattleActorRepository battleActorRepository;
    private final BattleLogRepository battleLogRepository;
    private final TurnEndStatusLogic turnEndStatusLogic;

    public void startBattle() {
        BattleActor enemy = battleContext.getEnemy();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();
        Member currentMember = battleContext.getMember();

        // 기존 적이 있을 경우 동기화
        currentMember.getRoom().getMembers().stream()
                .filter(roomMember -> !roomMember.equals(currentMember))
                .findFirst().ifPresent(syncLogic::syncEnemy);

        partyMembers.forEach(partyMember -> {
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            characterLogic.processBattleStart(partyMember, enemy, partyMembers);
        });
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        enemyLogic.processBattleStart(enemy, partyMembers);
    }

    public List<ActorLogicResult> progressTurn() {
        Member currentMember = battleContext.getMember();

        List<ActorLogicResult> progressTurnResults = new ArrayList<>();
        progressTurnResults.add(syncLogic.processSyncRequest(currentMember)); // 미리 동기화
        progressTurnResults.addAll(processStrike()); // 아군의 공격 추가
        progressTurnResults.addAll(processEnemyStrike()); // 적의 공격 추가
        if (!battleContext.getFrontCharacters().isEmpty()) {
            progressTurnResults.addAll(processTurnEnd()); // 턴종 처리 추가
            //CHECK 나중에 부활기능이 추가될경우, 쿨다운을 위시한 부분을 전처리 해줘야함
        }
        syncLogic.syncEnemy(currentMember); // 모든 행동 종료후 내 결과를 동기화

        progressTurnResults = progressTurnResults.stream()
                .filter(result -> !result.getMoveType().isNone()).toList();

        // 턴 증가
        currentMember.increaseTurn();
        // 행동 쿨타임 설정
        int moveCooldown = calcMemberMoveCooldown(progressTurnResults);
        currentMember.updateLastMovedTimeNow();
        currentMember.updateMoveCooldown(moveCooldown);
        // 공헌도 계산 및 설정
        int honor = calcHonor(progressTurnResults);
        progressTurnResults.getFirst().updateHonor(honor); // 첫번째 결과인 sync 에 세팅
        currentMember.addHonor(honor);

        progressTurnResults.forEach(result -> log.info("[progressTurn] Result: {}", result));

        return progressTurnResults;
    }

    /**
     * 턴 종료처리
     *
     * @return
     */
    protected List<ActorLogicResult> processTurnEnd() {
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();
        BattleActor enemy = battleContext.getEnemy();
        List<ActorLogicResult> turnEndResults = new ArrayList<>();
        // 아군 턴종 처리
        partyMembers.forEach(partyMember -> {
            partyMember.changeGuard(false); // 가드 off
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            turnEndResults.addAll(characterLogic.processTurnEnd(partyMember, enemy, partyMembers));
        }); // CHECK 턴종 처리에 대한 반응 연산 필요할듯

        // 적 턴종료 처리
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyTurnEndResults = enemyLogic.processTurnEnd(enemy, partyMembers);
        turnEndResults.addAll(enemyTurnEndResults);

        // 턴종 스테이터스 처리
        turnEndResults.addAll(turnEndStatusLogic.processTurnEnd(enemy, partyMembers));

        // 전조 발동
        turnEndResults.addAll(enemyLogic.activateOmen(enemy, partyMembers));
        
        // 턴종 후 스테이터스, 상태처리
        turnEndResults.addAll(turnEndStatusLogic.processTurnEndAfter(enemy, partyMembers));

        saveBattleLogAll(turnEndResults);
        return turnEndResults;
    }

    /**
     * 파라미터로 받은 partyMembers 전원이 enemy 를 대상으로 공격행동 수행
     * 1캐릭 공격 -> 적 반응
     *
     * @return
     */
    protected List<ActorLogicResult> processStrike() {
        List<ActorLogicResult> results = new ArrayList<>();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();
        BattleActor enemy = battleContext.getEnemy();

        for (BattleActor mainCharacter : partyMembers) {
            int multiStrikeCount = (int) StatusUtil.getEffectValueMax(mainCharacter, StatusEffectType.MULTI_STRIKE);
            CharacterLogic characterLogic = characterLogicMap.get(mainCharacter.getActor().getNameEn() + "Logic");
            int strikeCount = 0; // 공격행동 카운트
            boolean isNextMoveChargeAttack = false;

            do {
                strikeCount++;
                battleContext.setMainCharacter(mainCharacter);
                ActorLogicResult strikeResult = isNextMoveChargeAttack ?
                        characterLogic.processChargeAttack(mainCharacter, enemy, partyMembers) : // 오의 재발동시 즉시사용 전용
                        characterLogic.processStrike(mainCharacter, enemy, partyMembers);
                isNextMoveChargeAttack = strikeResult.executeChargeAttack();
                // 반응
                results.addAll(postProcessToMove(strikeResult));

                if (strikeCount > 5)
                    throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
            } while (strikeCount < multiStrikeCount);
        }
        return results;
    }

    protected List<ActorLogicResult> processEnemyStrike() {
        BattleActor enemy = battleContext.getEnemy();
        List<ActorLogicResult> results = new ArrayList<>();
        int multiStrikeCount = (int) StatusUtil.getEffectValueMax(enemy, StatusEffectType.MULTI_STRIKE);
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        int strikeCount = 0; // 공격행동 카운트

        do {
            strikeCount++;
            ActorLogicResult strikeResult = enemyLogic.processStrike(enemy, battleContext.getFrontCharacters());
            // 반응
            results.addAll(postProcessToMove(strikeResult));

            if (strikeCount > 5)
                throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
        } while (strikeCount < multiStrikeCount);

        return results;
    }

    /**
     * 사용자 요청에 따른 단일 move 처리
     * @param moveId
     * @return
     */
    public List<ActorLogicResult> processMove(Long moveId) {
        Move move = moveRepository.findById(moveId).orElseThrow();
        Member currentMember = battleContext.getMember();
        List<ActorLogicResult> results = new ArrayList<>();

        ActorLogicResult syncResult = syncLogic.processSyncRequest(currentMember);
        results.add(syncResult);

        List<ActorLogicResult> moveResult =
                switch (move.getType().getParentType()) {
                    case ABILITY -> processAbility(moveId);
                    case SUMMON -> processSummon(moveId);
                    case FATAL_CHAIN -> processFatalChain(moveId);
                    default ->
                            throw new IllegalArgumentException("[processMove] Invalid move type = " + move.getType());
                };
        results.addAll(moveResult);

        syncLogic.syncEnemy(currentMember);

        results.forEach(result -> log.info("[processMove] Result: {}", result));

        // 행동 쿨다운 설정
        int resultMoveCooldown = calcMemberMoveCooldown(results);
        currentMember.updateLastMovedTimeNow();
        currentMember.updateMoveCooldown(resultMoveCooldown);
        // 공헌도 계산
        int honor = calcHonor(results);
        results.getFirst().updateHonor(honor); // 첫번째 결과인 SYNC 에 세팅
        currentMember.addHonor(honor);

        return results;
    }

    /**
     * 어빌리티 처리 (유저가 어빌리티를 직접 눌렀을때, 자동발동은 reaction 으로 처리됨)
     * @param moveId
     * @return
     */
    protected List<ActorLogicResult> processAbility(Long moveId) {
        Move ability = moveRepository.findById(moveId).orElseThrow();
        BattleActor mainCharacter = battleContext.getMainCharacter();
        BattleActor enemy = battleContext.getEnemy();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();

        // 검증
        boolean abilityUsable = mainCharacter.getAbilityUsable(ability.getType());
        if (!abilityUsable) throw new MoveValidationException("어빌리티를 사용할 수 없습니다. [어빌리티 봉인]");

        // 어빌리티 사용
        CharacterLogic characterLogic = characterLogicMap.get(mainCharacter.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult abilityResult = characterLogic.processAbility(mainCharacter, enemy, partyMembers, ability.getType());
        // 반응
        results.addAll(postProcessToMove(abilityResult));

        // 어빌리티 후행동 - 턴 진행 없이 일반공격
        if (abilityResult.getExecuteAttackTargetType() != null) {
            List<BattleActor> executeAttackActors = abilityResult.getExecuteAttackTargetType() == StatusTargetType.SELF ? List.of(mainCharacter) : partyMembers;
            executeAttackActors.forEach(actor -> {
                CharacterLogic logic = characterLogicMap.get(actor.getActor().getNameEn() + "Logic");
                ActorLogicResult executeAttackResult = logic.processAttack(actor, enemy, partyMembers);
                results.addAll(postProcessToMove(executeAttackResult));
            });
        }

        return results;
    }

    protected List<ActorLogicResult> processSummon(Long moveId) {
        BattleActor leaderCharacter = battleContext.getLeaderCharacter();
        BattleActor enemy = battleContext.getEnemy();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();

        // 기본 소환
        Move summonMove = moveRepository.findById(moveId).orElseThrow(() -> new IllegalArgumentException("없는 소환석"));
        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult summonResult = summonLogic.processSummon(leaderCharacter, enemy, partyMembers, summonMove, false);

        // 반응
        results.addAll(postProcessToMove(summonResult));

        // 합체 소환
        Room room = leaderCharacter.getMember().getRoom();
        Long unionSummonId = room.getUnionSummonId();
        if (unionSummonId != null) {
            // 합체 소환 처리
            Move unionSummonMove = moveRepository.findById(unionSummonId).orElseThrow(() -> new IllegalArgumentException("없는 합체 소환석"));
            ActorLogicResult unionSummonResult = summonLogic.processSummon(leaderCharacter, enemy, partyMembers, unionSummonMove, true);
            // 반응
            results.addAll(postProcessToMove(unionSummonResult));
            room.updateUnionSummonId(null);
        } else {
            // 합체 소환 등록
            room.updateUnionSummonId(summonMove.getId());
        }

        results.stream() // summonIds 세팅 (합체소환 까지 확인해서 최종적으로 넣어줌)
                .filter(actorLogicResult -> actorLogicResult.getMoveType() == MoveType.SUMMON_DEFAULT)
                .findFirst().ifPresent(actorLogicResult -> {
                    List<Long> summonIds = new ArrayList<>();
                    summonIds.add(summonMove.getId());
                    if (unionSummonId != null) summonIds.add(unionSummonId);
                    actorLogicResult.updateSummonIds(summonIds);
                }); // 첫 소환에만 세팅, 합체소환 이펙트인 두번째에는 세팅하지 않음

        return results;
    }

    protected List<ActorLogicResult> processFatalChain(Long moveId) {
        Move fatalChain = moveRepository.findById(moveId).orElseThrow();
        BattleActor leaderCharacter = battleContext.getLeaderCharacter();
        CharacterLogic leaderCharacterLogic = characterLogicMap.get(leaderCharacter.getActor().getNameEn() + "Logic");

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = leaderCharacterLogic.processFatalChain(leaderCharacter, battleContext.getEnemy(), battleContext.getFrontCharacters(), fatalChain);
        // 반응
        results.addAll(postProcessToMove(result));
        return results;
    }

    /**
     * 가드 상태 변경후 반환
     *
     * @param battleContext
     * @param targetType
     * @return Map<currentOrder::String, isGuardOn::String> '1' : 'true', ... 파티원 전체
     */
    public List<GuardResult> processGuard(BattleContext battleContext, StatusTargetType targetType) {
        BattleActor mainActor = battleContext.getMainCharacter();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();

        if (targetType == StatusTargetType.SELF) {
            getBattleStatusByEffectType(mainActor, StatusEffectType.GUARD_DISABLED).ifPresentOrElse(
                    battleStatus -> mainActor.changeGuard(false), // 가드불가면 무조건 false 로 변경
                    () -> mainActor.changeGuard(!mainActor.isGuardOn()) // 가드 가능하면 토글
            );
        } else if (targetType == StatusTargetType.PARTY_MEMBERS) {
            boolean mainActorIsGuardOn = mainActor.isGuardOn(); // 파티전체의 경우, 가드 누른 캐릭터와 동일한 상태의 가드만 토글
            partyMembers.forEach(partyMember ->
                    getBattleStatusByEffectType(partyMember, StatusEffectType.GUARD_DISABLED).ifPresentOrElse(
                            battleStatus -> partyMember.changeGuard(false),
                            () -> {
                                if (mainActorIsGuardOn == partyMember.isGuardOn())
                                    partyMember.changeGuard(!partyMember.isGuardOn());
                            }
                    )
            );
        }

        return partyMembers.stream().map(partyMember ->
                GuardResult.builder()
                        .currentOrder(partyMember.getCurrentOrder())
                        .isGuardOn(partyMember.isGuardOn())
                        .build()
        ).toList();
    }

    /**
     * 포션사용을 처리
     * 현재 포션사용은 언데드, 강압 효과 등 스테이터스 효과 관계없이 무조건 회복하도록 설정됨.
     *
     * @param battleContext
     * @param targetType    SELF, PARTY_MEMBERS
     * @return
     */
    public PotionResult processPotion(BattleContext battleContext, StatusTargetType targetType) {
        Member member = battleContext.getMember();
        List<BattleActor> potionTargets = new ArrayList<>();
        if (targetType == StatusTargetType.SELF && battleContext.getMainCharacter() != null) {
            int potionCount = member.getPotionCount();
            if (potionCount <= 0)
                throw new IllegalArgumentException("포션 검증에러, targetType = " + targetType + " potionCount = " + potionCount);
            member.addPotionCount(-1);
            potionTargets = List.of(battleContext.getMainCharacter());
        } else if (targetType == StatusTargetType.PARTY_MEMBERS) {
            int allPotionCount = member.getAllPotionCount();
            if (allPotionCount <= 0)
                throw new IllegalArgumentException("포션 검증에러, targetType = " + targetType + " potionCount = " + allPotionCount);
            member.addAllPotionCount(-1);
            potionTargets = battleContext.getFrontCharacters();
        } else throw new IllegalArgumentException("지원하지 않는 포션사용 타입, targetType = " + targetType);

        List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null));
        potionTargets.forEach(potionTarget -> {
            Integer beforeHp = potionTarget.getHp();
            potionTarget.updateHp(potionTarget.getHp() + potionTarget.getMaxHp() / 2); // 최대 HP 의 절반 회복
            Integer afterHp = potionTarget.getHp();
            healValues.set(potionTarget.getCurrentOrder(), afterHp - beforeHp); // CHECK 현재 포션은 스테이터스 효과와 관계없이 회복하므로 이렇게 구현.
        });

        List<Integer> hps = new ArrayList<>(Collections.nCopies(5, 0));
        List<Integer> hpRates = new ArrayList<>(Collections.nCopies(5, 0));
        battleContext.getFrontCharacters().forEach(battleActor -> {
            hps.set(battleActor.getCurrentOrder(), battleActor.getHp());
            hpRates.set(battleActor.getCurrentOrder(), battleActor.getHpRate());
        });

        return PotionResult.builder()
                .heals(healValues)
                .hps(hps)
                .hpRates(hpRates)
                .potionCount(member.getPotionCount())
                .allPotionCount(member.getAllPotionCount())
                .build();
    }

    protected List<ActorLogicResult> postProcessToMove(ActorLogicResult beforeLogicResult) {
        List<ActorLogicResult> results = new ArrayList<>();
        results.add(beforeLogicResult);

        // 이전 행동에 따른 분기처리
        boolean toEnemy = beforeLogicResult.getMainBattleActorId().equals(battleContext.getEnemy().getId());
        if (toEnemy) {
            results.addAll(processReactionsToEnemyResult(beforeLogicResult)); // 이쪽은 재귀처리 안함
        } else {
            BattleActor beforeResultActor = battleContext.getAllActors().stream()
                    .filter(battleActor -> battleActor.getId().equals(beforeLogicResult.getMainBattleActorId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("이전 actor 없음, actorId = " + beforeLogicResult.getMainActorId()));

            results.addAll(processReactionsToPartyResult(beforeResultActor, beforeLogicResult));
        }

        // 모든 결과 저장 CHECK battleLog 를 이용하는 기믹을 위한 상태 업데이트를 위해 (캐릭터 1명의 행동 + 반응) 마다 즉시 저장
        saveBattleLogAll(results);

        return results;
    }

    /**
     * 적의 행동에 대한 아군들의 반응 처리
     * 적 행동 → 아군A 반응 → 아군B 반응 → ... → 각 반응에 대한 재귀
     */
    private List<ActorLogicResult> processReactionsToEnemyResult(ActorLogicResult beforeEnemyResult) {
        BattleActor enemy = battleContext.getEnemy();
        List<ActorLogicResult> results = new ArrayList<>();
        // CHECK 적 사망시 반응 안해야함

        int[] currentOrderArray = IntStream.range(1, 5).toArray();
        for (int currentOrder : currentOrderArray) {
            List<BattleActor> currentPartyMembers = battleContext.getFrontCharacters(); // CHECK 갱신을 반영하기 위해 반드시 새로가져옴
            currentPartyMembers.stream()
                    .filter(partyMember -> partyMember.getCurrentOrder() == currentOrder)
                    .findFirst().ifPresent(reactingPartyMember -> {
                        log.info("[processReactionsToEnemyResult] reaction Active, reactingPartyMember = {}", reactingPartyMember);

                        // 1. 이전 적의 행동에 따른 아군 반응을 순서대로 처리
                        CharacterLogic logic = characterLogicMap.get(reactingPartyMember.getActor().getNameEn() + "Logic");
                        ActorLogicResult reactResult = logic.postProcessToEnemyMove(reactingPartyMember, enemy, currentPartyMembers, beforeEnemyResult);

                        // 1-2. 이전 적의 행동에 따른 아군 반응이 실제로 발생시, 해당 아군 반응에 대해 추가 아군의 반응 재귀처리
                        if (reactResult.getMoveType() != MoveType.NONE) {
                            log.info("[processReactionsToEnemyResult] 반응발생 \n beforeResult = {}\n  reactResult = {}", beforeEnemyResult, reactResult);
                            results.add(reactResult);
                            results.addAll(processReactionsToPartyResult(reactingPartyMember, reactResult));
                        }

                        // 1-4. 이전 적의 행동에 대한 아군반응에 대한 추가 아군 반응이 없을시, 다음 순서의 아군 반응을 처리
                    });
        }

        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
    }


    /**
     * 아군의 행동에 대한 반응 처리
     * 아군 행동 → 적 반응 (+ BREAK 특수처리) → 아군들 반응 → 각 반응에 대한 재귀
     *
     * @param beforeResultActor: 이전 행동 결과의 mainActor
     * @param beforeResult:      이전 행동 결과
     */
    private List<ActorLogicResult> processReactionsToPartyResult(BattleActor beforeResultActor, ActorLogicResult beforeResult) {
        List<ActorLogicResult> results = new ArrayList<>();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();
        BattleActor enemy = battleContext.getEnemy();
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");

        if (beforeResult.getMoveType() == MoveType.DEAD_DEFAULT) {
            // CHECK 아군이 사망시 반응하지 않음.
            // 아군이 사망시, partyMembers 에서 즉시 제거됨
            // 그렇게 되면 이전 결과에 반응할때, 해당 캐릭터의 id 로 partyMembers 에서 더이상 캐릭터를 조회할수없게됨
            // 캐릭터 로직에서 캐릭터 없는 오류가 발생함 -> 하지만 캐릭터 로직은 파라미터로 넘어오는 partyMemebrs 를 필터링 없이 신뢰하고 싶음.
            // 이렇게 전파되는걸 막기위해, DEFAULT_DEAD 의 경우 반응하지 않도록 미리설정하고
            // 필요한경우, CharacterLogic 에 postProcessDead 생성후, emptyResult 를 반환시키고, 필요한 캐릭터 로직이 override 해서 사용하도록 해야할듯?
            return results;
        }

        // 1-1. 적이 우선, 아군의 행동에 반응
        List<ActorLogicResult> enemyReactions = enemyLogic.postProcessToPartyMove(enemy, partyMembers, beforeResult);
        results.addAll(enemyReactions);

        // 1-2. 적의 반응으로 BREAK 발생시, 적의 추가 반응
        enemyReactions.stream()
                .filter(reaction -> reaction.getMoveType().getParentType() == MoveType.BREAK)
                .findFirst()
                .ifPresent(breakReaction -> {
                    results.addAll(enemyLogic.postProcessToEnemyMove(enemy, partyMembers, breakReaction));
                }); // CHECK 적의 BREAK 에 대해서만 발동함, 예조 해제에 따른 추가효과 적용을 위한 특수케이스임. (원래 적의 반응에 대한 추가반응은 구현 X)

        // 2-0. 아군의 반응을 위해, 행동주체의 순서를 맨 앞으로 당김
        List<Integer> currentOrderList = IntStream.range(1, 5).boxed().collect(Collectors.toList()); // CHECK 파티 멤버는 갱신되거나 없을수 있기 때문에 currentOrder 로 매칭되는 파티원만 실행하도록 함.
        currentOrderList.remove(beforeResultActor.getCurrentOrder());
        currentOrderList.addFirst(beforeResultActor.getCurrentOrder());

        for (int currentOrder : currentOrderList) {
            List<BattleActor> currentPartyMembers = battleContext.getFrontCharacters(); // CHECK 갱신을 반영하기 위해 반드시 새로가져옴
            currentPartyMembers.stream()
                    .filter(partyMember -> partyMember.getCurrentOrder() == currentOrder)
                    .findFirst().ifPresent(reactingPartyMember -> {
                        // 2-1. 아군의 행동에 대해 아군이 순서대로(주체먼저) 반응
                        CharacterLogic logic = characterLogicMap.get(reactingPartyMember.getActor().getNameEn() + "Logic");
                        ActorLogicResult reactResult = logic.postProcessToPartyMove(reactingPartyMember, enemy, currentPartyMembers, beforeResult);

                        // 2-2. 이전 아군의 행동에 따른 아군 반응이 실제로 발생시, 해당 아군 반응에 대해 추가 아군의 반응 재귀처리
                        if (reactResult.getMoveType() != MoveType.NONE) {
                            log.info("[processReactionsToPartyResult] 반응발생 \n beforeResult = {}\n  reactResult = {}", beforeResult, reactResult);
                            results.add(reactResult);
                            results.addAll(processReactionsToPartyResult(reactingPartyMember, reactResult));
                        }

                        // 2-3. 혹시모를 무한재귀를 방지
                        if (results.size() > 50)
                            throw new IllegalArgumentException("반응 메서드의 결과가 50개를 초과, 마지막 결과: " + results.getLast());

                        // 2-4. 이전 아군의 행동에 대한 아군반응에 대한 추가 아군 반응이 없을시, 다음 순서의 아군 반응을 처리
                    });
        }

        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
    }

    protected void saveBattleLogAll(List<ActorLogicResult> results) {
        results.forEach(this::saveBattleLog);
    }

    protected void saveBattleLog(ActorLogicResult logicResult) {
        if (logicResult.getMoveType().isNone()) return;
        // 현재 Status 및 관련사항은 mainActor 것만 저장함
        BattleActor mainActor = battleActorRepository.findById(logicResult.getMainBattleActorId()).orElseThrow(() -> new IllegalArgumentException("[saveBattleLog] mainActorId not present, id = " + logicResult.getMainBattleActorId()));
        BattleActor enemy = battleContext.getEnemy();

        Long targetActorId = mainActor.isCharacter() && logicResult.getTotalHitCount() > 0 ? enemy.getActor().getId() : null; // 배틀로그중, 특정 상태의 적을 필터링 하기 위해 사용

        List<Integer> damages = logicResult.getDamages();

        List<String> damageElementTypes = logicResult.getDamageElementTypes().stream()
                .map(ElementType::name)
                .toList();

        int[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                .map(list -> list.stream()
                        .mapToInt(Integer::intValue)
                        .toArray())
                .toArray(int[][]::new);

        List<BattleStatusDto> mainActorAddedBattleStatuses = logicResult.getAddedBattleStatusesList().stream()
//                .peek(list -> log.info("[saveBattleLog] mainActorId = {}, statusList = {}, size = {}", logicResult.getMainBattleActorId(), list, list.size())) // 아군 전체에 대한 스테이터스 적용여부를 로그로 보존
                .filter(list -> !list.isEmpty() && list.getFirst().getBattleActorId().equals(logicResult.getMainBattleActorId()))
                .findFirst().orElseGet(ArrayList::new);

        List<Long> statusIds = mainActorAddedBattleStatuses.stream()
                .map(BattleStatusDto::getStatusId)
                .toList();

        List<String> statusTypes = mainActorAddedBattleStatuses.stream()
                .map(battleStatus -> battleStatus.getStatusType().name())
                .toList();

        battleLogRepository.save(
                BattleLog.builder()
                        .roomId(mainActor.getMember().getRoom().getId())
                        .userId(mainActor.getMember().getUser().getId())
                        .currentTurn(logicResult.getCurrentTurn())
                        .moveType(logicResult.getMoveType())
                        .mainActorId(logicResult.getMainActorId())
                        .targetActorId(targetActorId)
                        .hitCount(logicResult.getTotalHitCount())
                        .damages(damages)
                        .damageElementTypes(damageElementTypes)
                        .additionalDamages(additionalDamages)
                        .statusTypes(statusTypes)
                        .statusIds(statusIds)
                        .build());

    }

    public ActorLogicResult syncBattle(Member member) {
        return syncLogic.processSyncRequest(member);

    }

    protected int calcMemberMoveCooldown(List<ActorLogicResult> results) {
        int resultMoveCooldown = 1;
        for (ActorLogicResult result : results) {
            if (result.getMainBattleActorOrder() == 0 || result.getMoveType().isNone()) continue;
            int moveCoolDown = switch (result.getMoveType().getParentType()) {
                case ATTACK -> 3;
                case ABILITY -> 2;
                case CHARGE_ATTACK -> 4;
                case SUMMON -> 4;
                case FATAL_CHAIN -> 4;
                default -> {
                    log.warn("[calcMemberMoveCooldown] default case, moveType = {}, result = {}", result.getMoveType(), result);
                    yield 1;
                }
            };
            log.info("[calcMemberMoveCooldown] moveType = {}, moveCoolDown = {}", result.getMoveType(), moveCoolDown);
            resultMoveCooldown += moveCoolDown;
        }
        log.info("[calcMemberMoveCooldown] resultMoveCooldown = {}", resultMoveCooldown);
        return resultMoveCooldown;
    }

    private final Map<String, Integer> additionalHonorMovenameMap = Map.of(
            "팔랑크스", 1,
            "미제라블 미스트", 1
    );
    protected int calcHonor(List<ActorLogicResult> results) {
        int resultHonor = 0;

        Integer basicMaxHonor = battleContext.getEnemy().getMaxHp() / 100; // 기본 총 공헌도는 적 체력의 1%로 함 (적 체력 1억 시, 기본 총 공헌도 100만)
        // 추가 공헌도는 조건을 통해 얻으며, 기본 총 공헌도를 기준으로 획득 (따라서, 최종 공헌도는 기본 총 공헌도를 넘어감)
        // 원본 게임이 적 최대체력 기준 비율로 계산하므로 그와 비슷하게 계산. 단위만 줄임
        for (ActorLogicResult result : results) {

            //1. 특정 주인공의 어빌리티 사용시 기본 총 공헌도의 1% 분의 공헌도 획득
            if (result.getMoveName() != null) {
                Integer value = additionalHonorMovenameMap.get(result.getMoveName());
                if (value != null) resultHonor += basicMaxHonor / 100;
                log.info("[calcHonor] ABILITY moveName = {}, resultHonor = {}", result.getMoveName(), resultHonor);
            }

            //2. 적의 전조를 해제시 기본 총 공헌도의 1% 분의 공헌도를 획득
            if (result.getMoveType().getParentType() == MoveType.BREAK) {
                resultHonor += basicMaxHonor / 100;
                log.info("[calcHonor] BREAK moveType = {}, resultHonor = {}", result.getMoveType(), resultHonor);
            }
            
            // 3. 자신이 입힌 데미지의 1% 만큼 데미지 공헌도 획득 ( = 기본 총 공헌도 분배)
            if (result.getTotalHitCount() > 0) {
                Integer damageSum = result.getDamages().stream().reduce(0, Integer::sum);
                resultHonor += damageSum / 100;

                Integer additionalDamageSum = result.getAdditionalDamages().stream()
                        .flatMap(Collection::stream)
                        .reduce(0, Integer::sum);
                resultHonor += additionalDamageSum / 100;
                log.info("[calcHonor] DAMAGE moveType = {}, resultHonor = {}", result.getMoveType(), resultHonor);
            }

            log.info("[calcHonor] resultHonor = {}", resultHonor);
        }

        return resultHonor;
    }












    // ==== 참고용, 나중에 삭제 ======================================================================

    /**
     * 이전 행동으로 발생한 결과에 따른 아군과 적의 반응 재귀처리
     * 이전 행동이 아군행동 인 경우 : 아군메인행동1 -> 적메인반응1 -> 아군반응1-1 -> 적 반응1-1 -> 아군반응1-1-1(없음) -> 아군반응1-2 -> 적반응1-2 -> 아군반응 1-2-1(없음) -> 아군반응 1-2-2(없음) -> 아군반응 1-2-3(없음) -> 아군반응 1-2-4(없음) -> 아군반응 1-3 -> 적반응 1-3 -> 아군반응 1-3-1 ...
     * 이전 행동이 적 행동인경우 : 적메인행동1 -> 적메인반응1 -> 아군반응1 -> 적반응1 -> ... 이하동일
     * 이전 행동이 적 행동인 경우 첫 호출은 적에대한 반응, 이후 재귀는 아군에 대한 반응으로 실행
     * 적의 반응에 대한 아군의 반응은 미구현
     * 아군의 반응에 대해서는 아군 전체가 재귀로 반응함.
     * 아군의 아군에 대한 반응의 경우, 이전 행동한 아군 본인이 처음으로 반응하도록 순서변경
     *
     * @param mainActor         : 이전 행동의 실행주체 (적, 아군 모두)
     * @param partyMembers
     * @param enemy
     * @param beforeLogicResult
     * @return
     */
    protected List<ActorLogicResult> postProcessToMoveLegacy(BattleActor mainActor, List<BattleActor> partyMembers, BattleActor enemy, ActorLogicResult beforeLogicResult) {
        List<ActorLogicResult> results = new ArrayList<>();
        // 이전 행동 저장
//        saveBattleLog(beforeLogicResult);
        results.add(beforeLogicResult);
        boolean toEnemy = beforeLogicResult.getMainBattleActorId().equals(enemy.getId()); // 첫 실행시 true, 이후 재귀 실행시 false
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        // 이전 행동에 대한 적의 반응
        List<ActorLogicResult> enemyPostProcessResult = new ArrayList<>();
        List<BattleActor> modifiedPartyMembers = null;
        if (toEnemy) {
            enemyPostProcessResult.addAll(enemyLogic.postProcessToEnemyMove(enemy, partyMembers, beforeLogicResult));
        } else {
            // 재귀실행부터는 이쪽을 사용 (파티에 대한 반응)
            enemyPostProcessResult.addAll(enemyLogic.postProcessToPartyMove(enemy, partyMembers, beforeLogicResult));
            // 이전 행동 본인이 우선하도록 순서조정
            modifiedPartyMembers = new ArrayList<>(partyMembers);
            modifiedPartyMembers.remove(mainActor);
            modifiedPartyMembers.addFirst(mainActor);
        }
//        saveBattleLogAll(enemyPostProcessResult);
        results.addAll(enemyPostProcessResult);

        // 적의 반응에 대한 반응 CHECK 현재 BREAK 상태를 감지하여 사용. 나중에 사용례가 늘어날경우 리팩토링
        enemyPostProcessResult.stream()
                .filter(result -> result.getMoveType().getParentType() == MoveType.BREAK)
                .findFirst().ifPresent(result -> {
                    List<ActorLogicResult> enemyAdditionalPostProcessResult = enemyLogic.postProcessToEnemyMove(enemy, partyMembers, result);
//                    saveBattleLogAll(enemyAdditionalPostProcessResult);
                    results.addAll(enemyAdditionalPostProcessResult);
                });

        for (BattleActor partyMember : modifiedPartyMembers != null ? modifiedPartyMembers : partyMembers) {
            CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            ActorLogicResult afterLogicResult = toEnemy ?
                    partyMemberLogic.postProcessToEnemyMove(partyMember, enemy, partyMembers, beforeLogicResult) :
                    partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, beforeLogicResult);
            // 이전 캐릭터 행동에 대한 현재 캐릭터의 반응이 있을시 현재 캐릭터의 반응에 대한 반응 재귀호출
            if (afterLogicResult.getMoveType() != MoveType.NONE) {
                results.addAll(postProcessToMoveLegacy(partyMember, partyMembers, enemy, afterLogicResult));
            }
        }

        // NONE 없앤후 반환
        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
    }

}
