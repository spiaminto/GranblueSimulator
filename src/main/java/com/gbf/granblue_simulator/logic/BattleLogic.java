package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import com.gbf.granblue_simulator.logic.common.TurnEndStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getBattleStatusByEffectType;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getStatusEffectMap;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final Map<String, CharacterLogic> characterLogicMap;
    private final Map<String, EnemyLogic> enemyLogicMap;

    private final SummonLogic summonLogic;

    private final MoveRepository moveRepository;
    private final BattleActorRepository battleActorRepository;
    private final BattleLogRepository battleLogRepository;
    private final SetStatusLogic setStatusLogic;
    private final TurnEndStatusLogic turnEndStatusLogic;

    public void startBattle(List<BattleActor> partyMembers, BattleActor enemy) {
        partyMembers.forEach(partyMember -> {
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            characterLogic.processBattleStart(partyMember, enemy, partyMembers);
        });
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        enemyLogic.processBattleStart(enemy, partyMembers);
    }

    public List<ActorLogicResult> progressTurn(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> progressTurnResults = new ArrayList<>();
        progressTurnResults.addAll(processStrike(enemy, partyMembers)); // 아군의 공격 추가
        progressTurnResults.addAll(processEnemyStrike(enemy, partyMembers)); // 적의 공격 추가
        progressTurnResults.addAll(processTurnEnd(enemy, partyMembers)); // 턴종 처리 추가
        partyMembers.forEach(BattleActor::progressAbilityCoolDown); // 어빌리티 쿨다운 진행
        partyMembers.forEach(BattleActor::resetAbilityUseCount); // 어빌리티 사용횟수 초기화
        partyMembers.forEach(BattleActor::resetStrikeCount); // 공격 행동 횟수 초기화

        setStatusLogic.progressBattleStatus(enemy, partyMembers); // 배틀 스테이터스 남은 턴수 진행

        progressTurnResults = progressTurnResults.stream()
                .filter(result -> !result.getMoveType().isNone()).toList();

        enemy.getMember().increaseTurn(); // 기준턴 증가

        progressTurnResults.forEach(result -> log.info("progressTurnResult: {}", result));

        return progressTurnResults;
    }

    /**
     * 턴 종료처리
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    private List<ActorLogicResult> processTurnEnd(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> turnEndResults = new ArrayList<>();
        // 아군 턴종 처리
        partyMembers.forEach(partyMember -> {
            partyMember.changeGuard(false); // 가드 off
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            turnEndResults.addAll(characterLogic.processTurnEnd(partyMember, enemy, partyMembers));
        });

        // 적 턴종료 처리
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyTurnEndResults = enemyLogic.processTurnEnd(enemy, partyMembers);
        turnEndResults.addAll(enemyTurnEndResults);

        // 턴종 스테이터스 처리
        turnEndResults.addAll(turnEndStatusLogic.processTurnEnd(enemy, partyMembers));

        // 전조 발동 (턴종 마지막 처리)
        turnEndResults.addAll(enemyLogic.activateOmen(enemy, partyMembers));

        saveBattleLogAll(turnEndResults);
        return turnEndResults;
    }

    /**
     * 파라미터로 받은 partyMembers 전원이 enemy 를 대상으로 공격행동 수행
     * 1캐릭 공격 -> 적 반응
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    private List<ActorLogicResult> processStrike(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();

        for (BattleActor mainActor : partyMembers) {
            int multiStrikeCount = (int) StatusUtil.getEffectValueMax(mainActor, StatusEffectType.MULTI_STRIKE);
            CharacterLogic characterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
            int strikeCount = 0; // 공격행동 카운트
            boolean isNextMoveChargeAttack = false;

            do {
                strikeCount++;
                ActorLogicResult strikeResult = isNextMoveChargeAttack ?
                        characterLogic.processChargeAttack(mainActor, enemy, partyMembers) :
                        characterLogic.processStrike(mainActor, enemy, partyMembers);
                isNextMoveChargeAttack = strikeResult.executeChargeAttack();
                // 반응
                results.addAll(postProcessToMove(mainActor, partyMembers, enemy, strikeResult));

                if (strikeCount > 5)
                    throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
            } while (strikeCount < multiStrikeCount);
        }
        return results;
    }

    private List<ActorLogicResult> processEnemyStrike(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();
        int multiStrikeCount = (int) StatusUtil.getEffectValueMax(mainActor, StatusEffectType.MULTI_STRIKE);
        EnemyLogic enemyLogic = enemyLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        int strikeCount = 0; // 공격행동 카운트

        do {
            strikeCount++;
            ActorLogicResult strikeResult = enemyLogic.processStrike(mainActor, partyMembers);
            // 반응
            results.addAll(postProcessToMove(mainActor, partyMembers, enemy, strikeResult));

            if (strikeCount > 5)
                throw new IllegalStateException("[processEnemyStrike] strikeCount exceeded, strikeCount = " + strikeCount);
        } while (strikeCount < multiStrikeCount);

        return results;
    }

    public List<ActorLogicResult> processMove(BattleActor mainActor, BattleEnemy enemy, List<BattleActor> partyMembers, Long moveId) {
        Move move = moveRepository.findById(moveId).orElseThrow();
        return switch (move.getType().getParentType()) {
            case ABILITY -> processAbility(mainActor, enemy, partyMembers, moveId);
            case SUMMON -> processSummon(mainActor, enemy, partyMembers, moveId);
            case FATAL_CHAIN -> processFatalChain(mainActor, enemy, partyMembers, moveId);
            default -> throw new IllegalArgumentException("[processMove] Invalid move type = " + move.getType());
        };
    }

    private List<ActorLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move ability = moveRepository.findById(moveId).orElseThrow();
        CharacterLogic characterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> results = new ArrayList<>();
        // 어빌리티 사용
        ActorLogicResult abilityResult = characterLogic.processAbility(mainActor, enemy, partyMembers, ability.getType());
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, abilityResult));

        // 어빌리티 후행동 - 턴 진행 없이 일반공격
        if (abilityResult.getExecuteAttackTargetType() != null) {
            List<BattleActor> executeAttackActors = abilityResult.getExecuteAttackTargetType() == StatusTargetType.SELF ? List.of(mainActor) : partyMembers;
            executeAttackActors.forEach(actor -> {
                CharacterLogic logic = characterLogicMap.get(actor.getActor().getNameEn() + "Logic");
                ActorLogicResult executeAttackResult = logic.processAttack(actor, enemy, partyMembers);
                results.addAll(postProcessToMove(actor, partyMembers, enemy, executeAttackResult));
            });
        }

        saveBattleLogAll(results);
        return results;
    }

    private List<ActorLogicResult> processSummon(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = summonLogic.processSummon(mainActor, enemy, partyMembers, move);
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, result));
        return results;
    }

    private List<ActorLogicResult> processFatalChain(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move fatalChain = moveRepository.findById(moveId).orElseThrow();
        CharacterLogic mainCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = mainCharacterLogic.processFatalChain(mainActor, enemy, fatalChain);
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, result));
        return results;
    }

    /**
     * 가드 상태 변경후 반환
     *
     * @param mainActor
     * @param partyMembers
     * @param targetType
     * @return Map<currentOrder::String, isGuardOn::String> '1' : 'true', ... 파티원 전체
     */
    public List<GuardResult> processGuard(BattleActor mainActor, List<BattleActor> partyMembers, StatusTargetType targetType) {
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
                            () -> { if (mainActorIsGuardOn == partyMember.isGuardOn()) partyMember.changeGuard(!partyMember.isGuardOn()); }
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
    protected List<ActorLogicResult> postProcessToMove(BattleActor mainActor, List<BattleActor> partyMembers, BattleActor enemy, ActorLogicResult beforeLogicResult) {
        List<ActorLogicResult> results = new ArrayList<>();
        // 이전 행동 저장
        saveBattleLog(beforeLogicResult);
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
        saveBattleLogAll(enemyPostProcessResult);
        results.addAll(enemyPostProcessResult);

        // 적의 반응에 대한 반응 CHECK 현재 BREAK 상태를 감지하여 사용. 나중에 사용례가 늘어날경우 리팩토링
        enemyPostProcessResult.stream()
                .filter(result -> result.getMoveType().getParentType() == MoveType.BREAK)
                .findFirst().ifPresent(result -> {
                    List<ActorLogicResult> enemyAdditionalPostProcessResult = enemyLogic.postProcessToEnemyMove(enemy, partyMembers, result);
                    saveBattleLogAll(enemyAdditionalPostProcessResult);
                    results.addAll(enemyAdditionalPostProcessResult);
                });

        for (BattleActor partyMember : modifiedPartyMembers != null ? modifiedPartyMembers : partyMembers) {
            CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            ActorLogicResult afterLogicResult = toEnemy ?
                    partyMemberLogic.postProcessToEnemyMove(partyMember, enemy, partyMembers, beforeLogicResult) :
                    partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, beforeLogicResult);
            // 이전 캐릭터 행동에 대한 현재 캐릭터의 반응이 있을시 현재 캐릭터의 반응에 대한 반응 재귀호출
            if (afterLogicResult.getMoveType() != MoveType.NONE) {
                results.addAll(postProcessToMove(partyMember, partyMembers, enemy, afterLogicResult));
            }
        }

        // NONE 없앤후 반환
        return results.stream().filter(actorLogicResult -> actorLogicResult.getMoveType() != MoveType.NONE).toList();
    }

    protected void saveBattleLogAll(List<ActorLogicResult> results) {
        results.forEach(this::saveBattleLog);
    }

    protected void saveBattleLog(ActorLogicResult logicResult) {
        if (logicResult.getMoveType().isNone()) return;
        // 현재 Status 및 관련사항은 mainActor 것만 저장함
        BattleActor mainActor = battleActorRepository.findById(logicResult.getMainBattleActorId()).orElseThrow(() -> new IllegalArgumentException("[saveBattleLog] mainActorId not present, id = " + logicResult.getMainBattleActorId()));
        List<Integer> damages = logicResult.getDamages();
        List<String> damageElementTypes = logicResult.getDamageElementTypes().stream()
                .map(ElementType::name)
                .toList();
        Integer[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                .map(additionalDamage -> additionalDamage.toArray(Integer[]::new))
                .toArray(Integer[][]::new);
        List<BattleStatusDto> mainActorAddedBattleStatuses = logicResult.getAddedBattleStatusesList().stream()
                .peek(list -> log.info("[saveBattleLog] mainActorId = {}, statusList = {}, size = {}", logicResult.getMainBattleActorId(), list, list.size())) // 아군 전체에 대한 스테이터스 적용여부를 로그로 보존
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
                        .targetActorId(logicResult.getTargetActorId())
                        .hitCount(logicResult.getTotalHitCount())
                        .damages(damages)
                        .damageElementTypes(damageElementTypes)
                        .additionalDamages(additionalDamages)
                        .statusTypes(statusTypes)
                        .statusIds(statusIds)
                        .build());

    }

}
