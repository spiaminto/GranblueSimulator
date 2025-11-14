package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.Enemy;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.omen.Omen;
import com.gbf.granblue_simulator.domain.base.omen.OmenType;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.OmenLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.BaseActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.domain.base.move.MoveType.STRIKE_SEALED;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getEffectByModifierType;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getMaxValueEffectByModifierType;

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

    protected final BaseActorRepository baseActorRepository;

    // 필수 오버라이드
    // 전투 시작시 효과
    public abstract List<ActorLogicResult> processBattleStart(Actor mainActor, List<Actor> partyMembers);

    // 통상공격
    protected abstract ActorLogicResult attack(Actor mainActor, List<Actor> partyMembers);

    // 오의
    protected abstract ActorLogicResult chargeAttack(Actor mainActor, List<Actor> partyMembers);

    // 아군이 ~ 할때 효과 (적은 기본적으로 전조 처리가 있기 때문에 1개 이상 반환, 로직 작성시 전조처리를 우선할것)
    public abstract List<ActorLogicResult> postProcessToPartyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult partyMoveResult);

    // 적이 ~ 할때 효과
    public abstract List<ActorLogicResult> postProcessToEnemyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult enemyMoveResult);

    // 턴 종료시 효과
    public abstract List<ActorLogicResult> processTurnEnd(Actor mainActor, List<Actor> partyMembers);

    // 턴 종료후 전조 발동
    public abstract List<ActorLogicResult> activateOmen(Actor mainActor, List<Actor> partyMembers);

    /**
     * 공격 행동을 수행
     * 오의게이지와 관계없이 nextStandby 가 set 되어있는 경우에 한해 오의를 사용. 나머지는 전부 일반공격
     *
     * @param mainActor
     * @param partyMembers
     * @return
     */
    public ActorLogicResult processStrike(Actor mainActor, List<Actor> partyMembers) {
        // 공격행동 봉인시 즉시 반환
        ActorLogicResult sealedStrikeResult = getEffectByModifierType(mainActor, StatusModifierType.STRIKE_SEALED)
                .map(battleStatus -> resultMapper.toResult(mainActor, partyMembers, Move.getTransientMove(STRIKE_SEALED), null, Collections.emptyList(), null))
                .orElseGet(() -> null);
        if (sealedStrikeResult != null) return sealedStrikeResult;
        // 공격행동 결정 및 수행
        Enemy mainEnemy = (Enemy) mainActor;
        MoveType currentStandbyType = mainEnemy.getCurrentStandbyType();
        return currentStandbyType != null ?
                chargeAttack(mainActor, partyMembers) :
                attack(mainActor, partyMembers);
    }

    // 어빌리티 수행
    public ActorLogicResult processAbility(Actor mainActor, List<Actor> partyMembers, MoveType moveType) {
        Move ability = mainActor.getBaseActor().getMoves().get(moveType);
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
    protected DefaultActorLogicResult defaultAttack(Actor mainActor, List<Actor> partyMembers) {
        // 평타 횟수 (독립시행)
        Move attackMove = mainActor.getBaseActor().getMoves().get(
                Math.random() < mainActor.getStatus().getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                        Math.random() < mainActor.getStatus().getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK : MoveType.SINGLE_ATTACK
        );
        // 타겟 설정
        List<Actor> targets = this.getAttackTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), attackMove.getType(), null);
        // 전체공격시 타겟 변경
        List<Actor> allTargetedTargets = new ArrayList<>();
        if (attackMove.isAllTarget()) {
            // 어빌리티 및 오의와 같이 전체공격시 타겟을 복제하여 설정. 데미지로직 구현상 그쪽에는 쓰지말것 (일반공격시, 타수에 맞게 데미지가 복제되므로 타겟만 후에 복제해야함)
            for (int i = 0; i < attackMove.getHitCount(); i++) allTargetedTargets.addAll(targets);
            targets = allTargetedTargets;
        }
        return DefaultActorLogicResult.builder()
                .resultMove(attackMove).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).build();
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param mainActor
     * @param partyMembers
     * @param standby
     * @return
     */
    protected DefaultActorLogicResult defaultChargeAttack(Actor mainActor, List<Actor> partyMembers, Move standby) {
        return defaultChargeAttack(mainActor, partyMembers, standby, mainActor.getBaseActor().getMoves().get(standby.getType().getChargeAttackType()), null);
    }

    /**
     * 기본적인 오의 처리 (배율 수정)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param mainActor
     * @param partyMembers
     * @param standby            오의(전조) 타입 조회를 위해 필요
     * @param chargeAttack
     * @param modifiedDamageRate
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(Actor mainActor, List<Actor> partyMembers, Move standby, Move chargeAttack, Double modifiedDamageRate) {
        // 타겟설정
        List<Actor> targets = getAttackTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), partyMembers);
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack, modifiedDamageRate);
        // 스테이터스 타겟 설정 (중복제거)
        List<Actor> statusTargets = targets.stream().distinct().toList();
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, statusTargets, chargeAttack);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), chargeAttack.getType(), standby.getOmen().getOmenType());
        // 스탠바이 초기화
        Enemy mainEnemy = (Enemy) mainActor;
        mainEnemy.setCurrentStandbyType(null);
        mainEnemy.setNextIncantStandbyType(null);
        return DefaultActorLogicResult.builder()
                .resultMove(chargeAttack).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).setStatusResult(setStatusResult).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     * 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param mainActor
     * @param partyMembers
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Actor mainActor, List<Actor> partyMembers, Move ability) {
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
    protected DefaultActorLogicResult defaultAbility(Actor mainActor, List<Actor> partyMembers, Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        // 데미지 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
        // 히트수 변경 확인
        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        // 데미지 계산
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processEnemy(mainActor, partyMembers, ability) :
                null;
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, partyMembers, ability.getStatusEffects());
        // 적은 어빌리티 및 쿨타임 존재 X
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
    protected DefaultActorLogicResult defaultOmen(Actor mainActor, ActorLogicResult otherResult) {
        Enemy mainEnemy = (Enemy) mainActor;
        if (mainEnemy.getCurrentStandbyType() == null)
            return DefaultActorLogicResult.builder().resultMove(null).build(); // 발생중인 전조 없음
        Move resultMove = null;
        Omen resultOmen = null;
        Move standby = mainEnemy.getBaseActor().getMoves().get(mainEnemy.getCurrentStandbyType());
        Omen standbyOmen = standby.getOmen();
        // 전조 연산
        int processedOmenValue = omenLogic.updateOmenValue(mainEnemy, otherResult);
        if (processedOmenValue == 0) {
            // 전조 중단 (브레이크)
            resultMove = mainEnemy.getBaseActor().getMoves().get(standby.getType().getBreakType());
            resultOmen = standby.getOmen();
            mainEnemy.setCurrentStandbyType(null);
            mainEnemy.setNextIncantStandbyType(null);
            if (standbyOmen.getOmenType() == OmenType.CHARGE_ATTACK) chargeGaugeLogic.setChargeGauge(mainActor, 0);
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
    protected List<Actor> getAttackTargets(boolean isAllTarget, int hitCount, List<Actor> partyMembers) {
        // 감싸기 효과 적용 확인
        Optional<StatusEffect> substituteEffect = getMaxValueEffectByModifierType(partyMembers, StatusModifierType.SUBSTITUTE);
        return substituteEffect
                .map(battleStatus -> isAllTarget
                        ? Collections.nCopies(partyMembers.size(), battleStatus.getActor())  // 전체타겟인 경우 전원분 감싸기 id
                        : Collections.nCopies(hitCount, battleStatus.getActor())) // 전체타겟 아닌경우 히트수만큼 감싸기 id
                .orElseGet(() -> isAllTarget
                        ? partyMembers
                        : IntStream.range(0, hitCount)
                        .mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size())))
                        .toList());
    }

    // 가변 오버라이드 (내부사용)
    protected ActorLogicResult firstAbility(Actor mainActor, List<Actor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondAbility(Actor mainActor, List<Actor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdAbility(Actor mainActor, List<Actor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthAbility(Actor mainActor, List<Actor> partyMembers, Move ability) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult firstSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fifthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult sixthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult seventhSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult eighthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult ninthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult tenthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        log.warn("No Enemy Selected");
        return null;
    }

}
