package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogic;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /*
    캐릭터 어빌리티발동
     -> 캐릭터 로직실행 -> 캐릭터 후행동 -> 아군 post process 실행
     -> 적 post process 실행 (여기에 전조 포함) -> 결과반환
     */

    public void startBattle(List<BattleActor> partyMembers, BattleActor enemy) {
        partyMembers.forEach(partyMember -> {
            CharacterLogic characterLogic = characterLogicMap.get(partyMember.getActor().getNameEn() + "Logic");
            characterLogic.onBattleStart(partyMember, enemy, partyMembers);
        });
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        enemyLogic.onBattleStart(enemy, partyMembers);
    }

    public List<ActorLogicResult> process(BattleLogicRequest request) {
        BattleActor mainActor = battleActorRepository.findById(request.getMainActorId()).orElseThrow();
        //TODO 정렬필요
        List<BattleActor> partyMembers = battleActorRepository.findAllById(request.getPartyMemberIds());
        BattleActor enemy = battleActorRepository.findById(request.getEnemyId()).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();
        if (request.getRequestType() == RequestType.ATTACK) {
            results = processAttack(enemy, partyMembers);
        } else if (request.getRequestType() == RequestType.ABILITY) {
            results = processAbility(mainActor, enemy, partyMembers, request.getRequestMoveId());
        }
        return results;
    }

    public List<ActorLogicResult> processAttack(BattleActor enemy, List<BattleActor> partyMembers) {
        List<BattleActor> moveActors = partyMembers;

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = null;

        // moveActor 마다 NORMAL_ATTACK 또는 CHARGE_ATTACK 실행. 그에따른 재행동역시 실행함.
        for (BattleActor moveActor : moveActors) {
            MoveType moveType = moveActor.getChargeGauge() >= moveActor.getMaxChargeGauge() ? MoveType.CHARGE_ATTACK : MoveType.ATTACK;
            CharacterLogic nextCharacterLogic = characterLogicMap.get(moveActor.getActor().getNameEn() + "Logic");
            ActorLogicResult moveResult = ActorLogicResult.builder().build();
            do {
                switch (moveType) {
                    case ATTACK -> moveResult = nextCharacterLogic.attack(moveActor, enemy, partyMembers);
                    case FIRST_ABILITY -> moveResult = nextCharacterLogic.firstAbility(moveActor, enemy, partyMembers);
                    case SECOND_ABILITY ->
                            moveResult = nextCharacterLogic.secondAbility(moveActor, enemy, partyMembers);
                    case THIRD_ABILITY -> moveResult = nextCharacterLogic.thirdAbility(moveActor, enemy, partyMembers);
                    case CHARGE_ATTACK ->
                            moveResult = nextCharacterLogic.chargeAttack(moveActor, enemy, partyMembers); // 재행동류, 오의재발동 버프가 있을시 후행동으로 오의가 발동할 수 있다.
                    case FIRST_SUPPORT_ABILITY ->
                            moveResult = nextCharacterLogic.firstSupportAbility(moveActor, enemy, partyMembers);
                    case SECOND_SUPPORT_ABILITY ->
                            moveResult = nextCharacterLogic.secondSupportAbility(moveActor, enemy, partyMembers);
                    case THIRD_SUPPORT_ABILITY ->
                            moveResult = nextCharacterLogic.thirdSupportAbility(moveActor, enemy, partyMembers);
                    default -> throw new IllegalArgumentException("Invalid move type = " + moveType);
                }
                results.add(moveResult);
                saveBattleLog(moveResult);

                // 적의 반응
                EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
                List<ActorLogicResult> enemyReactResults = enemyLogic.onOtherMove(enemy, partyMembers, moveResult);
                if (!enemyReactResults.isEmpty()) {
                    results.addAll(enemyReactResults);
                    enemyReactResults.forEach(this::saveBattleLog);
                }

                moveType = moveResult.getNextMoveType(); // 내부에서 공격 이후 후행동이 발생할 경우 후행동의 moveType 으로 변경
            } while (moveResult.hasNextMove());
        }
        return results;
    }

    public List<ActorLogicResult> processEnemyAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = null;

        // moveActor 마다 NORMAL_ATTACK 또는 CHARGE_ATTACK 실행. 그에따른 재행동역시 실행함.
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
            moveType = moveResult.getNextMoveType(); // 내부에서 공격 이후 후행동이 발생할 경우 후행동의 moveType 으로 변경
        } while (moveResult.hasNextMove());

        // turn end
        ActorLogicResult turnEndEnemyResult = enemyLogic.onTurnEnd(mainActor, partyMembers);
        results.add(turnEndEnemyResult);
        saveBattleLog(turnEndEnemyResult);
        return results;
    }

    public List<ActorLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        CharacterLogic mainCharacterLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();

        ActorLogicResult result = null;
        switch (move.getType()) {
            case FIRST_ABILITY -> result = mainCharacterLogic.firstAbility(mainActor, enemy, partyMembers);
            case SECOND_ABILITY -> result = mainCharacterLogic.secondAbility(mainActor, enemy, partyMembers);
            case THIRD_ABILITY -> result = mainCharacterLogic.thirdAbility(mainActor, enemy, partyMembers);
            case FIRST_SUPPORT_ABILITY ->
                    result = mainCharacterLogic.firstSupportAbility(mainActor, enemy, partyMembers);
            case SECOND_SUPPORT_ABILITY ->
                    result = mainCharacterLogic.secondSupportAbility(mainActor, enemy, partyMembers);
            case THIRD_SUPPORT_ABILITY ->
                    result = mainCharacterLogic.thirdSupportAbility(mainActor, enemy, partyMembers);
            default -> throw new IllegalArgumentException("Invalid move type = " + move.getType());
        }
        saveBattleLog(result);
        results.add(result);

        // 적의 반응
        EnemyLogic enemyLogic = enemyLogicMap.get(enemy.getActor().getNameEn() + "Logic");
        List<ActorLogicResult> enemyReactResults = enemyLogic.onOtherMove(enemy, partyMembers, result);
        if (!enemyReactResults.isEmpty()) {
            results.addAll(enemyReactResults);
            enemyReactResults.forEach(this::saveBattleLog);
        }

        // 후행동 있음
        if (result != null && result.hasNextMove()) {
            // 후행동 할 액터 설정
            List<BattleActor> nextMoveActors = new ArrayList<>();
            if (result.getNextMoveTarget() == StatusTargetType.SELF) {
                nextMoveActors.add(mainActor);
            } else if (result.getNextMoveTarget() == StatusTargetType.PARTY_MEMBERS) {
                nextMoveActors = partyMembers;
            }
            // 후행동
            for (BattleActor nextMoveActor : nextMoveActors) {
                MoveType nextMoveType = result.getNextMoveType(); // 후행동의 타입 ('후행동의 후행동' 이후 다시 후행동 개시시 초기화)
                CharacterLogic nextCharacterLogic = characterLogicMap.get(nextMoveActor.getActor().getNameEn() + "Logic"); // 후행동 로직
                ActorLogicResult nextMoveResult = ActorLogicResult.builder().build(); // 후행동 결과저장
                do { // 후행동의 후행동 처리를 위해 반복 
                    switch (nextMoveType) {
                        case ATTACK ->
                                nextMoveResult = nextCharacterLogic.attack(nextMoveActor, enemy, partyMembers);
                        case FIRST_ABILITY ->
                                nextMoveResult = nextCharacterLogic.firstAbility(nextMoveActor, enemy, partyMembers);
                        case SECOND_ABILITY ->
                                nextMoveResult = nextCharacterLogic.secondAbility(nextMoveActor, enemy, partyMembers);
                        case THIRD_ABILITY ->
                                nextMoveResult = nextCharacterLogic.thirdAbility(nextMoveActor, enemy, partyMembers);
                        case FIRST_SUPPORT_ABILITY ->
                                nextMoveResult = nextCharacterLogic.firstSupportAbility(nextMoveActor, enemy, partyMembers);
                        case SECOND_SUPPORT_ABILITY ->
                                nextMoveResult = nextCharacterLogic.secondSupportAbility(nextMoveActor, enemy, partyMembers);
                        case THIRD_SUPPORT_ABILITY ->
                                nextMoveResult = nextCharacterLogic.thirdSupportAbility(nextMoveActor, enemy, partyMembers);
                        // 어빌리티 후행동에 오의는 없다.
                        default -> throw new IllegalArgumentException("Invalid move type = " + nextMoveType);
                    }
                    results.add(nextMoveResult);
                    saveBattleLog(nextMoveResult);

                    // 후행동에 대한 적의 반응
                    List<ActorLogicResult> nextMoveEnemyReactResults = enemyLogic.onOtherMove(enemy, partyMembers, nextMoveResult);
                    if (!nextMoveEnemyReactResults.isEmpty()) {
                        results.addAll(nextMoveEnemyReactResults);
                        nextMoveEnemyReactResults.forEach(this::saveBattleLog);
                    }

                    nextMoveType = nextMoveResult.getNextMoveType(); // 후행동의 후행동 타입을 받아와 다음 switch 조건 처리
                } while (nextMoveResult.hasNextMove() && result.getNextMoveTarget() == StatusTargetType.SELF);
                // '후행동의 후행동' 의 경우, 행동할 액터가 PARTY_MEMBERS 가 될 수 없다.
                // 어빌발동 -> 후행동(전체) -> 1번 캐릭터 후행동 -> 1번캐릭터의 '후행동의 후행동'(자신) -> 2번캐릭터 후행동 -> ...
                // 이는 특정 액터의 무한행동을 유도할수 있다. 원본 게임에서도 해당 조건은 현재까지 존재하지 않는다.
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

    public void saveBattleLog(ActorLogicResult logicResult) {
        if (logicResult == null) return;
        // 현재 Status 및 관련사항은 mainActor 것만 저장함
        BattleActor mainActor = battleActorRepository.findById(logicResult.getMainBattleActorId()).orElseThrow();
        List<Integer> damages = logicResult.getDamages();
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
                        .additionalDamages(additionalDamages)
                        .statusTypes(statusTypes)
                        .statusEffectTypes(statusEffectTypes)
                        .build());

    }

}
