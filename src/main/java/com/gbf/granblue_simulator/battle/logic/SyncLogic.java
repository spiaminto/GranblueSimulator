package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.enemy.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusResult;
import com.gbf.granblue_simulator.metadata.repository.BaseStatusEffectRepository;
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
    private final EnemyLogicResultMapper enemyLogicResultMapper;

    /**
     * 요청자의 현재 최신 상태를 반환, 동기화 자체는 '이전 참전자' 가 이미 동기화 해놓음. <br>
     * 1. 일반 동기화: requestSync 를 통해 직접 요청이 오는경우 :
     * 2. 커맨드 동기화: 각 커맨드 실행전 내부에서 동기화 하는 경우
     *
     * @return ActorLogicResult syncResult, mainActor = enemy
     */
    public ActorLogicResult processSync() {
        log.info("[processSync] battleContext.print()");
        battleContext.print();

        Member currentMember = battleContext.getMember();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = battleContext.getFrontCharacters();

        // mainActor 의 경우
        // 1. 일반 동기화: requestSync 를 통해 직접 요청이 오는경우 : 첫번째 아군, 아군 전원 사망시 적
        // 2. 커맨드 동기화: 각 커맨드 실행전 내부에서 동기화 하는 경우 : requestMainActor

        // 적이 죽으면, 즉시 사망결과 반환
        if (enemy.isAlreadyDead()) return enemyLogicResultMapper.toResult(enemy.getMove(MoveType.DEAD_DEFAULT));

        // 적 추가 처리
        // 1. 종료된 시간제 스테이터스 효과 직접 삭제
        setStatusLogic.removeExpiredTimeBasedStatusEffects(enemy, partyMembers);

        // 아군 추가처리
        // 1. 등록된 참전자 버프 적용
        SetStatusResult setStatusResult = null;
        List<Long> forAllStatusIds = currentMember.getForAllStatusIds();
        if (!forAllStatusIds.isEmpty() && !partyMembers.isEmpty()) {
            List<BaseStatusEffect> waitingForAllBaseStatusEffects = baseStatusEffectRepository.findAllById(forAllStatusIds);
            setStatusResult = setStatusLogic.setStatusEffect(partyMembers.getFirst(), waitingForAllBaseStatusEffects, StatusEffectTargetType.PARTY_MEMBERS);
            currentMember.clearForAllStatusIds();
        }

        // 최신 상태 반환
        ActorLogicResult syncResult = characterLogicResultMapper.toResult(Move.getTransientMove(MoveType.SYNC), null, setStatusResult);
        return syncResult;
    }

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
        targetEnemies.forEach(target -> target.updateHp(referenceHp)); // 사망시 Integer.MIN 으로 업데이트 가능
        
        // 3.1 적 사망시 처리
        if (referenceEnemy.isAlreadyDead()) {
            this.finishBattle();
        }

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
                                if (levelDiff > 0)
                                    setStatusLogic.addStatusEffectsLevel(targetEnemy, levelDiff, targetStatus);
                                else if (levelDiff < 0)
                                    setStatusLogic.subtractStatusEffectLevel(targetEnemy, -levelDiff, targetStatus);
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

            // 시간제 효과 종료의 경우 개인이 행동 또는 동기화시 직접 삭제하는게 나을듯

            // 스텟 재계산
            targetEnemy.getStatus().syncStatus();
        });

    }

    protected void finishBattle() {
        Member member = battleContext.getMember();
        Room room = member.getRoom();
        List<Member> members = room.getMembers();

        // 방 상태 변경
        room.updateEndedAtNow();
        room.updateIsHidden(true);

    }


}
