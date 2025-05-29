package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.ElementType;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final Map<String, CharacterLogic> characterLogicMap;
    private final Map<String, EnemyLogic> enemyLogicMap;
    private final BattleActorRepository battleActorRepository;
    private final MoveRepository moveRepository;
    private final BattleLogRepository battleLogRepository;
    private final ActorLogicUtil actorLogicUtil;
    private final SummonLogic summonLogic;
    private final ActorRepository actorRepository;

    /*
    캐릭터 어빌리티발동
     -> 캐릭터 로직실행 -> 캐릭터 후행동 -> 아군 post process 실행
     -> 적 post process 실행 (여기에 전조 포함) -> 결과반환
     */

    public void startBattle(List<BattleActor> partyMembers, BattleActor enemy) {
        partyMembers.forEach(partyMember -> {
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            characterLogic.processBattleStart(partyMember, enemy, partyMembers);
        });
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        enemyLogic.onBattleStart(enemy, partyMembers);
    }

//    public List<ActorLogicResult> process(BattleLogicRequest request) {
//        BattleActor mainActor = battleActorRepository.findById(request.getMainActorId()).orElseThrow();
//        //TODO 정렬필요
//        List<BattleActor> partyMembers = battleActorRepository.findAllById(request.getPartyMemberIds());
//        BattleActor enemy = battleActorRepository.findById(request.getEnemyId()).orElseThrow();
//
//        List<ActorLogicResult> results = new ArrayList<>();
//        if (request.getRequestType() == RequestType.ATTACK) {
//            results = processAttack(enemy, partyMembers);
//        } else if (request.getRequestType() == RequestType.ABILITY) {
//            results = processAbility(mainActor, enemy, partyMembers, request.getRequestMoveId());
//        }
//        return results;
//    }

    public List<ActorLogicResult> progressTurn(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> progressTurnResults = new ArrayList<>();
        progressTurnResults.addAll(processAttack(enemy, partyMembers)); // 아군의 공격 추가
        progressTurnResults.addAll(processEnemyAttack(enemy, partyMembers)); // 적의 공격 추가
        progressTurnResults.addAll(processTurnEnd(enemy, partyMembers)); // 턴종 처리 추가
        progressTurnResults = progressTurnResults.stream().filter(Objects::nonNull).toList();

        actorLogicUtil.progressTurn(enemy, partyMembers); // 턴진행 부가처리
        progressTurnResults.forEach(result -> log.info("progressTurnResult: {}", result));

        return progressTurnResults;
    }

    protected List<ActorLogicResult> processTurnEnd(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> turnEndResults = new ArrayList<>();
        // 아군 턴종 처리
        partyMembers.forEach(partyMember -> {
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            turnEndResults.addAll(characterLogic.processTurnEnd(partyMember, enemy, partyMembers));
        });

        // 적 턴종료 처리
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyTurnEndResults = enemyLogic.onTurnEnd(enemy, partyMembers);
        turnEndResults.addAll(enemyTurnEndResults);
        if (!enemyTurnEndResults.isEmpty() && enemyTurnEndResults.getFirst().getMoveType() == MoveType.FORM_CHANGE) {
            // 폼체인지 시 변한 폼으로 턴종 한번 더 실행하여 전조갱신
            EnemyLogic formChangedEnemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
            turnEndResults.addAll(formChangedEnemyLogic.onTurnEnd(enemy, partyMembers));// CT기 또는 HP 트리거 갱신 (영창기는 스킵됨)
        }

        saveBattleLogAll(turnEndResults);
        return turnEndResults;
    }

    /**
     * 파라미터로 받은 partyMembers 전원이 enemy 를 대상으로 일반공격
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    public List<ActorLogicResult> processAttack(BattleActor enemy, List<BattleActor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic"); // 적 로직은 턴 종료시만 갱신

        for (BattleActor moveActor : partyMembers) {
            CharacterLogic nextCharacterLogic = characterLogicMap.get(moveActor.getActor().getNameEn() + "Logic");

            // 공격
            ActorLogicResult moveResult = nextCharacterLogic.processAttack(moveActor, enemy, partyMembers, null);
            results.add(moveResult);
            MoveType nextMoveType = moveResult.getNextMoveType(); // 후행동 타입
            for (BattleActor partyMember : partyMembers) {
                // 아군의 반응 (후행 반복 x) TODO 나중에 후행반복 해야할 수도 있음.
                CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
                results.add(partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, moveResult));
            }
            results.addAll(enemyLogic.onOtherMove(enemy, partyMembers, moveResult)); // 적의 반응

            // 후행동
            while (moveResult.hasNextMove()) {
                moveResult = switch (nextMoveType.getParentType()) {
                    case ATTACK, CHARGE_ATTACK ->
                            nextCharacterLogic.processAttack(moveActor, enemy, partyMembers, nextMoveType);
                    case ABILITY, SUPPORT_ABILITY ->
                            nextCharacterLogic.processAbility(moveActor, enemy, partyMembers, nextMoveType); // 어빌리티, 서포트 어빌리티
                    default -> throw new IllegalArgumentException("Invalid move type = " + nextMoveType);
                };
                results.add(moveResult);
                nextMoveType = moveResult.getNextMoveType(); // 후행동 타입으로 갱신
                for (BattleActor partyMember : partyMembers) {
                    // 아군의 반응 (후행 반복 x)
                    CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
                    results.add(partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, moveResult));
                }
                results.addAll(enemyLogic.onOtherMove(enemy, partyMembers, moveResult)); // 적의 반응
            }
        }
        saveBattleLogAll(results);
        return results;
    }

    public List<ActorLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        Move ability = moveRepository.findById(moveId).orElseThrow();
        List<ActorLogicResult> results = new ArrayList<>();

        CharacterLogic mainCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        ActorLogicResult mainResult = mainCharacterLogic.processAbility(mainActor, enemy, partyMembers, ability.getType());
        results.add(mainResult);

        // 적의 반응
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        results.addAll(enemyLogic.onOtherMove(enemy, partyMembers, mainResult)); // 적의 반응

        // 후행동 있음
        if (mainResult.hasNextMove()) {
            // 후행동 할 액터 설정
            List<BattleActor> nextMoveActors = mainResult.getNextMoveTarget() == StatusTargetType.SELF ? List.of(mainActor) : partyMembers;
            MoveType nextMoveType = mainResult.getNextMoveType(); // 후행동 타입
            ActorLogicResult nextMoveResult = null; // 후행동 결과
            CharacterLogic nextCharacterLogic = null; // 후행동 로직
            // 후행동
            for (BattleActor nextMoveActor : nextMoveActors) {
                nextCharacterLogic = characterLogicMap.get(nextMoveActor.getActor().getNameEn() + "Logic");
                do {
                    switch (nextMoveType.getParentType()) {
                        case ATTACK, CHARGE_ATTACK -> nextMoveResult = nextCharacterLogic.processAttack(nextMoveActor, enemy, partyMembers, nextMoveType);
                        case ABILITY, SUPPORT_ABILITY -> nextMoveResult = nextCharacterLogic.processAbility(nextMoveActor, enemy, partyMembers, nextMoveType);
                        default -> throw new IllegalArgumentException("Invalid move type = " + nextMoveType);
                    }
                    results.add(nextMoveResult);
                    nextMoveType = nextMoveResult.getNextMoveType(); // 후행동 타입 갱신

                    for (BattleActor partyMember : partyMembers) {
                        // 아군의 반응 (후행 반복 x)
                        CharacterLogic partyMemberLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
                        results.add(partyMemberLogic.postProcessToPartyMove(partyMember, enemy, partyMembers, nextMoveResult));
                    }
                    results.addAll(enemyLogic.onOtherMove(enemy, partyMembers, nextMoveResult)); // 적의 반응

                } while (nextMoveResult.hasNextMove() && nextMoveResult.getNextMoveTarget() == StatusTargetType.SELF);
                // '후행동의 후행동' 의 경우, 행동할 액터가 PARTY_MEMBERS 가 될 수 없다.
                // 어빌발동 -> 후행동(전체) -> 1번 캐릭터 후행동 -> 1번캐릭터의 '후행동의 후행동'(자신) -> 2번캐릭터 후행동 -> ...
                // 이는 특정 액터의 무한행동을 유도할수 있다. 원본 게임에서도 해당 조건은 현재까지 존재하지 않는다.
            }
        }
        saveBattleLogAll(results);
        return results;
    }

    public List<ActorLogicResult> processEnemyAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = null;

        // 다음 스탠바이가 있으면 특수기
        MoveType moveType = enemy.getNextStandbyType() != null ? MoveType.CHARGE_ATTACK : MoveType.ATTACK;
        EnemyLogic enemyLogic = enemyLogicMap.get(mainActor.getActor().getNameEn() + "Logic");

        ActorLogicResult moveResult = ActorLogicResult.builder().build();
        do {
            switch (moveType) {
                case ATTACK -> moveResult = enemyLogic.attack(mainActor, partyMembers);
                case CHARGE_ATTACK ->
                        moveResult = enemyLogic.chargeAttack(mainActor, partyMembers); // 재행동류, 오의재발동 버프가 있을시 후행동으로 오의가 발동할 수 있다.
                case FIRST_SUPPORT_ABILITY -> moveResult = enemyLogic.firstSupportAbility(mainActor, partyMembers);
                case SECOND_SUPPORT_ABILITY -> moveResult = enemyLogic.secondSupportAbility(mainActor, partyMembers);
                case THIRD_SUPPORT_ABILITY -> moveResult = enemyLogic.thirdSupportAbility(mainActor, partyMembers);
                // TODO STANDBY 추가?
                default -> throw new IllegalArgumentException("Invalid move type = " + moveType);
            }
            results.add(moveResult);
            saveBattleLog(moveResult);
            moveType = moveResult.getNextMove(); // 내부에서 공격 이후 후행동이 발생할 경우 후행동의 moveType 으로 변경

            // 적의 공격에 대한 아군의 반응
            partyMembers.stream()
                    .map(partyMember -> {
                        CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
                        return characterLogic.postProcessEnemyMove(partyMember, enemy, partyMembers);
                    })
                    .filter(Objects::nonNull)
                    .forEach(this::saveBattleLog);

        } while (moveResult.hasNextMove());

        // turn end
//        ActorLogicResult turnEndEnemyResult = enemyLogic.onTurnEnd(mainActor, partyMembers);
//        results.add(turnEndEnemyResult);
//        saveBattleLog(turnEndEnemyResult);
        return results;
    }

    public List<ActorLogicResult> processSummon(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        CharacterLogic mainCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = summonLogic.processSummon(mainActor, enemy, partyMembers, move);

        saveBattleLog(result);
        results.add(result);

        // 적의 반응
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyReactResults = enemyLogic.onOtherMove(enemy, partyMembers, result);
        if (!enemyReactResults.isEmpty()) {
            results.addAll(enemyReactResults);
            enemyReactResults.forEach(this::saveBattleLog);
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

    public void saveBattleLogAll(List<ActorLogicResult> results) {
        results.forEach(this::saveBattleLog);
    }

    public void saveBattleLog(ActorLogicResult logicResult) {
        if (logicResult.getMoveType().isNone()) return;
        // 현재 Status 및 관련사항은 mainActor 것만 저장함
        BattleActor mainActor = battleActorRepository.findById(logicResult.getMainBattleActorId()).orElseThrow();
        List<Integer> damages = logicResult.getDamages();
        List<String> damageElementTypes = logicResult.getDamageElementTypes().stream()
                .map(ElementType::name)
                .toList();
        Integer[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                .map(additionalDamage -> additionalDamage.toArray(Integer[]::new))
                .toArray(Integer[][]::new);
        // 스테이터스 비엇는지 확인 ( {{}} )
        boolean isResultStatusEmpty = logicResult.getAddedBattleStatusesList().stream().mapToInt(List::size).sum() == 0;
        log.info("size = {}", logicResult.getAddedBattleStatusesList().size());
        logicResult.getAddedBattleStatusesList().forEach(
                list -> log.info("statuslist = {}, size = {}", list, list.size())
        );
        log.info("empty = {}", isResultStatusEmpty);
        List<BattleStatus> battleStatuses = !isResultStatusEmpty ? logicResult.getAddedBattleStatusesList().get(mainActor.getCurrentOrder()) : Collections.emptyList();
        List<String> statusTypes = battleStatuses.stream()
                .map(battleStatus -> battleStatus.getStatus().getType().name())
                .toList();
        List<String> statusEffectTypes = battleStatuses.stream()
                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().keySet().stream()
                        .map(Enum::name).toList())
                .flatMap(List::stream)
                .toList();

        battleLogRepository.save(
                BattleLog.builder()
                        .roomId(mainActor.getMember().getRoom().getId())
                        .userId(mainActor.getMember().getUser().getId())
                        .moveType(logicResult.getMoveType())
                        .mainActorId(mainActor.getId())
                        .hitCount(logicResult.getTotalHitCount())
                        .damages(damages)
                        .damageElementTypes(damageElementTypes)
                        .additionalDamages(additionalDamages)
                        .statusTypes(statusTypes)
                        .statusEffectTypes(statusEffectTypes)
                        .build());

    }

}
