package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.character.CharacterLogic;
import com.gbf.granblue_simulator.logic.character.dto.CharacterLogicResult;
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

    private final Map<String, CharacterLogic> characterLogicMap;
    private final BattleActorRepository battleActorRepository;
    private final MoveRepository moveRepository;

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

    public List<CharacterLogicResult> processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Long moveId) {
        CharacterLogic mainActorLogic = characterLogicMap.get(mainActor.getActor().getNameEn() + "Logic");
        Move move = moveRepository.findById(moveId).orElseThrow();

        List<CharacterLogicResult> results = new ArrayList<>();

        CharacterLogicResult result = null;
        switch (move.getType()) {
            case FIRST_ABILITY -> result = mainActorLogic.firstAbility(mainActor, enemy, partyMembers);
            case SECOND_ABILITY -> result = mainActorLogic.secondAbility(mainActor, enemy, partyMembers);
            case THIRD_ABILITY -> result = mainActorLogic.thirdAbility(mainActor, enemy, partyMembers);
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
                CharacterLogic nextActorLogic = characterLogicMap.get(nextMoveActor.getActor().getNameEn() + "Logic"); // 후행동 로직
                CharacterLogicResult nextMoveResult = CharacterLogicResult.builder().build(); // 후행동 결과저장
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
                        // 어빌리티 후행동에 오의는 없다.
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


    private void processAttack(BattleActor enemy, List<BattleActor> partyMembers) {

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
        ABILITY, ATTACK
    }

}
