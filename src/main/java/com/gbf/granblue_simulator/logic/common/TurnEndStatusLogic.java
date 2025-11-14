package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.domain.base.types.ElementType;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.StatusEffectDto;
import com.gbf.granblue_simulator.logic.actor.enemy.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getEffectsByModifierTypes;

@Component
@Slf4j
@RequiredArgsConstructor
public class TurnEndStatusLogic {

    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ProcessStatusLogic processStatusLogic;
    private final SetStatusLogic setStatusLogic;

    /**
     * 턴 종료시 스테이터스 효과 처리
     *
     * @param enemy
     * @param partyMembers
     * @return
     */
    public List<ActorLogicResult> processTurnEnd(Actor enemy, List<Actor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();
        Actor mainActor = partyMembers.isEmpty() ? enemy : partyMembers.getFirst();
        Move turnEndMove = Move.getTransientMove(MoveType.TURN_END_PROCESS);

        // 아군 턴종 힐 처리
        results.addAll(processPartyHeal(partyMembers).stream()
                .map(setStatusResult -> characterLogicResultMapper.toResult(mainActor, enemy, partyMembers, turnEndMove, null, setStatusResult))
                .toList());

        // 적 턴종 힐 처리
        results.addAll(processEnemyHeal(enemy).stream()
                .map(setStatusResult -> enemyLogicResultMapper.toResult(enemy, partyMembers, turnEndMove, null, null, setStatusResult))
                .toList());

        // 아군 오의 게이지 상승
        results.addAll(processPartyChargeGaugeUp(partyMembers).stream()
                .map(setStatusResult -> characterLogicResultMapper.toResult(mainActor, enemy, partyMembers, turnEndMove, null, setStatusResult))
                .toList());

        // 적 오의게이지 상승 은 고양효과로 갈음

        // 아군 턴종 데미지 처리
        results.addAll(processPartyTurnDamage(partyMembers).stream()
                .map(damageLogicResult -> enemyLogicResultMapper.attackToResult(enemy, partyMembers, turnEndMove, damageLogicResult, damageLogicResult.getTargetOrders()))
                .toList());

        // 적 턴종 데미지 처리
        results.addAll(processEnemyTurnDamage(enemy).stream()
                .map(damageLogicResult -> characterLogicResultMapper.attackToResult(mainActor, enemy, partyMembers, turnEndMove, damageLogicResult))
                .toList());

        return results;
    }

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
        Actor mainActor = partyMembers.isEmpty() ? enemy : partyMembers.getFirst();

