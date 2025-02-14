package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.ActorLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleLogic {

    private final Map<String, ActorLogic> characterLogicMap;
    private final BattleActorRepository battleActorRepository;
    private final MoveRepository moveRepository;
    private final BattleLogRepository battleLogRepository;

    /*
    캐릭터 어빌리티발동
     -> 캐릭터 로직실행 -> 캐릭터 후행동 -> 아군 post process 실행
     -> 적 post process 실행 (여기에 전조 포함) -> 결과반환
     */

    public void process(BattleLogicRequest request) {
        BattleActor mainActor = battleActorRepository.findById(request.getMainActorId()).orElseThrow();
        //TODO 정렬필요
        List<BattleActor> partyMembers = battleActorRepository.findAllById(request.getPartyMemberIds());
        BattleActor enemy = battleActorRepository.findById(request.getEnemyId()).orElseThrow();

        if (request.getRequestType() == RequestType.ATTACK) {
            processAttack(enemy, partyMembers);
        } else if (request.getRequestType() == RequestType.ABILITY) {
            processAbility(mainActor, enemy, partyMembers, request.getRequestMoveId());
        }

    }

    public void saveBattleLog(List<ActorLogicResult> logicResults) {
        List<BattleLog> battleLogs = logicResults.stream().map(
                logicResult -> {
                    List<Integer> damages = logicResult.getDamages();
                    Integer[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                                .map(additionalDamage -> additionalDamage.toArray(Integer[]::new))
                                .toArray(Integer[][]::new);
                    List<Status> statuses = logicResult.getStatusList();
                    List<String> statusTypes = statuses.stream()
                            .map(status -> status.getType().name())
                            .toList();
                    List<String> statusEffectTypes = statuses.stream()
                            .map(status -> status.getStatusEffects().stream()
                                    .map(statusEffect -> statusEffect.getType().name()).toList())
                            .flatMap(List::stream)
                            .toList();
                    BattleActor mainActor = battleActorRepository.findById(logicResult.getMainBattleActorId()).orElseThrow();

                    return BattleLog.builder()
                            .roomId(mainActor.getMember().getRoom().getId())
                            .userId(mainActor.getMember().getUser().getId())
                            .moveType(logicResult.getMoveType())
                            .mainActorId(mainActor.getId())
                            .hitCount(logicResult.getTotalHitCount())
                            .damages(damages)
                            .additionalDamages(additionalDamages)
                            .statusTypes(statusTypes)
                            .statusEffectTypes(statusEffectTypes)
                            .build();
                }).toList();

        battleLogRepository.saveAll(battleLogs);
    }

    public List<ActorLogicResult> processAttack(BattleActor enemy, List<BattleActor> partyMembers) {
        List<BattleActor> moveActors = partyMembers;

        List<ActorLogicResult> results = new ArrayList<>();
        ActorLogicResult result = null;

        // moveActor 마다 NORMAL_ATTACK 또는 CHARGE_ATTACK 실행. 그에따른 재행동역시 실행함.
        for (BattleActor moveActor : moveActors) {
            MoveType moveType = moveActor.getChargeGauge() >= 100 ? MoveType.CHARGE_ATTACK : MoveType.NORMAL_ATTACK;
            ActorLogic nextActorLogic = characterLogicMap.get(moveActor.getActor().getNameEn() + "Logic");
            ActorLogicResult moveResult = ActorLogicResult.builder().build();
            do {
                switch (moveType) {
                    case NORMAL_ATTACK -> moveResult = nextActorLogic.attack(moveActor, enemy, partyMembers);
                    case FIRST_ABILITY -> moveResult = nextActorLogic.firstAbility(moveActor, enemy, partyMembers);
                    case SECOND_ABILITY -> moveResult = nextActorLogic.secondAbility(moveActor, enemy, partyMembers);
                    case THIRD_ABILITY -> moveResult = nextActorLogic.thirdAbility(moveActor, enemy, partyMembers);
                    case CHARGE_ATTACK ->
                            moveResult = nextActorLogic.chargeAttack(moveActor, enemy, partyMembers); // 재행동류, 오의재발동 버프가 있을시 후행동으로 오의가 발동할 수 있다.
                    case FIRST_SUPPORT_ABILITY -> moveResult = nextActorLogic.firstSupportAbility(moveActor, enemy, partyMembers);
                    case SECOND_SUPPORT_ABILITY -> moveResult = nextActorLogic.secondSupportAbility(moveActor, enemy, partyMembers);
                    case THIRD_SUPPORT_ABILITY -> moveResult = nextActorLogic.thirdSupportAbility(moveActor, enemy, partyMembers);
                    default -> throw new IllegalArgumentException("Invalid move type = " + moveType);
                }
                results.add(moveResult);
                moveType = moveResult.getNextMoveType(); // 내부에서 공격 이후 후행동이 발생할 경우 후행동의 moveType 으로 변경
            } while (moveResult.hasNextMove());
        }

        saveBattleLog(results);
        return results;
    }

    public List<ActorLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        ActorLogic mainActorLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<ActorLogicResult> results = new ArrayList<>();

        ActorLogicResult result = null;
        switch (move.getType()) {
            case FIRST_ABILITY -> result = mainActorLogic.firstAbility(mainActor, enemy, partyMembers);
            case SECOND_ABILITY -> result = mainActorLogic.secondAbility(mainActor, enemy, partyMembers);
            case THIRD_ABILITY -> result = mainActorLogic.thirdAbility(mainActor, enemy, partyMembers);
            case FIRST_SUPPORT_ABILITY -> result = mainActorLogic.firstSupportAbility(mainActor, enemy, partyMembers);
            case SECOND_SUPPORT_ABILITY -> result = mainActorLogic.secondSupportAbility(mainActor, enemy, partyMembers);
            case THIRD_SUPPORT_ABILITY -> result = mainActorLogic.thirdSupportAbility(mainActor, enemy, partyMembers);
            default -> throw new IllegalArgumentException("Invalid move type = " + move.getType());
        }
        results.add(result);

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
                ActorLogic nextActorLogic = characterLogicMap.get(nextMoveActor.getActor().getNameEn() + "Logic"); // 후행동 로직
                ActorLogicResult nextMoveResult = ActorLogicResult.builder().build(); // 후행동 결과저장
                do { // 후행동의 후행동 처리를 위해 반복 
                    switch (nextMoveType) {
                        case NORMAL_ATTACK ->
                                nextMoveResult = nextActorLogic.attack(nextMoveActor, enemy, partyMembers);
                        case FIRST_ABILITY ->
                                nextMoveResult = nextActorLogic.firstAbility(nextMoveActor, enemy, partyMembers);
                        case SECOND_ABILITY ->
                                nextMoveResult = nextActorLogic.secondAbility(nextMoveActor, enemy, partyMembers);
                        case THIRD_ABILITY ->
                                nextMoveResult = nextActorLogic.thirdAbility(nextMoveActor, enemy, partyMembers);
                        case FIRST_SUPPORT_ABILITY ->
                                nextMoveResult = nextActorLogic.firstSupportAbility(nextMoveActor, enemy, partyMembers);
                        case SECOND_SUPPORT_ABILITY ->
                                nextMoveResult = nextActorLogic.secondSupportAbility(nextMoveActor, enemy, partyMembers);
                        case THIRD_SUPPORT_ABILITY ->
                                nextMoveResult = nextActorLogic.thirdSupportAbility(nextMoveActor, enemy, partyMembers);
                        // 어빌리티 후행동에 오의는 없다.
                        default -> throw new IllegalArgumentException("Invalid move type = " + nextMoveType);
                    }
                    results.add(nextMoveResult);
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

}
