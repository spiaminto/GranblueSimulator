package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.enemy.DefaultEnemyMoveLogic;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerPhase;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ReactionLogic {

    private final BattleContext battleContext;
    private final BattleLogService battleLogService;
    private final DefaultCharacterMoveLogic characterMoveLogic;
    private final DefaultEnemyMoveLogic defaultEnemyMoveLogic;
    private final TrackingConditionLogic trackingConditionLogic;

    /**
     * 반응 처리<br>
     * 결과에 emptyResult 를 포함하지 않음.
     */
    public List<MoveLogicResult> processReaction(MoveLogicResult firstResult) {
        if (firstResult.isEmpty()) return Collections.emptyList();

        List<MoveLogicResult> allResults = new ArrayList<>();

        MoveType firstMoveType = firstResult.getMove().getType();
        boolean processReaction =
                firstMoveType != MoveType.GUARD_DEFAULT
                        && firstMoveType != MoveType.STRIKE_SEALED;
        if (processReaction) {
            allResults.addAll(process(firstResult, 0));
        } else {
            allResults.add(firstResult);
            battleLogService.saveBattleLog(firstResult);
        }

        return allResults;
    }

    /**
     * baseResult 에 따른 반응을 '우선순위 기반 DFS' 처리. <br>
     * 같은 depth 에서, phase 별로 우선순위 정렬 후 처리시작, 반응이 발생할시 즉시 depth 를 올려 재귀처리 <br>
     *
     * @param baseResult 이전 depth 에서의 결과
     * @param depth      현재 depth
     * @return 현재 depth 결과 + 현재 depth 에서 파생된 다음 depth 결과들
     */
    private List<MoveLogicResult> process(MoveLogicResult baseResult, int depth) {
        if (depth > 10) throw new IllegalStateException("[processReaction] 재귀 깊이 초과");

        List<MoveLogicResult> results = new ArrayList<>();
        results.add(baseResult);
        battleLogService.saveBattleLog(baseResult);

        trackingConditionLogic.updateCommonConditions(baseResult); // Move.conditionTracker 공통 condition 갱신

        List<Actor> currentFieldActors = battleContext.getCurrentFieldActors();

        // 0. 사망처리
        if (battleContext.getEnemy().isAlreadyDead()) return results; // 적이 죽었을시 즉시 종료
        for (Actor actor : currentFieldActors) {
            if (actor.isNowDead()) {
                MoveLogicResult deadResult = actor.isEnemy()
                        ? defaultEnemyMoveLogic.processDead(actor)
                        : characterMoveLogic.processDead(actor);
                if (deadResult != null) { // 캐릭터의 경우 불사효과 등으로 버틸시 null 반환 가능
                    if (actor.isEnemy()) return results; // 적 사망시 후처리 없이 즉시 종료
                    else {
                        results.addAll(process(deadResult, depth + 1)); // 캐릭터가 죽은경우 반응추가    
                    }
                }
            }
        }

        // 1. 트리거 타입 결정
        List<TriggerType> triggers = List.of(
                TriggerType.REACT_SELF, TriggerType.REACT_ENEMY, TriggerType.REACT_CHARACTER
        ); // 반응 순서 고정

        // 2. 트리거 타입에 따른 반응 수집 및 정렬
        List<Reaction> reactions = new ArrayList<>();
        for (TriggerType trigger : triggers) { // SELF -> ENEMY -> CHARACTER
            for (Actor actor : currentFieldActors) { // order by Actor.currentOrder
                if (trigger == TriggerType.REACT_SELF && !baseResult.isFromActor(actor)) continue; // 자신의 행동반응
                if (trigger == TriggerType.REACT_ENEMY && !baseResult.getMainActor().isEnemy()) continue;
                if (trigger == TriggerType.REACT_CHARACTER && baseResult.getMainActor().isEnemy()) continue;

                actor.getMoves(trigger).forEach(move -> {
                    TriggerPhase phase = move.getBaseMove().getTriggerPhase();
                    if (move.getBaseMove().getId().equals(baseResult.getMove().getBaseMove().getId()) || phase.isNone())
                        return; // 동일 행동이 트리거 되거나, 페이즈정보가 없는경우 패스

                    reactions.add(new Reaction(actor, move, trigger, phase));
                });
            }
        }
        // 현재 depth 에서, 반응 순서 우선 -> 페이즈 정렬
        reactions.sort(Comparator.comparing((Reaction reaction) -> reaction.getTriggerType().getReactionOrder()).thenComparing(Reaction::getPhase));

        // 디버깅
        log.info("\n [ReactionLogic.process] depth = {} \n baseResult = {} \n Reactions = {}", depth, baseResult.getMainActor().getName() + " : " + baseResult.getMove().getBaseMove().getName() + " Move.type: " + baseResult.getMove().getType(), "\n   " + reactions.stream().map(Reaction::toString).collect(Collectors.joining("\n   ")));

        // 3. 반응 실행 및 재귀
        for (Reaction reaction : reactions) {
            Actor reactionActor = reaction.getActor();
            if (reactionActor.isAlreadyDead()) continue; // 반응 처리 도중 이미 사망한경우 반응 스킵 (보험)

            MoveLogicResult reactionResult = reactionActor.isEnemy()
                    ? defaultEnemyMoveLogic.processMove(reaction.getMove(), baseResult)
                    : characterMoveLogic.processMove(reaction.getMove(), baseResult);

            // 반응결과가 있을 경우, 그 결과에 대한 반응 처리 재귀
            if (!reactionResult.isEmpty()) {
                // 재귀 시작
                results.addAll(process(reactionResult, depth + 1));
            }
        }

        log.info("\n [ReactionLogic.process] EXECUTED depth = {} \n baseResult = {} \n results = {}", depth, baseResult.getMainActor().getName() + " : " + baseResult.getMove().getBaseMove().getName() + " Move.type: " + baseResult.getMove().getType(), "\n   " + results.stream().map(result -> result.getMainActor().getName() + ": " + result.getMove().getBaseMove().getName()).collect(Collectors.joining("\n   ")));

        return results;
    }

}
