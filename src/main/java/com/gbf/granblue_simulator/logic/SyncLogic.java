package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.domain.battle.BattleContext;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.move.BaseStatusEffectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
    private final BaseStatusEffectRepository baseStatusEffectRepository;
    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final BattleContext battleContext;

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
        List<Actor> targetEnemies = referenceMember.getRoom().getMembers().stream()
                .filter(member -> !referenceMember.getId().equals(member.getId()))
                .flatMap(member -> member.getActors().stream().filter(Actor::isEnemy))
                .toList();

        // 2. 동기화 기준
        Actor referenceEnemy = referenceMember.getActors().stream().filter(Actor::isEnemy).findFirst().orElseThrow(() -> new IllegalArgumentException("[syncEnemy] referenceEnemy null, referenceMember = " + referenceMember));

        // 3. 체력 동기화
        Integer referenceHp = referenceEnemy.getHp();
        targetEnemies.forEach(target -> target.updateHp(referenceHp));

        // 4. 참전자 스테이터스 동기화
        if (targetEnemies.isEmpty()) return; // 여기부턴 연산이 무거워 지므로 없으면 반환

        // ref 의 참전자 효과
        Map<Long, StatusEffect> refStatusMap = referenceEnemy.getStatusEffects().stream()
                .filter(StatusEffect::isForAll)
                .collect(Collectors.toMap(s -> s.getBaseStatusEffect().getId(), Function.identity()));

        targetEnemies.forEach(targetEnemy -> {
            // target 의 참전자 효과
            Map<Long, StatusEffect> targetStatusMap = targetEnemy.getStatusEffects().stream()
                    .filter(StatusEffect::isForAll)
                    .collect(Collectors.toMap(s -> s.getBaseStatusEffect().getId(), Function.identity()));

            // ref와 target 공통: 레벨 동기화 (ref 쪽으로 맞춤)
            refStatusMap.forEach((statusId, refStatus) ->
                    Optional.ofNullable(targetStatusMap.get(statusId))
                            .ifPresent(targetStatus -> {
                                int levelDiff = refStatus.getLevel() - targetStatus.getLevel();
                                if (levelDiff > 0) setStatusLogic.addStatusEffectLevel(targetEnemy, levelDiff, targetStatus);
                                else if (levelDiff < 0) setStatusLogic.subtractStatusEffectLevel(targetEnemy, -levelDiff, targetStatus);
                            })
            );

            // ref 에만 존재: 추가
            refStatusMap.entrySet().stream()
                    .filter(entry -> !targetStatusMap.containsKey(entry.getKey()))
                    .forEach(entry -> setStatusLogic.addSyncedStatusEffect(targetEnemy, entry.getValue()));

            // target 에만 존재: 삭제 (ref 에서 디스펠 됨)
            targetStatusMap.entrySet().stream()
                    .filter(entry -> !refStatusMap.containsKey(entry.getKey()))
                    .forEach(entry -> setStatusLogic.removeStatusEffect(targetEnemy, entry.getValue()));

            // 스테이터스가 서로 우선순위 처리되어 삭제/재적용 될때도 위대로 하면 문제없을듯.

            // 스텟 재계산
            targetEnemy.getStatus().syncStatus();
        });

    }

    /**
     * 각 커맨드 전, 후에 처리되는 동기화
     * 1. 일반 동기화 (스테이터스, 체력 등) <br>
     * 2. 대기중인 참전자 버프 처리 <br>
     *
     * @param currentMember
     * @return
     */
    public ActorLogicResult processSync(Member currentMember) {
        log.info("[processSyncRequest] currentMember: {}", currentMember);
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = battleContext.getFrontCharacters();

        // 1. 적 동기화
        // 적의 참전자 버프 시간 진행, 결과 반환 x
        setStatusLogic.progressTimeBasedStatusEffects(enemy, partyMembers);

        // 2. 아군 동기화
        // 참전자 버프 적용
        SetStatusResult setStatusResult = null;
        List<Long> forAllStatusIds = currentMember.getForAllStatusIds();
        if (!forAllStatusIds.isEmpty()) {
            List<BaseStatusEffect> waitingForAllBaseStatusEffects = baseStatusEffectRepository.findAllById(forAllStatusIds);
            setStatusResult = setStatusLogic.setStatusEffect(leaderCharacter, enemy, partyMembers, waitingForAllBaseStatusEffects, StatusEffectTargetType.PARTY_MEMBERS);
            currentMember.clearForAllStatusIds();
        }

        ActorLogicResult syncResult = characterLogicResultMapper.toResult(leaderCharacter, enemy, partyMembers, Move.getTransientMove(MoveType.SYNC), null, setStatusResult);
        return syncResult;
    }


}
