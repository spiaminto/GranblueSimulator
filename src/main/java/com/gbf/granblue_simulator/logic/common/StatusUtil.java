package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

@Slf4j
@Transactional
@Component
public class StatusUtil {

    /**
     * BattleActor.BattleStatuses 의 각각의 statusEffect 를 Map<StatusEffectType, List<StatusEffect>> 로 변환 (플랫화)
     *
     * @param battleActor
     * @return
     */
    public Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(BattleActor battleActor) {
        return this.getFlatStatusEffectStream(battleActor)
                .collect(Collectors.groupingBy(
                        StatusEffect::getType,
                        mapping(Function.identity(), collectingAndThen(
                                toList(), list -> list != null ? list : new ArrayList<>()
                        )))
                );
    }

    /**
     * 추격만 별도로 가져오는 메서드
     *
     * @param battleActor
     * @return
     */
    public List<StatusEffect> getAdditionalDamageEffects(BattleActor battleActor) {
        return this.getFlatStatusEffectStream(battleActor)
                .filter(statusEffect ->
                        statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_A ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_C ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_E ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_W ||
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_S
                ).toList();
    }

    protected Stream<StatusEffect> getFlatStatusEffectStream(BattleActor battleActor) {
        return battleActor.getBattleStatuses().stream()
                .map(battleStatus -> battleStatus.getStatus().getStatusEffects().stream()
                        .map(statusEffect -> statusEffect.setCurrentLevel(battleStatus.getLevel())).toList())
                .flatMap(List::stream);
    }

    /**
     * name 이름의 고유 스테이터스 가졌는지 확인
     *
     * @param battleActor
     * @param name
     * @return 가졌으면 true
     */
    public boolean hasUniqueStatus(BattleActor battleActor, String name) {
        return battleActor.getBattleStatuses().stream()
                .anyMatch(battleStatus -> name.equals(battleStatus.getStatus().getName()));
    }

    /**
     * name 이름의 고유 스테이터스 레벨 확인
     *
     * @param battleActor
     * @param name
     * @return int 레벨
     */
    public int getUniqueStatusLevel(BattleActor battleActor, String name) {
        Optional<BattleStatus> matchedBattleStatus = battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.map(BattleStatus::getLevel).orElse(0);
    }

    public boolean isUniqueStatusReachedLevel(BattleActor battleActor, String name, int level) {
        Optional<BattleStatus> matchedBattleStatus = battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> name.equals(battleStatus.getStatus().getName()))
                .findFirst();
        return matchedBattleStatus.filter(battleStatus -> battleStatus.getLevel() >= level).isPresent();
    }


    /**
     * battleActors 전원의 names 로 받은 스테이터스의 레벨을 level 만큼 증가
     *
     * @param battleActors
     * @param level        증가량 (목표 값이 아님)
     * @param names        가변인자
     */
    public void addUniqueStatusLevelAll(List<BattleActor> battleActors, int level, String... names) {
        battleActors.forEach(battleActor -> addUniqueStatusLevel(battleActor, level, names));
    }

    /**
     * battleActor 의 names 로 받은 스테이터스의 레벨을 level 만큼 증가
     *
     * @param battleActor
     * @param level       증가량 (목표 값이 아님)
     * @param names       가변인자
     */
    public void addUniqueStatusLevel(BattleActor battleActor, int level, String... names) {
        List<String> statusNames = Arrays.stream(names).toList();
        battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> statusNames.stream()
                        .anyMatch(name -> name.equals(battleStatus.getStatus().getName()))
                ).forEach(battleStatus -> {
                    log.info("battleStatusinLEvelup = {}", battleStatus);
                    battleStatus.addLevel(level);
                });
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
