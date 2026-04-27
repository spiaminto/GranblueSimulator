package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.WeaponStatus;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
@Slf4j
public class StatusService {

    /**
     * base 를 기준으로 엔티티 생성시 초기값 저장
     */
    public Status init(Actor actor) {
        Status status = Status.builder().build().init(actor);
        this.syncStatus(actor);
        return status;
    }

    /**
     * 정보만 읽어 반환해야할때 사용. 읽기전용으로 상세 상태를 초기화함.
     */
    @Transactional(readOnly = true)
    public void initStatusForRead(List<Actor> actors) {
        for (Actor actor : actors) {
            Status status = actor.getStatus();

            Actor enemy = actor.getMember().getActors().stream().filter(Actor::isEnemy).findAny().orElseThrow(() -> new MoveProcessingException("지정된 적이 없습니다."));

            WeaponStatus weaponStatus = actor.isEnemy() ? enemyWeaponStatus : weaponStatusMap.get(enemy.getBaseActor().getId());
            if (weaponStatus == null) throw new MoveProcessingException("스테이터스를 불러오는데 문제가 발생했습니다.");

            StatusDetails statusDetails = status.getStatusDetails();
            if (statusDetails == null) {
                statusDetails = StatusDetails.init(actor, weaponStatus);
                status.updateStatusDetails(statusDetails);
            }
            statusDetails.syncStatusDetails(actor);

            DamageStatusDetails damageStatusDetails = status.getDamageStatusDetails();
            if (damageStatusDetails == null) {
                damageStatusDetails = DamageStatusDetails.init(actor, weaponStatus);
                status.updateDamageStatusDetails(damageStatusDetails);
            }
            damageStatusDetails.syncDamageStatusDetails(actor);
        }
    }

    /**
     * 스테이터스 갱신 (재계산)
     */
    public void syncStatus(Actor actor) {
        Status status = actor.getStatus();

        Actor enemy = actor.getMember().getActors().stream().filter(Actor::isEnemy).findAny().orElseThrow(() -> new MoveProcessingException("지정된 적이 없습니다."));

        WeaponStatus weaponStatus = actor.isEnemy() ? enemyWeaponStatus : weaponStatusMap.get(enemy.getBaseActor().getId());
        if (weaponStatus == null) throw new MoveProcessingException("스테이터스를 불러오는데 문제가 발생했습니다.");

        // 1. 스테이터스 상세 초기화 및 동기화
        StatusDetails statusDetails = status.getStatusDetails();
        if (statusDetails == null) {
            statusDetails = StatusDetails.init(actor, weaponStatus);
            status.updateStatusDetails(statusDetails);

            if (status.getHp() == -1) {
                status.initHp(statusDetails.getCalcedMaxHp()); // 엔티티 첫 생성의 경우 HP 를 선 초기화
            }
        }
        statusDetails.syncStatusDetails(actor);

        // 2. 데미지 스테이터스 상세 초기화 및 동기화
        DamageStatusDetails damageStatusDetails = status.getDamageStatusDetails();
        if (damageStatusDetails == null) {
            damageStatusDetails = DamageStatusDetails.init(actor, weaponStatus);
            status.updateDamageStatusDetails(damageStatusDetails);
        }
        damageStatusDetails.syncDamageStatusDetails(actor);

        // 3. 최종 스테이터스 동기화
        status.syncStatus();
    }

    private static final WeaponStatus enemyWeaponStatus = WeaponStatus.builder()
            .weaponAtkUpRate(0)
            .weaponAtkUpUniqueRate(0)
            .weaponMaxHpUpRate(0)

            .weaponDamageCapUpRate(0)
            .weaponNormalAttackDamageCapUpRate(0)
            .weaponAbilityDamageCapUpRate(0)
            .weaponChargeAttackDamageCapUpRate(0)
            .weaponSupplementalDamage(0)
            .weaponSeraphicAmplifyDamageRate(0)
            .build();

    private static final WeaponStatus diasporaWeaponStatus = WeaponStatus.builder()
            .weaponAtkUpRate(27.0)
            .weaponAtkUpUniqueRate(0.5)
            .weaponMaxHpUpRate(3.35)

            .weaponDamageCapUpRate(0.1)
            .weaponNormalAttackDamageCapUpRate(0.1)
            .weaponAbilityDamageCapUpRate(0.5)
            .weaponChargeAttackDamageCapUpRate(0.15)
            .weaponSupplementalDamage(5000)
            .weaponSeraphicAmplifyDamageRate(0)
            .build();

    private static final WeaponStatus hexachromaticWeaponStatus = WeaponStatus.builder()
            .weaponAtkUpRate(30.0)
            .weaponAtkUpUniqueRate(0.5)
            .weaponMaxHpUpRate(3.45)

            .weaponDamageCapUpRate(0.15)
            .weaponNormalAttackDamageCapUpRate(0.1)
            .weaponAbilityDamageCapUpRate(0.5)
            .weaponChargeAttackDamageCapUpRate(0.15)
            .weaponSupplementalDamage(10000)
            .weaponSeraphicAmplifyDamageRate(0)
            .build();

    private static final Map<Long, WeaponStatus> weaponStatusMap = Map.of(
            10000L, diasporaWeaponStatus,
            10100L, diasporaWeaponStatus,
            10200L, diasporaWeaponStatus,
            10300L, hexachromaticWeaponStatus,
            10400L, hexachromaticWeaponStatus,
            10500L, hexachromaticWeaponStatus,
            10600L, hexachromaticWeaponStatus
    );

}
