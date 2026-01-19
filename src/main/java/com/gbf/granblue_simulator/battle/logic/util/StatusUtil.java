package com.gbf.granblue_simulator.battle.logic.util;

import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class StatusUtil {

    /**
     * Actor.StatusEffects 를 각각의 StatusModifier 를 기준으로 Map<StatusModifierType, List<StatusEffect>> 로 변환 (플랫화) <br>
     *
     * @param actor
     * @return
     */
    public static Map<StatusModifierType, List<StatusEffect>> getModifierMap(Actor actor) {
        Map<StatusModifierType, List<StatusEffect>> map = new EnumMap<>(StatusModifierType.class);

        for (StatusEffect statusEffect : actor.getStatusEffects()) {
            for (StatusModifierType type : statusEffect.getBaseStatusEffect().getStatusModifiers().keySet()) { // StatusModifierType 으로 순회
                map.computeIfAbsent(type, key -> new ArrayList<>()).add(statusEffect); // StatusModifierType key 로 추가, 없으면 새로 생성해서 삽입
            }
        }

        return map;
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
     * 해당 Move 가 가진 BaseStatusEffect 중  파라미터로 넘어온 name 을 가진 BaseStatusEffect 반환, findfirst, contains <br>
     * 해당 Move 의 여러 상태효과중 특정 상태효과를 가져와야 할때 사용 (반드시 존재하는 상태효과여야 함)
     *
     * @param move
     * @param name
     * @return
     * @throws IllegalArgumentException 해당 이름의 상태효과 가 없음
     */
    public static BaseStatusEffect getBaseEffectByNameFromMove(BaseMove move, String name) {
        return move.getBaseStatusEffects().stream()
                .filter(status -> status.getName().contains(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("해당 이름의 상태효과 없음"));
    }

    /**
     * 해당 Move 가 가진 BaseStatusEffect 중  파라미터로 넘어온 name 을 가진 BaseStatusEffect 를 Optional 반환, findfirst, contains <br>
     * 해당 Move 가 특정 상태효과를 가졌는지 확인해야 할때 주로 사용을 위해 분리
     *
     * @param move
     * @param name
     * @return
     */
    public static Optional<BaseStatusEffect> checkBaseEffectByNameFromMove(BaseMove move, String name) {
        return move.getBaseStatusEffects().stream()
                .filter(status -> status.getName().contains(name))
                .findFirst();
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
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getStatusModifiers().containsKey(statusModifierType))
                .findFirst();
    }

    /**
     * 해당 statusEffectType 를 가진 StatusEffect 모두 반환
     * 턴종 스테이터스 처리시 사용
     *
     * @param actor
     * @param statusModifierType
     * @return
     */
    public static List<StatusEffect> getEffectsByModifierType(Actor actor, StatusModifierType statusModifierType) {
        List<StatusEffect> result = new ArrayList<>();

        for (StatusEffect statusEffect : actor.getStatusEffects()) {
            if (statusEffect.getBaseStatusEffect().getStatusModifiers().containsKey(statusModifierType)) {
                result.add(statusEffect);
            }
        }

        return result;

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
                        .comparing((StatusEffect statusEffect) -> statusEffect.getBaseStatusEffect().getStatusModifiers().get(statusModifierType).getInitValue())
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
     * @param type              합산할 Modifier 타입
     * @return 합산수치, 없으면 0
     */
    public static double getModifierValueSum(Map<StatusModifierType, List<StatusEffect>> statusModifierMap, StatusModifierType type) {
        List<StatusEffect> statusEffects = statusModifierMap.get(type);
        if (statusEffects == null || statusEffects.isEmpty()) return 0;

        double sum = 0;
        for (StatusEffect effect : statusEffects) {
            sum += effect.getModifierValue(type); // 합산
        }
        return sum;
    }

    /**
     * 주어진 항의 modifier 수치를 곱연산 (XX율 끼리의 곱연산)<br>
     * 같은 항 내부에서 중복적용시 곱연산 하는 일부 효과를 위해 사용
     * 속성 내성 에서 사용중
     *
     * @param statusModifierMap 항 맵
     * @return 승산수치, 없으면 0
     */
    public static double getModifierValueMultiplied(Map<StatusModifierType, List<StatusEffect>> statusModifierMap, StatusModifierType type) {
        List<StatusEffect> statusEffects = statusModifierMap.get(type);
        if (statusEffects == null || statusEffects.isEmpty()) return 0;

        double productRate = 0;
        for (StatusEffect effect : statusEffects) {
            double val = effect.getModifierValue(type);
            if (val < 0 || val > 1)
                log.warn("[getModifierValueMultiplied] 승산을 위한 효과량에 주의필요 effectName = {}, value = {}", effect.getBaseStatusEffect().getName(), val);
            productRate = (productRate + val) - (productRate * val);
            // 0.5, 0.6 -> 0.8 / 1 * (1 - 0.5) * (1 - 0.6) = 0.2
        }
        return productRate;
    }

    /**
     * 주어진 항의 modifier 수치 최댓값을 구함 <br>
     * 주로 겹치는 고유 상태효과의 Modifier 들 중 합산 상한연산이 아닌 우열연산 하는 효과에서 사용 <br>
     * 추격 (ADDITIONAL_DAMAGE_X)
     *
     * @param statusModifierMap 항 맵
     * @return 최댓값, 없으면 0
     */
    public static double getModifierValueMax(Map<StatusModifierType, List<StatusEffect>> statusModifierMap, StatusModifierType type) {
        List<StatusEffect> statusEffects = statusModifierMap.get(type);
        if (statusEffects == null || statusEffects.isEmpty()) return 0;

        double max = 0;
        for (StatusEffect effect : statusEffects) {
            double val = effect.getModifierValue(type);
            if (val > max) {
                max = val;
            }
        }
        return max;
    }

    /**
     * 주어진 항의 modifier 수치 최솟값을 구함 <br>
     * 주로 겹치는 고유 상태효과의 Modifier 들 중 합산 상한연산이 아닌 우열연산 하는 효과에서 사용 <br>
     * 피데미지 고정 (TAKEN_DAMAGE_FIX), 블록 효과 (TAKEN_DAMAGE_BLOCK) 에서 사용
     *
     * @param statusModifierMap 항 맵
     * @return 최솟값, 없으면 0
     */
    public static double getModifierValueMin(Map<StatusModifierType, List<StatusEffect>> statusModifierMap, StatusModifierType type) {
        List<StatusEffect> statusEffects = statusModifierMap.get(type);
        if (statusEffects == null || statusEffects.isEmpty()) return 0;

        Double min = null; // null 로 초기화 후 사용, null 을 반환하진 않음.
        for (StatusEffect effect : statusEffects) {
            double val = effect.getModifierValue(type);
            if (min == null || val < min) {
                min = val;
            }
        }
        return min;
    }

    /**
     * 주어진 항의 modifier 수치중 가장 마지막에 적용된 modifier 의 적용시간을 가져옴 <br>
     * 피데미지 속성변환 에서 사용중
     *
     * @param statusModifierMap 항 맵
     * @return 최솟값, 없으면 0
     */
    public static LocalDateTime getLatestModifierTime(Map<StatusModifierType, List<StatusEffect>> statusModifierMap, StatusModifierType type) {
        List<StatusEffect> statusEffects = new ArrayList<>(statusModifierMap.getOrDefault(type, Collections.emptyList()));
        if (statusEffects.isEmpty()) return null;

        statusEffects.sort(Comparator.comparing(StatusEffect::getCreatedAt).reversed());
        LocalDateTime latestTime = statusEffects.getFirst().getCreatedAt();
        return latestTime;
    }




    /**
     *  Effect: StatusEffect
     *  BaseEffect: BaseStatusEffect
     *  BasicEffect / BasicBaseEffect : 기본 상태효과
     */


}
