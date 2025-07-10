package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StatusUtil {

    /**
     * 주어진 battleActor 의 battleStatus 중 statusEffectType 을 가진 battleStatus 의 statusEffect.value 의 합
     *
     * @param battleActor
     * @param statusEffectType 합산할 이펙트 타입
     * @return
     */
    public static double getEffectValueSum(BattleActor battleActor, StatusEffectType statusEffectType) {
        List<StatusEffect> statusEffects = getStatusEffectMap(battleActor).getOrDefault(statusEffectType, Collections.emptyList());
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.stream()
                        .map(StatusEffect::getCalcValue) // 레벨제 계산후 반환
                        .mapToDouble(Double::doubleValue)
                        .sum();
    }

    /**
     * 주어진 battleActor 의 battleStatus 중 statusEffectType 을 가진 battleStatus 의 statusEffect.value 중 최댓값
     * 고유버프를 통한 항 중복시 최댓값만을 선택할때 사용 (ex 야치마 서포어비 추격 + 기타 고유버프 추격 항 공통일떄 max 선택)
     *
     * @param battleActor
     * @param statusEffectType 합산할 이펙트 타입
     * @return
     */
    public static double getEffectValueMax(BattleActor battleActor, StatusEffectType statusEffectType) {
        List<StatusEffect> statusEffects = getStatusEffectMap(battleActor).getOrDefault(statusEffectType, Collections.emptyList());
        return statusEffects == null || statusEffects.isEmpty() ?
                0 :
                statusEffects.stream()
                        .map(StatusEffect::getCalcValue)
                        .mapToDouble(Double::doubleValue)
                        .max().orElse(0);
    }

    /**
     * Move.Statuses 의 각각의 statusEffect 를 Map<StatusEffectType, List<StatusEffect>> 로 변환 (플랫화)
     *
     * @param move
     * @return
     */
    public static Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(Move move) {
        return move.getStatuses().stream()
                .flatMap(status -> status.getStatusEffects().values().stream())
                .collect(Collectors.groupingBy(
                        StatusEffect::getType,
                        mapping(Function.identity(), collectingAndThen(
                                toList(), list -> list != null ? list : new ArrayList<>()
                        ))
                ));
    }

    /**
     * BattleActor.BattleStatuses 의 각각의 statusEffect 를 Map<StatusEffectType, List<StatusEffect>> 로 변환 (플랫화)
     *
     * @param battleActor
     * @return
     */
    public static Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(BattleActor battleActor) {
        return getFlatStatusEffectStream(battleActor)
                .collect(Collectors.groupingBy(
                                StatusEffect::getType,
                                mapping(Function.identity(), toList())
                        )
                );
    }

    /**
     * 추격만 별도로 가져오는 메서드
     *
     * @param battleActor
     * @return
     */
    public static List<StatusEffect> getAdditionalDamageEffects(BattleActor battleActor) {
        return getFlatStatusEffectStream(battleActor)
                .filter(statusEffect ->
                        statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_A ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_C ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_U ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_W ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_S
                ).toList();
    }

    private static Stream<StatusEffect> getFlatStatusEffectStream(BattleActor battleActor) {
        return battleActor.getBattleStatuses().stream()
                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().values().stream()
                        .map(statusEffect -> statusEffect.setCurrentLevel(battleStatus.getLevel())).toList())
                .flatMap(List::stream);
    }

    /**
     * name 이름의 battleStatus 가졌는지 확인 (contains)
     *
     * @param battleActor
     * @param name
     * @return 가졌으면 true
     */
    public static boolean hasBattleStatus(BattleActor battleActor, String name) {
        return battleActor.getBattleStatuses().stream()
                .anyMatch(battleStatus -> battleStatus.getStatus().getName().contains(name));
    }

    /**
     * 스테이터스 이름으로 현재 적용된 스테이터스가 같은이름(contains)의 버프, 레벨제 만렙, 영속인 스테이터스인지 확인 (모든 조건을 만족하면 스테이터스 적용을 스킵)
     *
     * @param battleActor
     * @param name
     * @return 스테이터스 적용 스킵 가능하면 true
     */
    public static boolean isStatusSetSkippable(BattleActor battleActor, String name) {
        return getBattleStatusByName(battleActor, name).map(battleStatus ->
                battleStatus.getStatus().getType() == StatusType.BUFF &&
                        battleStatus.getLevel() > 0 &&
                        battleStatus.isMaxLevel() &&
                        battleStatus.isPerpetual()
        ).orElse(false);
    }

    /**
     * 해당 MoveType 의 Status 중 name 을 가진 Status 반환, findfirst, contains
     *
     * @param battleActor
     * @param name
     * @return
     * @throws IllegalArgumentException 해당 이름의 status 가 없음
     */
    public static Status getStatusByNameFromMove(BattleActor battleActor, MoveType moveType, String name) {
        return battleActor.getActor().getMoves().get(moveType).getStatuses().stream()
                .filter(status -> status.getName().contains(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("해당 name 을 가진 Status 가 없습니다. name = " + name + "moveType = " + moveType.name()));
    }

    /**
     * 해당 name 을 가진 BattleStatus 반환, findfirst, contains
     *
     * @param battleActor
     * @param name
     * @return
     */
    public static Optional<BattleStatus> getBattleStatusByName(BattleActor battleActor, String name) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getName().contains(name))
                .findFirst();
    }

    /**
     * 해당 name 을 가진 List<BattleStatus> 반환, contains
     *
     * @param battleActor
     * @param name
     * @return
     */
    public static List<BattleStatus> getBattleStatusesByName(BattleActor battleActor, String name) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getName().contains(name))
                .toList();
    }

    /**
     * 해당 name 을 가진 BattleStatus 반환, contains
     *
     * @param battleActor
     * @param statusType
     * @return
     */
    public static List<BattleStatus> getBattleStatuesByStatusType(BattleActor battleActor, StatusType statusType) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getType() == statusType)
                .toList();
    }

    /**
     * 해당 statusEffectType 을 가진 BattleStatus 반환, findFirst, orElse null
     * 오의 재발동에서만 사용중
     *
     * @param battleActor
     * @param statusEffectType
     * @return
     */
    public static BattleStatus getBattleStatusByEffectType(BattleActor battleActor, StatusEffectType statusEffectType) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getStatusEffects().containsKey(statusEffectType))
                .findFirst().orElse(null);
    }

    /**
     * 해당 statusEffectType 중 하나라도 가진 BattleStatus 모두 반환
     * 턴종 스테이터스 처리시 사용
     *
     * @param battleActor
     * @param statusEffectTypes
     * @return
     */
    public static List<BattleStatus> getBattleStatusesByEffectType(BattleActor battleActor, StatusEffectType... statusEffectTypes) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> Arrays.stream(statusEffectTypes)
                        .anyMatch(statusEffectType -> battleStatus.getStatus().getStatusEffects().containsKey(statusEffectType)
                        ))
                .toList();
    }

    /**
     * BattleActor.battleStatuses 에서 동일한 id의 status 찾아 Optional 로 반환
     * 주의) id 가 아닌 status.name 으로 동일여부를 판단함. 따라서 status.statusEffect 는 다를수 있음.
     *
     * @param battleActor
     * @param status
     * @return Optional<BattleStatus>
     */
    public static Optional<BattleStatus> getSameIdBattleStatus(BattleActor battleActor, Status status) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> status.getId().equals(battleStatus.getStatus().getId()))
                .findFirst();
    }

    /**
     * BattleActor.battleStatuses 에서 단일 statusEffect 로 구성되며, 그 statusEffect 가 파라미터로 받은 status 의 statusEffect 와 동일한 경우 그 battleStatus를 Optional 반환
     *
     * @param battleActor
     * @param status
     * @return Optional<BattleStatus> battleStatus, 단일 statusEffect 로 구성되지 않은 status 의 경우 Optional.empty
     */
    public static Optional<BattleStatus> getSameEffectTypeStatus(BattleActor battleActor, Status status) {
        Map<StatusEffectType, StatusEffect> inputStatusEffects = status.getStatusEffects();
        if (inputStatusEffects.size() != 1) return Optional.empty();
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> {
                            // 각 battleStatus 중 단일 StatusEffect 로 이루어지면서 입력된 status 와 동일한 StatusEffect 를 가진것을 필터링
                            Map<StatusEffectType, StatusEffect> currentBattleStatusEffects = battleStatus.getStatus().getStatusEffects();
                            return currentBattleStatusEffects.size() < 2 &&
                                    currentBattleStatusEffects.get(inputStatusEffects.keySet().iterator().next()) != null;
                        }
                ).findFirst(); // 동일 StatusEffect 끼리는 중첩 안됨 (단일 이펙트로 구성된 스테이터스의 경우)
    }


    /**
     * 모든 partyMembers 를 받아 StatusEffectType 이 같은것중 우선적용될것을 반환한다.
     *
     * @param battleActors     모든 파티원 (partyMembers)
     * @param statusEffectType
     * @return StatusEffectType 이 같은것중 적용 우선순위가 가장 높은 Optional<BattleStatus>
     */
    public static Optional<BattleStatus> getEffectiveCoveringEffect(List<BattleActor> battleActors, StatusEffectType statusEffectType) {
        return battleActors.stream()
                .map(BattleActor::getBattleStatuses)
                .flatMap(List::stream)
                .filter(battleStatus -> battleStatus.getStatus().getStatusEffects().containsKey(statusEffectType))
                .max(Comparator
                        .comparing((BattleStatus battleStatus) -> battleStatus.getStatus().getStatusEffects().get(statusEffectType).getValue()) // StatusEffect.value 높은쪽
                        .thenComparing(BattleStatus::getCreatedAt))// BattleStatus.createdAt 가 더 최근인쪽
                ;
    }


    /**
     * Status 의 StatusEffect 의 값 중 첫번째 반환.
     * 단일 StatusEffect 인 경우 사용 권장
     *
     * @param status
     * @return
     */
    public static Double getFirstStatusEffectValue(Status status) {
        return status.getStatusEffects().entrySet().iterator().next().getValue().getValue();
    }

    /**
     * name 이름의 고유 스테이터스 레벨 확인
     *
     * @param battleActor
     * @param name
     * @return int 레벨
     */
    public static int getUniqueStatusLevel(BattleActor battleActor, String name) {
        Optional<BattleStatus> matchedBattleStatus = battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.map(BattleStatus::getLevel).orElse(0);
    }

    public static boolean isUniqueStatusReachedLevel(BattleActor battleActor, String name, int level) {
        Optional<BattleStatus> matchedBattleStatus = battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.filter(battleStatus -> battleStatus.getLevel() >= level).isPresent();
    }


    //    public Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(BattleActor battleActor) {
//        return battleActor.getBattleStatuses().stream()
//                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().stream()
//                        .map(statusEffect -> statusEffect.setCurrentLevel(battleStatus.getLevel())).toList())
//                .flatMap(List::stream)
//                .collect(Collectors.groupingBy(
//                        StatusEffect::getType,
//                        mapping(Function.identity(), collectingAndThen(
//                                toList(), list -> list != null ? list : new ArrayList<>()
//                        )))
//                );
//    }


}
