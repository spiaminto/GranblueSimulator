package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.*;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class StatusUtil {

    /**
     *  Effect: StatusEffect
     *  BaseEffect: BaseStatusEffect
     *  BasicEffect / BasicBaseEffect : 기본 상태효과
     */


    /**
     * 특정 ModifierType 이 중복 적용되었을때 (주로 고유 상태효과의 별항효과들), Modifier.value 가 최대값인 StatusEffect 를 반환 <br>
     * 난격 (ATTACK_MULTI_HIT), 재행동 (MULTI_STRIKE) 에서 사용중
     *
     * @param actor
     * @param statusModifierType 합산할 modifier 타입
     * @return 없으면 0
     */
    public static double getEffectIsMaxValue(Actor actor, StatusModifierType statusModifierType) {
        List<StatusModifier> statusModifiers = getModifierMap(actor).getOrDefault(statusModifierType, Collections.emptyList());
        return statusModifiers == null || statusModifiers.isEmpty() ?
                0 :
                statusModifiers.stream()
                        .map(StatusModifier::getCalcValue)
                        .mapToDouble(Double::doubleValue)
                        .max().orElse(0);
    }

    /**
     * Actor.StatusEffects 를 각각의 StatusModifier 를 기준으로 Map<StatusModifierType, List<StatusEffect>> 로 변환 (플랫화) <br>
     * 이때, Modifier 를 통한 계산을 원할히 하기 위해 Modifier.level 을 현재 StatusEffect 의 레벨로 설정
     *
     * @param actor
     * @return
     */
    public static Map<StatusModifierType, List<StatusModifier>> getModifierMap(Actor actor) {
        return actor.getStatusEffects().stream()
                .map(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().values().stream()
                        .map(statusModifier -> statusModifier.setCurrentLevel(statusEffect.getLevel())) // Modifier 에 계산된 레벨 설정
                        .toList())
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                                StatusModifier::getType,
                                mapping(Function.identity(), toList())
                        )
                );
    }

    /**
     * name 이름의 StatusEffect 가졌는지 확인 (contains)
     *
     * @param actor
     * @param name
     * @return 가졌으면 true
     */
    public static boolean hasEffectByName(Actor actor, String name) {
        return actor.getStatusEffects().stream()
                .anyMatch(battleStatus -> battleStatus.getBaseStatusEffect().getName().contains(name));
    }

    /**
     * 해당 StatusTargetType 인 StatusEffect 를 반환
     *
     * @param actor
     * @param targetType
     * @return
     */
    public static List<StatusEffect> getEffectsByTargetType(Actor actor, StatusEffectTargetType targetType) {
        return actor.getStatusEffects().stream()
                .filter(battleStatus -> battleStatus.getBaseStatusEffect().getTargetType() == targetType)
                .toList();
    }

    /**
     * 해당 MoveType 의 Status 중 name 을 가진 Status 반환, findfirst, contains
     *
     * @param actor
     * @param name
     * @return
     * @throws IllegalArgumentException 해당 이름의 상태효과 가 없음
     */
    public static BaseStatusEffect getBaseEffectByName(Actor actor, MoveType moveType, String name) {
        return actor.getBaseActor().getMoves().get(moveType).getStatusEffects().stream()
                .filter(status -> status.getName().contains(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("[getBaseEffectByName] 해당 name 을 가진 BaseStatusEffect 가 없습니다. name = " + name + "moveType = " + moveType.name()));
    }

    /**
     * 해당 name 을 가진 StatusEffect 반환, findfirst, contains
     *
     * @param actor
     * @param name
     * @return
     */
    public static Optional<StatusEffect> getEffectByName(Actor actor, String name) {
        return actor.getStatusEffects().stream()
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getName().contains(name))
                .findFirst();
    }

    /**
     * 해당 name 을 가진 List<StatusEffect> 반환, contains
     *
     * @param actor
     * @param name
     * @return
     */
    public static List<StatusEffect> getEffectsByName(Actor actor, String name) {
        return actor.getStatusEffects().stream()
                .filter(battleStatus -> battleStatus.getBaseStatusEffect().getName().contains(name))
                .toList();
    }

    /**
     * 해당 StatusModifierType 을 가진 StatusEffect 반환, findFirst, optional
     *
     * @param actor
     * @param statusModifierType
     * @return
     */
    public static Optional<StatusEffect> getEffectByModifierType(Actor actor, StatusModifierType statusModifierType) {
        return actor.getStatusEffects().stream()
                .filter(battleStatus -> battleStatus.getBaseStatusEffect().getStatusModifiers().containsKey(statusModifierType))
                .findFirst();
    }

    /**
     * 해당 statusEffectType 중 하나라도 가진 StatusEffect 모두 반환
     * 턴종 스테이터스 처리시 사용
     *
     * @param actor
     * @param statusModifierTypes
     * @return
     */
    public static List<StatusEffect> getEffectsByModifierTypes(Actor actor, StatusModifierType... statusModifierTypes) {
        return actor.getStatusEffects().stream()
                .filter(battleStatus -> Arrays.stream(statusModifierTypes)
                        .anyMatch(statusEffectType -> battleStatus.getBaseStatusEffect().getStatusModifiers().containsKey(statusEffectType))
                )
                .toList();
    }

    /**
     * 주어진 actor 의 StatusEffect 중 baseStatusEffectId 가 같은 것을 반환 (고유 상태 효과)
     *
     * @param actor
     * @param baseStatusEffectId
     * @return Optional<StatusEffect>
     */
    public static Optional<StatusEffect> getEffectByBaseId(Actor actor, Long baseStatusEffectId) {
        return actor.getStatusEffects().stream()
                .filter(battleStatus -> baseStatusEffectId.equals(battleStatus.getBaseStatusEffect().getId()))
                .findFirst();
    }

    /**
     * 주어진 actor 의 StatusEffect 중 StatusModifierType 이 같은 기본 상태효과를 반환
     *
     * @param actor
     * @param modifierType
     * @return Optional<StatusEffect>
     */
    public static Optional<StatusEffect> getBasicEffectByModifierType(Actor actor, StatusModifierType modifierType) {
        return actor.getStatusEffects().stream()
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().size() == 1) // 기본 상태 효과
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().get(modifierType) != null)
                .findFirst();
    }

    /**
     * 주어진 actor 의 StatusEffect 중 StatusModifierType, StatusEffectTargetType 이 모두 같은 기본 상태효과를 반환 <br>
     * 기본 상태효과 + 같은 modifier + 같은 타겟항 (참전자 / 개인) 모두 만족할경우 반환함.
     *
     * @param actor
     * @param modifierType
     * @return Optional<StatusEffect>
     */
    public static Optional<StatusEffect> getBasicEffectByModifierTypeAndTargetType(Actor actor, StatusModifierType modifierType, StatusEffectTargetType targetType) {
        return actor.getStatusEffects().stream()
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().size() == 1) // 기본 상태 효과
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getTargetType() == targetType) // 타겟 타입 (주로 개인 / 참전자 구분용)
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().get(modifierType) != null)
                .findFirst();
    }


    /**
     * 모든 partyMembers 의 StatusEffects 중, 주어진 modifierType 을 포함하는 StatusEffect 중 가장 우선순위가 높은쪽을 반환한다. <br>
     * 우선순위는 StatusModifier.getValue() -> StatusEffect.createdAt 순으로 비교 <br>
     * 감싸기 적용시 사용중
     *
     * @param actors             모든 파티원 (partyMembers)
     * @param statusModifierType
     * @return StatusEffectType 이 같은것중 적용 우선순위가 가장 높은 Optional<StatusEffect>
     */
    public static Optional<StatusEffect> getMaxValueEffectByModifierType(List<Actor> actors, StatusModifierType statusModifierType) {
        return actors.stream()
                .map(Actor::getStatusEffects)
                .flatMap(List::stream)
                .filter(battleStatus -> battleStatus.getBaseStatusEffect().getStatusModifiers().containsKey(statusModifierType))
                .max(Comparator
                        .comparing((StatusEffect statusEffect) -> statusEffect.getBaseStatusEffect().getStatusModifiers().get(statusModifierType).getValue())
                        .thenComparing(StatusEffect::getCreatedAt))
                ;
    }

    /**
     * name 이름의 고유 스테이터스 레벨 확인
     *
     * @param actor
     * @param name
     * @return int 레벨
     */
    public static int getLevelByName(Actor actor, String name) {
        Optional<StatusEffect> matchedBattleStatus = actor.getStatusEffects().stream()
                .filter(battleStatus -> name.equals(battleStatus.getBaseStatusEffect().getName()))
                .findFirst();
        return matchedBattleStatus.map(StatusEffect::getLevel).orElse(0);
    }

    public static boolean isReachedLevelByName(Actor actor, String name, int level) {
        return actor.getStatusEffects().stream()
                .filter(statusEffect -> name.equals(statusEffect.getBaseStatusEffect().getName()))
                .anyMatch(statusEffect -> statusEffect.getLevel() >= level);
    }

    public static boolean isReachedMaxLevelByName(Actor actor, String name) {
        return actor.getStatusEffects().stream()
                .filter(statusEffect -> name.equals(statusEffect.getBaseStatusEffect().getName()))
                .anyMatch(StatusEffect::isMaxLevel);
    }

    /**
     * 주어진 항의 버프수치 합산을 구함
     *
     * @param statusModifierMap 항 맵
     * @return 합산수치, 없으면 0
     */
    public static double getModifierValueSum(Map<StatusModifierType, List<StatusModifier>> statusModifierMap, StatusModifierType type) {
        List<StatusModifier> statusModifiers = statusModifierMap.get(type);
        return statusModifiers == null || statusModifiers.isEmpty() ?
                0 :
                statusModifiers.stream()
                        .map(StatusModifier::getCalcValue) // 레벨제 계산후 반환
                        .mapToDouble(Double::doubleValue)
                        .peek(value -> log.info("getModifierValueSum: {}, {}", type, value))
                        .sum();
    }

    /**
     * 주어진 항의 modifier 수치 최댓값을 구함 <br>
     * 주로 겹치는 고유 상태효과의 Modifier 들 중 합산 상한연산이 아닌 우열연산 하는 효과에서 사용 <br>
     * 추격 (ADDITIONAL_DAMAGE_X)
     *
     * @param statusModifierMap 항 맵
     * @return 최댓값, 없으면 0
     */
    public static double getModifierValueMax(Map<StatusModifierType, List<StatusModifier>> statusModifierMap, StatusModifierType type) {
        List<StatusModifier> statusModifiers = statusModifierMap.getOrDefault(type, Collections.emptyList());
        return statusModifiers.stream()
                .map(StatusModifier::getCalcValue) // 레벨제 계산후 반환
                .mapToDouble(Double::doubleValue)
                .max().orElse(0);
    }

    /**
     * 주어진 항의 modifier 수치 최솟값을 구함 <br>
     * 주로 겹치는 고유 상태효과의 Modifier 들 중 합산 상한연산이 아닌 우열연산 하는 효과에서 사용 <br>
     * 피데미지 고정 (TAKEN_DAMAGE_FIX), 블록 효과 (TAKEN_DAMAGE_BLOCK) 에서 사용
     *
     * @param statusModifierMap 항 맵
     * @return 최댓값, 없으면 -1
     */
    public static double getModifierValueMin(Map<StatusModifierType, List<StatusModifier>> statusModifierMap, StatusModifierType type) {
        List<StatusModifier> statusModifiers = statusModifierMap.get(type);
        return statusModifiers == null || statusModifiers.isEmpty() ?
                -1 :
                statusModifiers.stream()
                        .map(StatusModifier::getCalcValue) // 레벨제 계산후 반환
                        .mapToDouble(Double::doubleValue)
                        .min().orElse(-1);
    }


}
