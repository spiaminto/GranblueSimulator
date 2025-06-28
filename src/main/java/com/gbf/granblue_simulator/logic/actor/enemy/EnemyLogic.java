package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getEffectiveCoveringEffect;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.hasBattleStatus;

/**
 * 모든 에너미로직 반환값은 null 을 사용하지 않는다.
 * 이 로직의 어빌리티 메서드 반환값이 유일한 null 리턴이며, 해당 메서드가 실행되는것은 오류임.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class EnemyLogic {

    protected final EnemyLogicResultMapper resultMapper;

    protected final DamageLogic damageLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final OmenLogic omenLogic;

    protected final BattleLogService battleLogService;

    protected final ActorRepository actorRepository;

    // 필수 오버라이드
    // 전투 시작시 효과
    public abstract List<ActorLogicResult> processBattleStart(BattleActor mainActor, List<BattleActor> partyMembers);

    // 통상공격
    protected abstract ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers);

    // 오의
    protected abstract ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers);

    // 아군이 ~ 할때 효과 (적은 기본적으로 전조 처리가 있기 때문에 1개 이상 반환, 로직 작성시 전조처리를 우선할것)
    public abstract List<ActorLogicResult> postProcessToPartyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult partyMoveResult);

    // 적이 ~ 할때 효과
    public abstract List<ActorLogicResult> postProcessToEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult enemyMoveResult);

    // 턴 종료시 효과
    public abstract List<ActorLogicResult> processTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers);

    /**
     * 공격 행동을 수행
     * 적은 후행동으로 오의를 사용할 수 없기 때문에 일반공격 - 일반공격 / 오의 - 일반공격 2종류만 존재
     * 오의게이지와 관계없이 nextStandby 가 set 되어있는 경우에 한해 오의를 사용. 나머지는 전부 일반공격
     *
     * @param mainActor
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        MoveType currentStandbyType = mainEnemy.getCurrentStandbyType();
        return currentStandbyType != null ?
                chargeAttack(mainActor, partyMembers) :
                attack(mainActor, partyMembers);
    }

    // 어빌리티 수행
    public ActorLogicResult processAbility(BattleActor mainActor, List<BattleActor> partyMembers, MoveType moveType) {
        Move ability = mainActor.getActor().getMoves().get(moveType);
        return switch (moveType) {
            case FIRST_ABILITY -> firstAbility(mainActor, partyMembers, ability);
            case SECOND_ABILITY -> secondAbility(mainActor, partyMembers, ability);
            case THIRD_ABILITY -> thirdAbility(mainActor, partyMembers, ability);
            case FOURTH_ABILITY -> fourthAbility(mainActor, partyMembers, ability);
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
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        // 평타 횟수 (독립시행)
        Move attackMove = mainActor.getActor().getMoves().get(
                Math.random() < mainActor.getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                        Math.random() < mainActor.getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK : MoveType.SINGLE_ATTACK
        );
        // 타겟 설정
        List<BattleActor> targets = this.getAttackTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), attackMove.getType(), null);
        // 후행동 확인
        MoveType nextMoveType = hasBattleStatus(mainActor, "재공격") ? MoveType.ATTACK : null;
        return DefaultActorLogicResult.builder().resultMove(attackMove).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).nextMoveType(nextMoveType).build();
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     * @param mainActor
     * @param partyMembers
     * @param standby
     * @return
     */
    protected DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, List<BattleActor> partyMembers, Move standby) {
        return defaultChargeAttack(mainActor, partyMembers, standby, mainActor.getActor().getMoves().get(standby.getType().getChargeAttackType()), null);
    }

    /**
     * 기본적인 오의 처리 (배율 수정)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param mainActor
     * @param partyMembers
     * @param standby 오의(전조) 타입 조회를 위해 필요
     * @param chargeAttack
     * @param modifiedDamageRate
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(BattleActor mainActor, List<BattleActor> partyMembers, Move standby, Move chargeAttack, Double modifiedDamageRate) {
        // 타겟설정
        List<BattleActor> targets = getAttackTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), partyMembers);
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack, modifiedDamageRate);
        // 스테이터스 타겟 설정 (중복제거)
        List<BattleActor> statusTargets = targets.stream().distinct().toList();
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, statusTargets, chargeAttack);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), chargeAttack.getType(), standby.getOmen().getOmenType());
        // 스탠바이 초기화
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        mainEnemy.setCurrentStandbyType(null);
        mainEnemy.setNextIncantStandbyType(null);
        // 후행동 확인 (적은 후행동 통상공격만)
        MoveType nextMoveType = hasBattleStatus(mainActor, "재공격") ? MoveType.ATTACK : null;
        return DefaultActorLogicResult.builder().resultMove(chargeAttack).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).setStatusResult(setStatusResult).nextMoveType(nextMoveType).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     * 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param mainActor
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability) {
        return this.defaultAbility(mainActor, partyMembers, ability, null, null);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화 및 히트수 변화 있음)
     * 데미지 배율 및 히트수 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param mainActor
     * @param partyMembers
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount   : 변경할 히트수 (기본 히트수 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        // 데미지 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
        // 히트수 변경 확인
        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        // 데미지 계산
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processEnemy(mainActor, partyMembers, ability) :
                null;
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, ability.getStatuses());
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

    /**
     * 기본적인 전조 갱신
     * 전조 상태확인 -> 전조 해제 값 계산 -> 전조 해제 결과값 enemy 에 set -> 브레이크 여부판단 -> 최종무브 리턴
     * 전조 연산 자체는 omenLogic 으로 넘기며, 전조 값 연산 결과가 0 인경우 브레이크를 아닌경우 전조를 그대로 반환
     *
     * @param mainActor
     * @param otherResult
     * @return 전조 또는 브레이크 (발생중인 전조 없으면 null)
     */
    protected DefaultActorLogicResult defaultOmen(BattleActor mainActor, ActorLogicResult otherResult) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        if (mainEnemy.getCurrentStandbyType() == null)
            return DefaultActorLogicResult.builder().resultMove(null).build(); // 발생중인 전조 없음
        Move resultMove = null;
        Omen resultOmen = null;
        Move standby = mainEnemy.getActor().getMoves().get(mainEnemy.getCurrentStandbyType());
        Omen standbyOmen = standby.getOmen();
        // 전조 연산
        Integer processedOmenValue = omenLogic.processOmen(mainEnemy, otherResult);
        if (processedOmenValue == 0) {
            // 전조 중단 (브레이크)
            resultMove = mainEnemy.getActor().getMoves().get(standby.getType().getBreakType());
            resultOmen = standby.getOmen();
            mainEnemy.setCurrentStandbyType(null);
            mainEnemy.setNextIncantStandbyType(null);
            if (standbyOmen.getOmenType() == OmenType.CHARGE_ATTACK) mainEnemy.setChargeGauge(0);
        } else {
            resultMove = standby;
            resultOmen = standby.getOmen();
        }
        return DefaultActorLogicResult.builder().resultMove(resultMove).resultOmen(resultOmen).build();
    }

    /**
     * 보스의 공격 타겟 결정후 반환 (전체공격의 경우 partyMembers 그대로 사용하면 됨)
     * 적용효과 : 감싸기
     *
     * @param hitCount
     * @param partyMembers
     * @return
     */
    protected List<BattleActor> getAttackTargets(boolean isAllTarget, int hitCount, List<BattleActor> partyMembers) {
        // 감싸기 효과 적용 확인
        Optional<BattleStatus> substituteEffect = getEffectiveCoveringEffect(partyMembers, StatusEffectType.SUBSTITUTE);
        return substituteEffect
                .map(battleStatus -> isAllTarget ?
                        Collections.nCopies(partyMembers.size(), battleStatus.getBattleActor()) : // 전체타겟인 경우 전원분 감싸기 id
                        Collections.nCopies(hitCount, battleStatus.getBattleActor())) // 전체타겟 아닌경우 히트수만큼 감싸기 id
                .orElseGet(() -> isAllTarget ?
                        partyMembers :
                        IntStream.range(0, hitCount)
                                .mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size())))
                                .toList());
    }

    // 가변 오버라이드 (내부사용)
    protected ActorLogicResult firstAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fifthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult sixthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult seventhSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult eighthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult ninthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult tenthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

}
