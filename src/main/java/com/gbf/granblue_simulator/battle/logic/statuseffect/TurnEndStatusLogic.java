package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.enemy.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectsByModifierTypes;

@Component
@Slf4j
@RequiredArgsConstructor
public class TurnEndStatusLogic {

    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ProcessStatusLogic processStatusLogic;
    private final BattleContext battleContext; // 특히, 아군의 턴데미지 적의 턴데미지 때 mainActor 설정이 필요하여 사용
    private final StatusEffectRepository statusEffectRepository;

    /**
     * 턴 종료 시 상태효과 처리
     * 되도록 최후에 처리
     *
     * @param turnEndProcessStartTime 턴 종료 처리 시작시간 (턴 종료시 생성/갱신된 상태효과의 경우 duration 진행 안함)
     * @return
     */
    public ActorLogicResult progressStatusEffect(LocalDateTime turnEndProcessStartTime) {
        List<Actor> allActors = battleContext.getCurrentFieldActors();

        // StatusEffect 남은시간 1턴 감소
        allActors.stream()
                .map(Actor::getStatusEffects)
                .flatMap(Collection::stream)
                .forEach(statusEffect -> {
                    if (statusEffect.getBaseStatusEffect().getDurationType().isTurnBased() // 턴제
                            && !(statusEffect.getBaseStatusEffect().getDurationType() == StatusDurationType.TURN_INFINITE) // 영속아님
                            && statusEffect.getUpdatedAt().isBefore(turnEndProcessStartTime)) // 턴종료 처리 이전 갱신된 상태효과
                        statusEffect.subtractDuration(1);
                });

        // 남은시간 0 턴인 상태효과
        Map<Actor, List<StatusEffect>> expiredEffectsMap = allActors.stream()
                .collect(Collectors.toMap(
                        actor -> actor,
                        actor -> actor.getStatusEffects().stream()
                                .filter(effect -> effect.getDuration() == 0)
                                .collect(Collectors.toList())
                ));

        // 삭제
        expiredEffectsMap.forEach((actor, expiredStatusEffects) -> {
            if (!expiredStatusEffects.isEmpty()) {
                expiredStatusEffects.forEach(effect -> log.info("[progressStatusEffects] expired: actor={}, status={}", actor.getName(), effect.getBaseStatusEffect().getName()));
                actor.getStatusEffects().removeAll(expiredStatusEffects);
                statusEffectRepository.deleteAllInBatch(expiredStatusEffects);
            }
        });

        // 스테이터스 갱신
        allActors.forEach(actor -> actor.getStatus().syncStatus());

        return enemyLogicResultMapper.toResult(Move.getTransientMove(MoveType.TURN_FINISH));
    }

    /**
     * 턴 종료시 스테이터스 효과 처리
     *
     * @param enemy
     * @param partyMembers
     * @return MoveType.NONE 결과를 리턴하지 않음
     */
    public List<ActorLogicResult> processTurnEnd(Actor enemy, List<Actor> partyMembers) {
        // 아군 전원 사망시 처리 스킵
        if (partyMembers.isEmpty()) return List.of();

        List<ActorLogicResult> results = new ArrayList<>();
        Actor partyMainActor = partyMembers.getFirst();

        // 아군 턴종 힐 처리
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(partyMembers, StatusModifierType.ACT_HEAL, MoveType.TURN_END_HEAL));

