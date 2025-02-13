package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
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
     * Move.Statuses 의 각각의 statusEffect 를 Map<StatusEffectType, List<StatusEffect>> 로 변환 (플랫화)
     *
     * @param move
     * @return
     */
    public Map<StatusEffectType, List<StatusEffect>> getStatusEffectMap(Move move) {
        return move.getStatuses().stream()
                .flatMap(status -> status.getStatusEffects().stream())
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
                                statusEffect.getType() == StatusEffectType.ADDITIONAL_DAMAGE_U ||
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
     * BattleActor.battleStatuses 에서 동일한 id의 status 찾아 Optional 로 반환
     * 주의) id 가 아닌 status.name 으로 동일여부를 판단함. 따라서 status.statusEffect 는 다를수 있음.
     *
     * @param battleActor
     * @param status
     * @return Optional<BattleStatus>
     */
    public Optional<BattleStatus> getSameIdBattleStatus(BattleActor battleActor, Status status) {
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getId().equals(status.getId()))
                .findFirst();
    }

    /**
     * BattleActor.battleStatuses 에서 동일한 statusEffect.type 의 status 찾아 Optional 로 반환
     * 하나의 StatusEffect 로 구성된 Status 가 아닐경우 일단 반환하나, warn 로그 찍음 (정상상황 아님)
     *
     * @param battleActor
     * @param status
     * @return Optional<BattleStatus>
     */
    public Optional<BattleStatus> getSameEffectTypeStatus(BattleActor battleActor, Status status) {
        if (status.getStatusEffects().size() > 1) log.warn("Status 가 두개이상의 StatusEffect 로 구성됨, Status = {}", status);
        return battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getStatusEffects().getFirst().getType() == status.getStatusEffects().getFirst().getType())
                .findFirst();
    }

    /**
     * Status 의 StatusEffect 의 값 반환.
     * 단일 StatusEffect 로 이루어지지 않은경우 첫번째 StatusEffect 의 value 를 리턴하지만, 경고 로그를 띄움 (정상상황 아님)
     *
     * @param status
     * @return
     */
    public Double getStatusEffectValue(Status status) {
        if (status.getStatusEffects().size() > 1) {
            log.warn("statusEffect 가 두개 이상입니다. status = {}", status);
        }
        return status.getStatusEffects().getFirst().getValue();
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
