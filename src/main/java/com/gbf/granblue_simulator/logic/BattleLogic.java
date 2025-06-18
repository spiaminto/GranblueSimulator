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
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
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
import java.util.stream.Collectors;

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
    private final StatusUtil statusUtil;

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
        progressTurnResults.addAll(processAttack(enemy, partyMembers)); // 아군의 공격 추가
        progressTurnResults.addAll(processEnemyAttack(enemy, partyMembers)); // 적의 공격 추가

        setStatusLogic.progressBattleStatus(enemy, partyMembers); // 배틀 스테이터스 남은 턴수 진행
        partyMembers.forEach(BattleActor::progressAbilityCoolDown); // 배틀 액터 쿨다운 진행

        progressTurnResults.addAll(processTurnEnd(enemy, partyMembers)); // 턴종 처리 추가
        progressTurnResults = progressTurnResults.stream()
                .filter(result -> !result.getMoveType().isNone()).toList();

        enemy.getMember().increaseTurn(); // 기준턴 증가

        progressTurnResults.forEach(result -> log.info("progressTurnResult: {}", result));

        return progressTurnResults;
    }

    protected List<ActorLogicResult> processTurnEnd(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> turnEndResults = new ArrayList<>();
        // 아군 턴종 처리
        partyMembers.forEach(partyMember -> {
            if (partyMember.isGuardOn()) partyMember.toggleGuard(); // 가드 off
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            turnEndResults.addAll(characterLogic.processTurnEnd(partyMember, enemy, partyMembers));
        });

        // 적 턴종료 처리
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyTurnEndResults = enemyLogic.processTurnEnd(enemy, partyMembers);
        turnEndResults.addAll(enemyTurnEndResults);

        saveBattleLogAll(turnEndResults);
        return turnEndResults;
    }

    /**
     * 파라미터로 받은 partyMembers 전원이 enemy 를 대상으로 일반공격
     * 1캐릭 공격 -> 적 반응
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    protected List<ActorLogicResult> processAttack(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();

        for (BattleActor mainActor : partyMembers) {
            CharacterLogic nextCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
            // 공격
            ActorLogicResult moveResult = nextCharacterLogic.processAttack(mainActor, enemy, partyMembers, null);
            // 반응
            results.addAll(postProcessToMove(mainActor, partyMembers, enemy, moveResult));

            MoveType nextMoveType = moveResult.getNextMoveType(); // 후행동 타입
            ActorLogicResult nextMoveResult = moveResult; // 후행동 결과
            // 후행동 (공격 후행동은 타겟이 SELF 만)
            while (nextMoveResult.hasNextMove()) {
                nextMoveResult = switch (nextMoveType.getParentType()) {
                    case ATTACK, CHARGE_ATTACK ->
                            nextCharacterLogic.processAttack(mainActor, enemy, partyMembers, nextMoveType);
                    case ABILITY ->
                            nextCharacterLogic.processAbility(mainActor, enemy, partyMembers, nextMoveType); // 어빌리티, 서포트 어빌리티
                    default ->
                            throw new IllegalArgumentException("[processAttack] Invalid next move type = " + nextMoveType);
                };
                // 반응
                results.addAll(postProcessToMove(mainActor, partyMembers, enemy, nextMoveResult));
                nextMoveType = nextMoveResult.getNextMoveType(); // 후행동 타입으로 갱신
            }
        }
        return results;
    }

    public List<ActorLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move ability = moveRepository.findById(moveId).orElseThrow();
        CharacterLogic mainCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> results = new ArrayList<>();
        // 어빌리티 사용
        ActorLogicResult moveResult = mainCharacterLogic.processAbility(mainActor, enemy, partyMembers, ability.getType());
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, moveResult));

        // 후행동 있음
        if (moveResult.hasNextMove()) {
            // 후행동 할 액터 설정
            List<BattleActor> nextMoveActors = moveResult.getNextMoveTarget() == StatusTargetType.SELF ? List.of(mainActor) : partyMembers;
            MoveType nextMoveType = moveResult.getNextMoveType(); // 후행동 타입
            ActorLogicResult nextMoveResult = moveResult; // 후행동 결과
            // 후행동
            for (BattleActor nextMoveActor : nextMoveActors) {
                CharacterLogic nextCharacterLogic = characterLogicMap.get(nextMoveActor.getActor().getNameEn() + "Logic");
                nextMoveResult = moveResult; // CHECK 나중에 로직 재확인해
                nextMoveType = nextMoveResult.getNextMoveType();
                while (nextMoveResult.hasNextMove()) {
                    nextMoveResult = switch (nextMoveType) {
                        case ATTACK -> nextCharacterLogic.processNormalAttack(nextMoveActor, enemy, partyMembers);
                        case CHARGE_ATTACK ->
                                nextCharacterLogic.processChargeAttack(nextMoveActor, enemy, partyMembers);
                        default ->
                                throw new IllegalArgumentException("[processAbility] Invalid next move type = " + nextMoveType);
                    };
                    // 반응
                    results.addAll(postProcessToMove(nextMoveActor, partyMembers, enemy, nextMoveResult));
                    nextMoveType = nextMoveResult.getNextMoveType(); // 후행동 타입 갱신
                }
            }
        }
        saveBattleLogAll(results);
        return results;
    }

    protected List<ActorLogicResult> processEnemyAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();
        EnemyLogic enemyLogic = enemyLogicMap.get(mainActor.getActor().getNameEn() + "Logic");

        // 공격
        ActorLogicResult attackResult = enemyLogic.processAttack(mainActor, partyMembers);
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, attackResult));

        // 후행동
        MoveType nextMoveType = attackResult.getNextMoveType(); // 후행동 타입
        if (nextMoveType != null) {
            // 적은 현재 후행동으로 ATTACK 만 함, 후행동으로 후행동이 발생하지 않음
            if (!nextMoveType.equals(MoveType.ATTACK))
                throw new IllegalArgumentException("[processEnemyAttack] Invalid next move type = " + nextMoveType);
            ActorLogicResult nextMoveResult = enemyLogic.processAttack(mainActor, partyMembers);
            // 반응
            results.addAll(postProcessToMove(mainActor, partyMembers, enemy, nextMoveResult));
        }

        return results;
    }

    public List<ActorLogicResult> processSummon(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = summonLogic.processSummon(mainActor, enemy, partyMembers, move);
        // 반응
        results.addAll(postProcessToMove(mainActor, partyMembers, enemy, result));
        return results;
    }

    public List<ActorLogicResult> processFatalChain(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
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
            List<StatusEffect> guardDisabledStatusEffects = statusUtil.getStatusEffectMap(mainActor).get(StatusEffectType.GUARD_DISABLED);
            if (guardDisabledStatusEffects == null) {
                mainActor.toggleGuard();
            }
        } else if (targetType == StatusTargetType.PARTY_MEMBERS) {
            boolean mainActorIsGuardOn = mainActor.isGuardOn(); // 파티전체의 경우, 가드 누른 캐릭터와 동일한 상태의 가드만 토글
            partyMembers.forEach(partyMember -> {
                        List<StatusEffect> guardDisabledStatusEffects = statusUtil.getStatusEffectMap(mainActor).get(StatusEffectType.GUARD_DISABLED);
                        if (guardDisabledStatusEffects == null && partyMember.isGuardOn() == mainActorIsGuardOn) {
                            partyMember.toggleGuard();
                        }
                    }
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
        return results;
    }


    @Data
    class BattleLogicRequest {
        private final Long mainActorId;
        private final List<Long> partyMemberIds; // mainActor 포함 전체 id
        private final Long enemyId;

        private final RequestType requestType;
        private final Long requestMoveId;
    }

    enum RequestType {
        ABILITY, ATTACK, SUMMON // 소환
        , PORTION // 포션
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
        List<BattleStatus> mainActorAddedBattleStatuses = logicResult.getAddedBattleStatusesList().stream()
                .peek(list -> log.info("[saveBattleLog] mainActorId = {}, statusList = {}, size = {}", logicResult.getMainBattleActorId(), list, list.size())) // 아군 전체에 대한 스테이터스 적용여부를 로그로 보존
                .filter(list -> !list.isEmpty() && list.getFirst().getBattleActor().getId().equals(logicResult.getMainBattleActorId()))
                .findFirst().orElseGet(ArrayList::new);
        List<String> statusTypes = mainActorAddedBattleStatuses.stream()
                .map(battleStatus -> battleStatus.getStatus().getType().name())
                .toList();
        List<String> statusEffectTypes = mainActorAddedBattleStatuses.stream()
                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().keySet().stream()
                        .map(Enum::name).toList())
                .flatMap(List::stream)
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
                        .statusEffectTypes(statusEffectTypes)
                        .build());

    }

    //    /**
//     * 아군의 공격 행동에 따른 아군과 적의 반응 재귀처리
//     * 파라미터로 들어온 기본 공격행동결과 -> (적 반응 , 아군 반응) 아군반응 있으면 재귀
//     * 아군메인무브 -> 적반응 -> 아군반응1
//     *
//     * @param partyMembers
//     * @param enemy
//     * @param partyPostProcessResult
//     * @return
//     */
//    private List<ActorLogicResult> postProcessToMove(BattleActor mainActor, List<BattleActor> partyMembers, BattleActor enemy, ActorLogicResult partyPostProcessResult) {
//        List<ActorLogicResult> results = new ArrayList<>();
//        results.add(partyPostProcessResult);
//        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
//        // 이전 캐릭터 행동에 대한 적의 반응 (주로 전조처리에 쓰임. 여기에 다시 반응하는 캐릭터는 미구현)
//        List<ActorLogicResult> enemyPostProcessResult = enemyLogic.postProcessToPartyMove(enemy, partyMembers, partyPostProcessResult);
//        saveBattleLogAll(enemyPostProcessResult);
//        results.addAll(enemyPostProcessResult);
//
//        List<BattleActor> modifiedPartyMembers = new ArrayList<>(partyMembers);
//        modifiedPartyMembers.remove(mainActor);
//        modifiedPartyMembers.addFirst(mainActor);
//        for (BattleActor partyMember : modifiedPartyMembers) {
//            // 이전 캐릭터 행동에 대한 현재 캐릭터의 반응
//            CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
//            partyPostProcessResult = partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, partyPostProcessResult);
//            saveBattleLog(partyPostProcessResult);
//            results.add(partyPostProcessResult);
//            // 이전 캐릭터 행동에 대한 현재 캐릭터의 반응이 있을시 현재 캐릭터의 반응에 대한 반응 재귀호출
//            if (partyPostProcessResult.getMoveType() != MoveType.NONE) {
//                results.addAll(postProcessToMove(partyMember, partyMembers, enemy, partyPostProcessResult));
//            }
//        }
//        return results;
//    }

}
