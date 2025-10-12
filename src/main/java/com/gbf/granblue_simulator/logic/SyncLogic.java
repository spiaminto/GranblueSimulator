package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.actor.battle.BattleContext;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.CalcStatusLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.move.StatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 동기화 클래스
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class SyncLogic {

    private final SetStatusLogic setStatusLogic;
    private final StatusRepository statusRepository;
    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final BattleContext battleContext;
    private final CalcStatusLogic calcStatusLogic;

    // 디스펠: 개인버프 디스펠하면 동기화 x, buff_for_all 디스펠하면 적용 -> 마지막에 적용해도 ok
    // 클올: 이것만큼은 따로 처리해줘야 될듯.... 있던 디버프를 카운트 만큼 없애야하므로

    /**
     * 적의 상태를 동기화
     * 행동 종료후 호출
     *
     * @param referenceMember 동기화 기준 멤버
     */
    // CHECK @Aysnc 로 비동기 처리할까?
//    @Transactional(propagation = Propagation.REQUIRES_NEW) // CHECK 혹시 트랜잭션 풀 갯수에 문제생기면 파사드 서비스 방식으로 변환
    public void syncEnemy(Member referenceMember) {
        log.info("[syncEnemy] referenceMember: {}", referenceMember);
        log.info("room = {}", referenceMember.getRoom());
        log.info("room.members = {}", referenceMember.getRoom().getMembers());
        
        // 1. 동기화 대상 확인
        List<BattleActor> targetEnemies = referenceMember.getRoom().getMembers().stream()
                .filter(member -> !referenceMember.getId().equals(member.getId()))
                .flatMap(member -> member.getBattleActors().stream().filter(BattleActor::isEnemy))
                .toList();
        if (targetEnemies.isEmpty()) return;

        // 2. 동기화 기준
        BattleActor referenceEnemy = referenceMember.getBattleActors().stream().filter(BattleActor::isEnemy).findFirst().orElseThrow(() -> new IllegalArgumentException("[syncEnemy] referenceEnemy null, referenceMember = " + referenceMember));

        // 3. 체력 동기화
        Integer referenceHp = referenceEnemy.getHp();
        targetEnemies.forEach(target -> target.updateHp(referenceHp));

        // 4. 참전자 스테이터스 동기화
        // ref 의 참전자 효과
        Map<Long, BattleStatus> refStatusMap = referenceEnemy.getBattleStatuses().stream()
                .filter(BattleStatus::isForAll)
                .collect(Collectors.toMap(s -> s.getStatus().getId(), Function.identity()));

        targetEnemies.forEach(targetEnemy -> {
            // target 의 참전자 효과
            Map<Long, BattleStatus> targetStatusMap = targetEnemy.getBattleStatuses().stream()
                    .filter(BattleStatus::isForAll)
                    .collect(Collectors.toMap(s -> s.getStatus().getId(), Function.identity()));

            // ref와 target 공통: 레벨 동기화 (ref 쪽으로 맞춤)
            refStatusMap.forEach((statusId, refStatus) ->
                    Optional.ofNullable(targetStatusMap.get(statusId))
                            .ifPresent(targetStatus -> {
                                int levelDiff = refStatus.getLevel() - targetStatus.getLevel();
                                if (levelDiff > 0) setStatusLogic.addBattleStatusLevel(targetEnemy, levelDiff, targetStatus);
                                else if (levelDiff < 0) setStatusLogic.subtractBattleStatusLevel(targetEnemy, -levelDiff, targetStatus);
                            })
            );

            // ref 에만 존재: 추가
            refStatusMap.entrySet().stream()
                    .filter(entry -> !targetStatusMap.containsKey(entry.getKey()))
                    .forEach(entry -> setStatusLogic.addSyncedBattleStatus(targetEnemy, entry.getValue()));

            // target 에만 존재: 삭제
            targetStatusMap.entrySet().stream()
                    .filter(entry -> !refStatusMap.containsKey(entry.getKey()))
                    .forEach(entry -> setStatusLogic.removeBattleStatus(targetEnemy, entry.getValue()));

            // 스테이터스가 서로 우선순위 처리되어 삭제/재적용 될때도 위대로 하면 문제없을듯.
            
            // 스텟 재계산
            calcStatusLogic.syncStatus(targetEnemy);
        });
        
    }

    /**
     * 프론트의 동기화 요청을 처리 <br>
     * 1. 일반 동기화 (스테이터스, 체력 등) <br>
     * 2. 대기중인 참전자 버프 처리 <br>
     *
     * @param currentMember
     * @return
     */
    public ActorLogicResult processSyncRequest(Member currentMember) {
        log.info("[processSyncRequest] currentMember: {}", currentMember);
        BattleActor leaderCharacter = battleContext.getLeaderCharacter();
        BattleActor enemy = battleContext.getEnemy();
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();

        // 1. 아군 동기화
        // 참전자 버프 적용
        SetStatusResult setStatusResult = null;
        List<Long> forAllStatusIds = currentMember.getForAllStatusIds();
        if (!forAllStatusIds.isEmpty()) {
            List<Status> waitingForAllStatuses = statusRepository.findAllById(forAllStatusIds);
            setStatusResult = setStatusLogic.setStatus(leaderCharacter, enemy, partyMembers, waitingForAllStatuses, StatusTargetType.PARTY_MEMBERS);
            currentMember.clearForAllStatusIds();
        }

        // 2. 적 동기화
        // 각멤버가 자신의 행동 후 적을 동기화 하므로, 여기서 더 할건 없음

        ActorLogicResult syncResult = characterLogicResultMapper.toResult(leaderCharacter, enemy, partyMembers, Move.getTransientMove(MoveType.SYNC), null, setStatusResult);
        return syncResult;
    }


}
