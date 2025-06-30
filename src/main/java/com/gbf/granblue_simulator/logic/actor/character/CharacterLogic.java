package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.domain.move.MoveType.*;

/**
 * 모든 캐릭터로직의 반환값은 null 을 사용하지 않는다.
 * 이 로직의 어빌리티 메서드 반환값이 유일한 null 리턴이며, 해당 메서드가 실행되는것은 오류임.
 */
@Slf4j
@Transactional
@RequiredArgsConstructor
public abstract class CharacterLogic {
    protected final CharacterLogicResultMapper resultMapper;
    protected final DamageLogic damageLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final SetStatusLogic setStatusLogic;

    // 필수 오버라이드
    // 전투 시작시 효과
    public abstract List<ActorLogicResult> processBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);
    // 통상공격
    protected abstract ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);
    // 오의
    protected abstract ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);
    // 아군이 ~ 할때 효과
    public abstract ActorLogicResult postProcessToPartyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult partyMoveResult);
    // 적이 ~ 할때 효과
    public abstract ActorLogicResult postProcessToEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult enemyMoveResult);
    // 턴 종료시 효과
    public abstract List<ActorLogicResult> processTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers);

    // 페이탈 체인 (어빌리티랑 통합 하려다 분리)
    public ActorLogicResult processFatalChain(BattleActor mainActor, BattleActor enemy, Move fatalChain) {
        return defaultFatalChain(mainActor, enemy, fatalChain);
    }

    /**
     * 공격 행동을 수행
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param moveType 공격 또는 오의 를 지정. 후행동으로 지정된 타입을 실행하려면 타입입력, 턴 진행시 자동으로 연산하려면 null
     * @return
     */
    public ActorLogicResult processAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, MoveType moveType) {
        boolean readyChargeAttack = mainActor.getChargeGauge() >= mainActor.getActor().getMaxChargeGauge();
        if (mainActor.isGuardOn()) return defaultGuard(mainActor, enemy, partyMembers);
        return moveType == MoveType.CHARGE_ATTACK_DEFAULT || readyChargeAttack ?
                chargeAttack(mainActor, enemy, partyMembers) :
                attack(mainActor, enemy, partyMembers);
    }

    /**
     * 통상 공격을 수행 (후행동)
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processNormalAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return attack(mainActor, enemy, partyMembers);
    }

    /**
     * 오의를 수행 (후행동)
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processChargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return chargeAttack(mainActor, enemy, partyMembers);
    }

    // 어빌리티 수행
    public ActorLogicResult processAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, MoveType moveType) {
        Move ability = mainActor.getActor().getMoves().get(moveType);
        return switch (moveType) {
            case FIRST_ABILITY -> firstAbility(mainActor, enemy, partyMembers, ability);
            case SECOND_ABILITY -> secondAbility(mainActor, enemy, partyMembers, ability);
            case THIRD_ABILITY -> thirdAbility(mainActor, enemy, partyMembers, ability);
            case FOURTH_ABILITY -> fourthAbility(mainActor, enemy, partyMembers, ability);
            default -> {
                log.warn("No Ability Selected");
                yield resultMapper.emptyResult();
            }
        };
    }

    /**
     * 기본적인 공격처리
     * 공격 행동 결정(평타횟수) -> 데미지 계산 -> 오의게이지 갱신
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 평타 횟수 (독립시행)
        Move attackMove = mainActor.getActor().getMoves().get(
                Math.random() < mainActor.getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                        Math.random() < mainActor.getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK : MoveType.SINGLE_ATTACK
        );
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, attackMove.getType());
        return DefaultActorLogicResult.builder().resultMove(attackMove).damageLogicResult(damageLogicResult).build();
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Double modifiedDamageRate) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT);
        // 오의 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : chargeAttack.getDamageRate();
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack.getType(), chargeAttack.getElementType(), damageRate, chargeAttack.getHitCount());
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, chargeAttack.getStatuses());
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, chargeAttack.getType());
        return DefaultActorLogicResult.builder().resultMove(chargeAttack).damageLogicResult(damageLogicResult).setStatusResult(setStatusResult).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     * 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        return this.defaultAbility(mainActor, enemy, partyMembers, ability, null, null);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화 및 히트수 변화 있음)
     * 데미지 배율 및 히트수 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount : 변경할 히트수 (기본 히트수 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        // 데미지 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
        // 히트수 변경 확인
        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        // 데미지 계산
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.process(mainActor, enemy, ability.getType(), ability.getElementType(), damageRate, hitCount) :
                null;
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses());
        // 쿨다운 설정
        MoveType abilityType = ability.getType();
        Integer coolDown = ability.getCoolDown();
        if (abilityType.getParentType() == MoveType.ABILITY) {
            switch (abilityType) {
                case FIRST_ABILITY -> mainActor.setFirstAbilityCoolDown(coolDown);
                case SECOND_ABILITY -> mainActor.setSecondAbilityCoolDown(coolDown);
                case THIRD_ABILITY -> mainActor.setThirdAbilityCoolDown(coolDown);
                case FOURTH_ABILITY -> mainActor.setFourthAbilityCoolDown(coolDown);
            }
        }
        return DefaultActorLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusResult(setStatusResult).build();
    }

    public ActorLogicResult defaultFatalChain(BattleActor mainActor, BattleActor enemy, Move fatalChain) {
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, fatalChain);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, Collections.emptyList(), fatalChain);
        mainActor.setFatalChainGauge(0); // 페이탈 체인 게이지 초기화
        return resultMapper.toResult(mainActor, enemy, Collections.emptyList(), fatalChain, damageLogicResult, setStatusResult);
    }

    protected ActorLogicResult defaultGuard(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return resultMapper.toResult(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(GUARD_DEFAULT), null, null);
    }

    // 가변 오버라이드 (내부사용)
    protected ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fourthAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fourthSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fifthSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult sixthSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult seventhSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult eighthSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Character Selected");
        return null;
    }
}
