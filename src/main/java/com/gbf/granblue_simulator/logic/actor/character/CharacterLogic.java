package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleContext;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.domain.move.MoveType.*;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getBattleStatusByEffectType;

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
    protected final BattleContext battleContext;

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
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processStrike(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 공격행동 봉인시 즉시 반환
        ActorLogicResult sealedStrikeResult = getBattleStatusByEffectType(mainActor, StatusEffectType.STRIKE_SEALED)
                .map(battleStatus -> resultMapper.toResult(mainActor, enemy, partyMembers, Move.getTransientMove(STRIKE_SEALED), null, null))
                .orElseGet(() -> null);
        if (sealedStrikeResult != null) return sealedStrikeResult;
        // 공격행동 결정 및 수행
        boolean readyChargeAttack = mainActor.getChargeGauge() >= mainActor.getActor().getMaxChargeGauge();
        boolean chargeAttackSealed = getBattleStatusByEffectType(mainActor, StatusEffectType.CHARGE_ATTACK_SEALED).isPresent();
        boolean isChargeAttackOn = mainActor.getMember().isChargeAttackOn(); // 오의 발동 on 여부
        if (mainActor.isGuardOn()) return defaultGuard(mainActor, enemy, partyMembers);
        mainActor.increaseStrikeCount(); // 공격행동 횟수 증가, 가드와 턴진행없이 일반공격에서는 이 카운트를 건드리지 않음
        return isChargeAttackOn && readyChargeAttack && !chargeAttackSealed ?
                chargeAttack(mainActor, enemy, partyMembers) :
                attack(mainActor, enemy, partyMembers);
    }

    /**
     * 통상 공격을 수행
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return attack(mainActor, enemy, partyMembers);
    }

    /**
     * 오의를 수행 (후행동)
     *
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
     *
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
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Double modifiedDamageRate) {
        return defaultChargeAttack(mainActor, enemy, partyMembers, modifiedDamageRate, null);
    }

    /**
     * 기본적인 오의 처리 (스테이터스 선택식)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 선택된 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Double modifiedDamageRate, List<Status> selectedStatuses) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT);
        // 오의 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : chargeAttack.getDamageRate();
        // 스테이터스 변경 확인
        List<Status> statuses = selectedStatuses != null ? selectedStatuses : chargeAttack.getStatuses();
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack.getType(), chargeAttack.getElementType(), damageRate, chargeAttack.getHitCount());
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, statuses);
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, chargeAttack.getType());
        // 오의 재발동
        boolean isMultiChargeAttack = getBattleStatusByEffectType(mainActor, StatusEffectType.MULTI_CHARGE_ATTACK)
                .map(battleStatus -> {
                    setStatusLogic.removeBattleStatus(mainActor, battleStatus); // 오의 재발동 스테이터스 삭제
                    return true;
                }).orElseGet(() -> false);

        return DefaultActorLogicResult.builder().resultMove(chargeAttack).damageLogicResult(damageLogicResult).setStatusResult(setStatusResult).executeChargeAttack(isMultiChargeAttack).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        return this.defaultAbility(mainActor, enemy, partyMembers, ability, null, null, null);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (스테이터스 선택식)
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability, List<Status> selectedStatuses) {
        return this.defaultAbility(mainActor, enemy, partyMembers, ability, null, null, selectedStatuses);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화, 히트수 변화 있음)
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount   : 변경할 히트수 (기본 히트수 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        return this.defaultAbility(mainActor, enemy, partyMembers, ability, modifiedDamageRate, modifiedHitCount, null);
    }


    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화, 히트수 변화, 스테이터스 변화 모두 있음)
     * 데미지 배율, 히트수, 스테이터스 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param ability
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount   : 변경할 히트수 (기본 히트수 사용시 null)
     * @param selectedStatuses   : 변경(선택)할 스테이터스 (어빌리티의 기본 모든 스테이터스 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability, Double modifiedDamageRate, Integer modifiedHitCount, List<Status> selectedStatuses) {
        // 데미지 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
        // 히트수 변경 확인
        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        // 스테이터스 변경 확인
        List<Status> statuses = selectedStatuses != null ? selectedStatuses : ability.getStatuses();
        // 데미지 계산
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.process(mainActor, enemy, ability.getType(), ability.getElementType(), damageRate, hitCount) :
                null;
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, statuses);
        // 쿨다운, 사용횟수 설정
        if (ability.getType().getParentType() == MoveType.ABILITY) {
            mainActor.updateAbilityCoolDown(ability.getCoolDown(), ability.getType());
            mainActor.increaseAbilityUseCount(ability.getType());
        }
        return DefaultActorLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusResult(setStatusResult).build();
    }

    public ActorLogicResult defaultFatalChain(BattleActor mainActor, BattleActor enemy, Move fatalChain) {
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, fatalChain);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, Collections.emptyList(), fatalChain);
        chargeGaugeLogic.setFatalChainGauge(mainActor, 0); // 페이탈 체인 게이지 초기화
        return resultMapper.toResult(mainActor, enemy, Collections.emptyList(), fatalChain, damageLogicResult, setStatusResult);
    }

    public ActorLogicResult defaultDeath(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult result) {
        BattleStatus immortalStatus = getBattleStatusByEffectType(mainActor, StatusEffectType.IMMORTAL).orElse(null);
        if (immortalStatus != null) {
            setStatusLogic.removeBattleStatus(mainActor, immortalStatus); // 불사효과 삭제
            mainActor.updateHp(1); // 체력 1
            return resultMapper.emptyResult(); // 안해도됨 (프론트처리 없음)
        } else {
            // 사망처리 -> currentOrder 로 뒤로 보내서 front 에서 제외시킴
            Integer deadActorCurrentOrder = mainActor.getCurrentOrder();
            mainActor.updateCurrentOrder(deadActorCurrentOrder + 100);
            // 서브멤버 존재시, 사망 캐릭터의 currentOrder 로 변경
            BattleActor firstSubCharacter = battleContext.getSubCharacters().stream().findFirst()
                    .map(firstSubMember -> {
                        firstSubMember.updateCurrentOrder(deadActorCurrentOrder);
                        return firstSubMember;
                    }).orElseGet(() -> null);
            // 컨텍스트 갱신
            battleContext.frontCharacterDead(mainActor, firstSubCharacter);
            // 결과 반환
            Move deadMove = mainActor.getMove(DEAD_DEFAULT);
            List<BattleActor> tempPartyMembers = new ArrayList<>(partyMembers); // 파티멤버의 갱신은 battleLogic 에서 하기 위해 일단 이렇게
            tempPartyMembers.remove(mainActor);
            return resultMapper.toResult(mainActor, enemy, tempPartyMembers, deadMove, null, null);
        }
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
