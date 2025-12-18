package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.actor.enemy.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectsByModifierTypes;

@Component
@Slf4j
@RequiredArgsConstructor
public class TurnEndStatusLogic {

    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ProcessStatusLogic processStatusLogic;
    private final SetStatusLogic setStatusLogic;
    private final BattleContext battleContext; // 특히, 아군의 턴데미지 적의 턴데미지 때 mainActor 설정이 필요하여 사용

    /**
     * 턴 종료 '후' 스테이터스 + 상태처리
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    public List<ActorLogicResult> processTurnEndAfter(Actor enemy, List<Actor> partyMembers) {
        // 턴 종료 '후' 상태 초기화
        partyMembers.forEach(Actor::progressAbilityCoolDown); // 어빌리티 쿨다운 진행
        partyMembers.forEach(Actor::resetAbilityUseCount); // 어빌리티 사용횟수 초기화
        partyMembers.forEach(Actor::resetStrikeCount); // 공격 행동 횟수 초기화
        setStatusLogic.progressStatusEffects(enemy, partyMembers); // 배틀 스테이터스 남은 턴수 진행

        return List.of(enemyLogicResultMapper.toResult(Move.getTransientMove(MoveType.TURN_FINISH)));
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
     * @param targetActors 타겟, 적의경우 List.of(enemy) 사용
     * @param modifierType 처리할 modifierType, ACT_XXX 만 가능
     * @param transientMoveType ActorLogic.moveType, TransientMove 만 가능
     * @return
     */
    protected List<ActorLogicResult> process(List<Actor> targetActors, StatusModifierType modifierType, MoveType transientMoveType) {
        List<SetStatusResult> setStatusResults = new ArrayList<>();
        // modifier 를 가진 모든 스테이터스를 key, 해당 스테이터스가 부여된 actor 를 value 로
        Map<BaseStatusEffect, List<Actor>> statusMap = getStatusMapByModifier(targetActors, modifierType);
        // 스테이터스 1개마다 결과 전부 만듦
        statusMap.forEach((status, targets) -> {
            List<List<ResultStatusEffectDto>> addedStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<ResultStatusEffectDto>()).collect(Collectors.toList());
            List<List<ResultStatusEffectDto>> removedStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<ResultStatusEffectDto>()).collect(Collectors.toList());
            List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null));
            List<Integer> damageValues = new ArrayList<>(Collections.nCopies(5, null));
            targets.forEach(target -> {
                ProcessStatusLogic.ProcessStatusLogicResult processResult = processStatusLogic.process(target, status, modifierType);
                // 자기자리에 set
                addedStatusesList.set(target.getCurrentOrder(), processResult.getAddedStatusEffects().stream().map(ResultStatusEffectDto::of).toList());
                removedStatusesList.set(target.getCurrentOrder(), processResult.getRemovedStatusEffects().stream().map(ResultStatusEffectDto::of).toList());
                healValues.set(target.getCurrentOrder(), processResult.getHealValue());
                damageValues.set(target.getCurrentOrder(), processResult.getDamageValue());
            });
            setStatusResults.add(SetStatusResult.builder()
                    .addedStatusesList(addedStatusesList)
                    .removedStatuesList(removedStatusesList)
                    .damageValues(damageValues)
                    .healValues(healValues)
                    .build());
        });

        Move move = Move.getTransientMove(transientMoveType);
        List<ActorLogicResult> logicResults = setStatusResults.stream()
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
