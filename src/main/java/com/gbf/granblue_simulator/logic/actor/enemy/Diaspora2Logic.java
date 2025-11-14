package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.Enemy;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.BaseActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getEffectByName;

@Component
@Slf4j
public class Diaspora2Logic extends EnemyLogic {

    public Diaspora2Logic(EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, BaseActorRepository baseActorRepository) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, baseActorRepository);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(Actor mainActor, List<Actor> partyMembers) {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack(Actor mainActor, List<Actor> partyMembers) {
        DefaultActorLogicResult attackResult = defaultAttack(mainActor, partyMembers);
        List<Integer> targetOrders = attackResult.getEnemyAttackTargets().stream().map(Actor::getCurrentOrder).toList();
        return resultMapper.attackToResult(mainActor, partyMembers, attackResult.getResultMove(), attackResult.getDamageLogicResult(), targetOrders);
    }

    @Override
    public ActorLogicResult chargeAttack(Actor mainActor, List<Actor> partyMembers) {
        Enemy mainEnemy = (Enemy) mainActor;
        Move standby = mainActor.getBaseActor().getMoves().get(mainEnemy.getCurrentStandbyType());
        Move chargeAttack = mainActor.getBaseActor().getMoves().get(mainEnemy.getCurrentStandbyType().getChargeAttackType());
        Double damageRate =
                chargeAttack.getType() == MoveType.CHARGE_ATTACK_B ? getChargeAttackBDamageRate(mainActor) : // 허수몽핵
                        chargeAttack.getType() == MoveType.CHARGE_ATTACK_D ? getChargeAttackDDamageRate(mainActor) : // 인자방출
                                chargeAttack.getDamageRate(); // 기본배율
        DefaultActorLogicResult chargeAttackResult = defaultChargeAttack(mainActor, partyMembers, standby, chargeAttack, damageRate);
        List<Integer> targetOrders = chargeAttackResult.getEnemyAttackTargets().stream().map(Actor::getCurrentOrder).toList();
        return resultMapper.toResult(mainActor, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), targetOrders, chargeAttackResult.getSetStatusResult());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult otherResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(mainActor, otherResult);
        if (omenResult.getResultMove() != null) {
            results.add(resultMapper.toResultWithOmen(mainActor, partyMembers, omenResult.getResultMove(), omenResult.getResultOmen()));
        }

        return results;
    }

    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult enemyResult) {
        // 자신의 이성임계가 해제됬을시 서포어비 2 발동 -> 임계도달 레벨 감소
        if (enemyResult.getMoveType() == MoveType.BREAK_C) {
            return List.of(secondSupportAbility(mainActor, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY), enemyResult));
        }

        // 인자 방출 발동시 자괴인자 레벨에 따라 가드불가 연장, 자괴인자 삭제 (결과 반환 없음)
        if (enemyResult.getMoveType() == MoveType.CHARGE_ATTACK_D) {
            afterChargeAttackD(mainActor, partyMembers);
        }

        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(Actor mainActor, List<Actor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 서포어비 1
        results.add(firstSupportAbility(mainActor, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY), null));

        return results;
    }

    @Override
    public List<ActorLogicResult> activateOmen(Actor mainActor, List<Actor> partyMembers) {
        Enemy enemy = (Enemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();

        // 5턴마다 영창기 이성임계 발동
        if ((mainActor.getMember().getCurrentTurn() + 1) % 5 == 0)
            setStandbyCEveryFiveTurn(mainActor);

        // 전조발생
        omenLogic.triggerOmen(enemy).ifPresent(standby -> {
            if (standby.getType() == MoveType.STANDBY_B) {
                omenLogic.updateOmenValue(enemy, getStandbyBOmenInitValue(enemy)); // 초기값 수정필요한 경우 수정
            }
            results.add(resultMapper.toResultWithOmen(enemy, partyMembers, standby, standby.getOmen()));
        });

        return results;
    }

    @Override // 자신의 모드에 따라 받은 데미지의 누적값이 일정 수치에 도달할경우 턴 종료시 (자신의 임계상태 레벨 상승 및) 공격력 증가, 재공격 효과
    protected ActorLogicResult firstSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        return StatusUtil.getEffectByName(mainActor, "모드 『")
                .flatMap(matchedStatusEffect ->
                        StatusUtil.getEffectByName(mainActor, "임계 상태").map(onLimitStatusEffect -> {
                            // 모드에 따른 데미지 합 유형 확인
                            String matchedStatusEffectName = matchedStatusEffect.getBaseStatusEffect().getName();
                            MoveType getDamageSumMovetype = matchedStatusEffectName.contains("알파") ? MoveType.ATTACK
                                    : matchedStatusEffectName.contains("베타") ? MoveType.ABILITY
                                    : matchedStatusEffectName.contains("감마") ? MoveType.CHARGE_ATTACK : null;
                            if (getDamageSumMovetype == null) return resultMapper.emptyResult();

                            // 받은 데미지 누적값 확인 및 상승 레벨 계산
                            Integer takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(mainActor, getDamageSumMovetype);
                            int addLevel = takenDamageSum / 30000 + 1 - onLimitStatusEffect.getLevel(); // 모드레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
                            log.info("[firstSupportAbility] addLevel = {}, currentLevel = {}, takenDamageSum = {}", addLevel, onLimitStatusEffect.getLevel(), takenDamageSum);
                            if (addLevel <= 0) return resultMapper.emptyResult();

                            // 임계상태 레벨 상승 (내부연산, 결과 반환 x) - CHECK 불가능 하진 않지만 한 행동이 레벨을 2회 올릴수도 있으나, 일단 이대로 킵, 모드 레벨은 StatusModifier 가 달려있으므로 임계상태 레벨을 올리도록 설정
                            setStatusLogic.addStatusEffectLevel(mainActor, addLevel, onLimitStatusEffect);

                            // 가하는 데미지 상승 및 재공격 적용 (결과 반환 ㅇ)
                            SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, partyMembers, ability);
                            return resultMapper.toResultWithEffect(mainActor, partyMembers, ability, null, null, setStatusResult, true, false);
                        })
                ).orElseGet(resultMapper::emptyResult);
    }

    @Override // 자신의 전조 이성임계(STANDBY_C)가 해제될 경우 자신의 임계 도달 레벨 감소
    protected ActorLogicResult secondSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        SetStatusResult setStatusResult = getEffectByName(mainActor, "임계 도달")
                .map(battleStatus -> setStatusLogic.subtractStatusEffectLevel(mainActor, 1, battleStatus))
                .orElse(null);
        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    // 기타 표시되지 않는 개인 로직 ======================================================================

    /**
     * 턴 종료시 5의 배수턴마다 이성임계가 발동 (스테이터스로 표시)
     *
     * @param mainActor
     */
    protected void setStandbyCEveryFiveTurn(Actor mainActor) {
        Enemy battleEnemy = (Enemy) mainActor;
        if (battleEnemy.getNextIncantStandbyType() == null) // STANDBY_D 가 더 우선
            battleEnemy.setNextIncantStandbyType(MoveType.STANDBY_C);
    }

    /**
     * 허수몽핵 (STANDBY_B) 의 경우 임계 도달 레벨에 비례해 해제조건 강화
     *
     * @param mainActor
     * @return
     */
    protected int getStandbyBOmenInitValue(Actor mainActor) {
        return getEffectByName(mainActor, "임계 도달").map(
                battleStatus -> 2500000 * (1 + battleStatus.getLevel())
        ).orElse(2500000);
    }

    /**
     * 허수몽핵 (CHARGE_ATTACK_B) 의 경우 임계 도달 레벨에 비례해 데미지 배율 강화
     *
     * @param mainActor
     * @return
     */
    protected double getChargeAttackBDamageRate(Actor mainActor) {
        return getEffectByName(mainActor, "임계 도달").map(
                battleStatus -> 10.0 * (1 + battleStatus.getLevel())
        ).orElse(10.0);
    }

    /**
     * 인자방출 (CHARGE_ATTACK_D) 의 경우 자괴인자 레벨에 비례해 데미지 배율 강화
     *
     * @param mainActor
     * @return
     */
    protected double getChargeAttackDDamageRate(Actor mainActor) {
        return getEffectByName(mainActor, "자괴인자").map(
                battleStatus -> 5.0 * (1 + battleStatus.getLevel())
        ).orElse(5.0);
    }

    /**
     * 인자방출 후 자신의 자괴인자를 삭제, 자괴인자 레벨에 비례해 파티멤버의 가드불가 효과 연장
     *
     * @param mainActor
     * @param partyMembers
     * @return
     */
    protected void afterChargeAttackD(Actor mainActor, List<Actor> partyMembers) {
        getEffectByName(mainActor, "자괴인자").ifPresent(
                battleStatus -> {
                    // 효과 연장
                    partyMembers.forEach(
                            partyMember -> getEffectByName(partyMember, "가드불가").ifPresent(
                                    guardDisabledStatus -> setStatusLogic.extendStatusEffectDuration(guardDisabledStatus, battleStatus.getLevel() * 2)
                            )
                    );
                    // 자괴 인자 삭제
                    setStatusLogic.removeStatusEffect(mainActor, battleStatus);
                }
        );


    }
}

