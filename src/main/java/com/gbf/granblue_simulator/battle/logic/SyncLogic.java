package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.ForMemberAbilityInfo;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.SetEffectRequest;
import com.gbf.granblue_simulator.battle.logic.move.mapper.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.move.mapper.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private final BattleContext battleContext;
    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final SetStatusLogic setStatusLogic;
    private final BaseMoveRepository baseMoveRepository;

    /**
     * 요청자의 현재 최신 상태를 반환, 동기화 자체는 '이전 참전자' 가 이미 동기화 해놓음. <br>
     * 1. 일반 동기화: requestSync 를 통해 직접 요청이 오는경우 :
     * 2. 커맨드 동기화: 각 커맨드 실행전 내부에서 동기화 하는 경우
     *
     * @return 동기화 결과들 (참전자 버프 행동이 여러개인 경우, 여러개의 결과가 반환) mainActor = enemy
     */
    public List<MoveLogicResult> processSync() {
        Member currentMember = battleContext.getMember();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        List<MoveLogicResult> results = new ArrayList<>();

        // mainActor 의 경우
        // 1. 일반 동기화: requestSync 를 통해 직접 요청이 오는경우 : 첫번째 아군, 아군 전원 사망시 적
        // 2. 커맨드 동기화: 각 커맨드 실행전 내부에서 동기화 하는 경우 : requestMainActor

        // 적이 죽으면, 즉시 사망결과 반환
        if (enemy.isAlreadyDead())
            return List.of(enemyLogicResultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(enemy, MoveType.DEAD_DEFAULT))));

        // 적 추가 처리
        // 1. 종료된 시간제 스테이터스 효과 직접 삭제
        setStatusLogic.removeExpiredTimeBasedStatusEffects(enemy, partyMembers);

        List<Member.PendingForAllMove> pendingForAllMoves = currentMember.getPendingForAllMoves();
        if (pendingForAllMoves.isEmpty()) {
            results.add(characterLogicResultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(battleContext.getMainActor(), MoveType.SYNC))));
        } else {
            // 참전자 버프 포함하는 move 처리
            Actor beforeMainActor = battleContext.getMainActor();
            battleContext.setCurrentMainActor(partyMembers.getFirst()); // 임시로 지정

            pendingForAllMoves.forEach(pendingForAllMove -> {
                BaseMove move = baseMoveRepository.findById(pendingForAllMove.getMoveId()).orElseThrow(() -> new IllegalArgumentException("[processSync] 참전자 버프 move 없음 moveId = " + pendingForAllMove.getMoveId() + " not found"));
                List<BaseStatusEffect> forAllBaseEffects = move.getBaseStatusEffects()
                        .stream()
                        .filter(baseStatusEffect -> baseStatusEffect.getTargetType() == StatusEffectTargetType.ALL_PARTY_MEMBERS)
                        .toList();
                SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.withSelectedTargets(forAllBaseEffects, battleContext.getFrontCharacters()));

                String sourceUsername = pendingForAllMove.getSourceUsername();
                String sourceMoveName = move.getName();

                results.add(characterLogicResultMapper.toResult(ResultMapperRequest.builder()
                        .move(Move.getTransientMove(battleContext.getMainActor(), MoveType.SYNC))
                        .setStatusEffectResult(setStatusEffectResult)
                        .forMemberAbilityInfo(ForMemberAbilityInfo.builder()
                                .moveName(sourceMoveName)
                                .sourceUsername(sourceUsername)
                                .cjsName(move.getDefaultVisual().getCjsName())
                                .isTargetedEnemy(move.getDefaultVisual().isTargetedEnemy())
                                .build())
                        .build()));
            });

            pendingForAllMoves.clear();
            battleContext.setCurrentMainActor(beforeMainActor);
        }

        // 최신 상태 반환
        return results;
    }

    /**
     * 적의 상태를 동기화
     * 행동 종료후 호출
     *
     * @param referenceMember 동기화 기준 멤버
     */
    public void syncEnemy(Member referenceMember) {
        log.info("[syncEnemy] referenceMember: {}", referenceMember);
        log.info("room = {}", referenceMember.getRoom());
        log.info("room.members = {}", referenceMember.getRoom().getMembers());

        // 1. 동기화 대상 확인
        List<Actor> targetEnemies = referenceMember.getRoom().getMembers().stream()
                .filter(member -> !referenceMember.getId().equals(member.getId()))
                .flatMap(member -> member.getActors().stream().filter(Actor::isEnemy))
                .toList();
        if (targetEnemies.isEmpty()) return; // 없으면 바로 종료
//        actorRepository.lockActorsAndStatuses(targetEnemies.stream().map(Actor::getId).sorted().toList()); // 락

        // 2. 동기화 기준
        Actor referenceEnemy = referenceMember.getActors().stream().filter(Actor::isEnemy).findFirst().orElseThrow(() -> new IllegalArgumentException("[syncEnemy] referenceEnemy null, referenceMember = " + referenceMember));

        // 3. 체력 동기화
        int referenceHp = referenceEnemy.getHp();
        targetEnemies.forEach(target -> target.updateHp(referenceHp)); // 사망시 Integer.MIN 으로 업데이트 가능

        // 3.1 적 사망시 처리
        if (referenceEnemy.isAlreadyDead()) {
            this.finishBattle();
        }

        // 4. 참전자 스테이터스 동기화
        // ref 의 참전자 효과
        Map<Long, StatusEffect> refStatusMap = referenceEnemy.getStatusEffects().stream()
                .filter(StatusEffect::isForAllEnemies)
                .collect(Collectors.toMap(s -> s.getBaseStatusEffect().getId(), Function.identity()));

        targetEnemies.forEach(targetEnemy -> {
            // target 의 참전자 효과
            Map<Long, StatusEffect> targetStatusMap = targetEnemy.getStatusEffects().stream()
                    .filter(StatusEffect::isForAllEnemies)
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
        });

    }

    protected void finishBattle() {
        Member member = battleContext.getMember();
        Room room = member.getRoom();

        // 방 상태 변경
        room.updateEndedAtNow();
        room.updateIsHidden(true);
    }


}