        // 적 턴종 힐 처리
        battleContext.setCurrentMainActor(enemy);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_HEAL, MoveType.TURN_END_HEAL));

        // 아군 오의 게이지 상승
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(partyMembers, StatusModifierType.ACT_CHARGE_GAUGE_UP, MoveType.TURN_END_CHARGE_GAUGE));

        // 아군 오의게이지 감소
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_CHARGE_GAUGE_DOWN, MoveType.TURN_END_CHARGE_GAUGE));

        // 적 오의게이지 상승 은 고양효과로 갈음
        // 적 오의게이지 턴 종료시 하락은 미구현

        // 아군에 대한 턴종 데미지 처리
        battleContext.setCurrentMainActor(enemy);
        results.addAll(process(partyMembers, StatusModifierType.ACT_DAMAGE, MoveType.TURN_END_DAMAGE)); // ACT_RATE_DAMAGE 까지 처리

        // 적에 대한 턴종 데미지 처리
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_DAMAGE, MoveType.TURN_END_DAMAGE));

        return results;
    }

    /**
     * 턴 종료시 처리할 modifier 당 해당하는 결과를 만들어 반환
     *
     * @param targetActors      타겟, 적의경우 List.of(enemy) 사용
     * @param modifierType      처리할 modifierType, ACT_XXX 계열의 후처리 필요 modifier 만 가능
     * @param transientMoveType ActorLogic.moveType, TransientMove 만 가능
     * @return
     */
    protected List<ActorLogicResult> process(List<Actor> targetActors, StatusModifierType modifierType, MoveType transientMoveType) {
        List<SetStatusEffectResult> setStatusEffectResults = new ArrayList<>();
        // modifier 를 가진 모든 스테이터스를 key, 해당 스테이터스가 부여된 actor 를 value 로
        Map<BaseStatusEffect, List<Actor>> statusMap = getStatusMapByModifier(targetActors, modifierType);
        // 스테이터스 1개마다 결과 전부 만듦
        statusMap.forEach((status, targets) -> {
            Map<Long, SetStatusEffectResult.Result> results = new HashMap<>();
            targets.forEach(target -> {
                ProcessStatusLogic.ProcessStatusLogicResult processResult = processStatusLogic.process(target, status, modifierType);

                if (results.containsKey(target.getId())) throw new IllegalArgumentException("턴 종료 상태효과 적용시, 하나의 상태효과가 여러타겟에 적용");
                results.put(target.getId(), SetStatusEffectResult.Result.builder()
                        .actorId(target.getId())
                        .addedStatusEffects(processResult.getAddedStatusEffects())
                        .removedStatusEffects(processResult.getRemovedStatusEffects())
                        .healValue(processResult.getHealValue())
                        .damageValue(processResult.getDamageValue())
                        .build());
            });

            setStatusEffectResults.add(SetStatusEffectResult.builder().results(results).build());
        });

        Move move = Move.getTransientMove(transientMoveType);
        List<ActorLogicResult> logicResults = setStatusEffectResults.stream()
                .map(setStatusResult -> characterLogicResultMapper.toResult(move, null, setStatusResult))
                .toList();
        return logicResults;
    }


    /**
     * 파라미터로 받은 modifier 를 포함하는 StatusEffect 를 가진 Actor를 BaseStatusEffect 를 key 로 하여 반환
     * 스테이터스와 부여된 Actor 쌍으로 처리를 위해 사용
     *
     * @param targets
     * @param modifierTypes
     * @return
     */
    protected Map<BaseStatusEffect, List<Actor>> getStatusMapByModifier(List<Actor> targets, StatusModifierType... modifierTypes) {
        return targets.stream()
                .flatMap(target -> getEffectsByModifierTypes(target, modifierTypes)
                        .stream()
                        .map(battleStatus -> Map.entry(battleStatus.getBaseStatusEffect(), target)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }


    /*
    원본 처리순서
    (참고) 우리쪽은 캐릭터에 달린 onTurnEnd 로 스테이터스를 세팅하기때문에 근본적으로 처리순서가 다름

    피데미지시 효과
    스택소모 버프/디버프, 웨폰버스트, 피타겟 효과
    클리어
    아군 재생
    적 재생
    아군 오의게이지 상승
    아군 오의게이지 감소
    아군 턴데미지
    적 턴데미지

    출처 https://x.com/zekasyuz/status/799531711285592064
     */

}