        return List.of(characterLogicResultMapper.toResult(mainActor, enemy, partyMembers, Move.getTransientMove(MoveType.TURN_END_PROCESS), null, null));
    }

    /**
     * 아군 재생 처리
     * 같은 재생 스테이터스의 경우 한번에 몰아서 처리
     * 여러 재생 스테이터스가 있는 경우 스테이터스마다 별도로 결과처리 (재생에 대한 반응이 있을시 재생별로 따로 처리해야함 && 보여줄때 합산이 아니고 스테이터스 마다 별도로 보여줘야함)
     *
     * @param partyMembers
     * @return
     */
    private List<SetStatusResult> processPartyHeal(List<Actor> partyMembers) {
        List<SetStatusResult> recoveryResults = new ArrayList<>();
        Map<BaseStatusEffect, List<Actor>> turnRecoveryStatusMap = getStatusMapByEffects(partyMembers, StatusModifierType.ACT_HEAL);
        // 스테이터스 1개당 타겟들에 효과 적용후 SetStatusResult 1개씩 만들어 반환
        turnRecoveryStatusMap.forEach((healStatus, targets) -> {
            log.info("[processPartyHeal] healStatus: {}, targets: {}", healStatus, targets.stream().map(Actor::getName).toList());
            List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null)); // 스테이터스 1개당 회복량 저장 배열
            targets.forEach(target -> {
                int healValue = processStatusLogic.process(target, healStatus, StatusModifierType.ACT_HEAL).getHealValue();
                healValues.set(target.getCurrentOrder(), healValue); // 자기자리 맞춰 세팅
            });
            recoveryResults.add(SetStatusResult.builder().healValues(healValues).build());
        });

        return recoveryResults;
    }

    /**
     * 적 재생 처리
     * 아군과 적 처리를 별도로 보여줘야 하고, 순서도 다르기 때문에 별도의 결과를 만들어 반환
     *
     * @param enemy
     * @return
     */
    private List<SetStatusResult> processEnemyHeal(Actor enemy) {
        List<SetStatusResult> recoveryResults = new ArrayList<>();
        List<StatusEffect> turnRecoveryStatuses = getEffectsByModifierTypes(enemy, StatusModifierType.ACT_HEAL);
        // 스테이터스 1개당 적에게 효과 적용후 SetStatusResult 1개씩 만들어 반환
        turnRecoveryStatuses.forEach(healStatus -> {
            List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, 0)); // 스테이터스 1개당 회복량 저장 배열
            int healValue = processStatusLogic.process(enemy, healStatus.getBaseStatusEffect(), StatusModifierType.ACT_HEAL).getHealValue();
            healValues.set(enemy.getCurrentOrder(), healValue);
            recoveryResults.add(SetStatusResult.builder().healValues(healValues).build());
        });

        return recoveryResults;
    }

    private List<SetStatusResult> processPartyChargeGaugeUp(List<Actor> partyMembers) {
        List<SetStatusResult> chargeGaugeUpResults = new ArrayList<>();
        Map<BaseStatusEffect, List<Actor>> chargeGaugeUpStatusMap = getStatusMapByEffects(partyMembers, StatusModifierType.ACT_CHARGE_GAUGE_UP);
        chargeGaugeUpStatusMap.forEach((chargeGaugeUpStatus, targets) -> {
            List<List<StatusEffectDto>> addedStatsuEffectsList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList());
            targets.forEach(target -> {
                List<StatusEffect> addedStatusEffects = processStatusLogic.process(target, chargeGaugeUpStatus, StatusModifierType.ACT_CHARGE_GAUGE_UP).getAddedStatusEffects();
                addedStatsuEffectsList.set(target.getCurrentOrder(), addedStatusEffects.stream().map(StatusEffectDto::of).toList());
            });
            chargeGaugeUpResults.add(SetStatusResult.builder().addedStatusesList(addedStatsuEffectsList).build());
        });
        return chargeGaugeUpResults;
    }


    /**
     * 아군 턴데미지 처리
     * 타겟정보를 지정하여 결과 반환
     *
     * @param partyMembers
     * @return
     */
    private List<DamageLogicResult> processPartyTurnDamage(List<Actor> partyMembers) {
        List<DamageLogicResult> damageLogicResults = new ArrayList<>();
        Map<BaseStatusEffect, List<Actor>> turnDamageStatusMap = getStatusMapByEffects(partyMembers, StatusModifierType.ACT_DAMAGE, StatusModifierType.ACT_RATE_DAMAGE);
        // 스테이터스 1개당 타겟들에 효과 적용후 DamageLogicResult 1개씩 만들어 반환
        turnDamageStatusMap.forEach((turnDamageStatus, targets) -> {
            List<Integer> damages = new ArrayList<>();
            List<Integer> targetOrders = new ArrayList<>();
            List<ElementType> elementTypes = new ArrayList<>();
            StatusModifierType modifierType = turnDamageStatus.getModifier(StatusModifierType.ACT_DAMAGE) != null ? StatusModifierType.ACT_DAMAGE : StatusModifierType.ACT_RATE_DAMAGE;
            targets.forEach(target -> {
                int damage = processStatusLogic.process(target, turnDamageStatus, modifierType).getDamageValue();
                damages.add(damage);
                targetOrders.add(target.getCurrentOrder());
                elementTypes.add(ElementType.NONE); // 턴데미지는 무조건 무속성
            });
            damageLogicResults.add(DamageLogicResult.builder().damages(damages).targetOrders(targetOrders).elementTypes(elementTypes).build());
        });
        return damageLogicResults;
    }

    /**
     * 적 재생 처리
     * 타겟정보를 지정하지 않음
     * 아군과 적 처리를 별도로 보여줘야 하고, 순서도 다르기 때문에 별도의 결과를 만들어 반환
     *
     * @param enemy
     * @return
     */
    private List<DamageLogicResult> processEnemyTurnDamage(Actor enemy) {
        List<DamageLogicResult> damageLogicResults = new ArrayList<>();
        List<StatusEffect> turnRecoveryStatuses = getEffectsByModifierTypes(enemy, StatusModifierType.ACT_DAMAGE, StatusModifierType.ACT_RATE_DAMAGE);
        // 스테이터스 1개당 적에게 효과 적용후 SetStatusResult 1개씩 만들어 반환
        turnRecoveryStatuses.forEach(damageStatus -> {
            StatusModifierType modifierType = damageStatus.getBaseStatusEffect().getModifier(StatusModifierType.ACT_DAMAGE) != null ? StatusModifierType.ACT_DAMAGE : StatusModifierType.ACT_RATE_DAMAGE;
            int damage = processStatusLogic.process(enemy, damageStatus.getBaseStatusEffect(), modifierType).getDamageValue();
            damageLogicResults.add(DamageLogicResult.builder().damages(List.of(damage)).elementTypes(List.of(ElementType.NONE)).build());
        });
        return damageLogicResults;
    }

    /**
     * 파라미터로 받은 StatusEffectType 를 포함하는 BattleStatus 를 가진 파티 멤버를 BattleStatus.Status 를 key 로 맵으로 변환하여 반환
     * 같은 스테이터스별로 모아서 처리후 결과를 반환하기 위함.
     *
     * @param partyMembers
     * @param effectTypes
     * @return
     */
    private Map<BaseStatusEffect, List<Actor>> getStatusMapByEffects(List<Actor> partyMembers, StatusModifierType... effectTypes) {
        return partyMembers.stream()
                .flatMap(partyMember -> getEffectsByModifierTypes(partyMember, effectTypes)
                        .stream()
                        .map(battleStatus -> Map.entry(battleStatus.getBaseStatusEffect(), partyMember)))
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
