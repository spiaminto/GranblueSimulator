package com.gbf.granblue_simulator.battle.logic.actor.enemy;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusResult;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.DEAD_DEFAULT;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.STRIKE_SEALED;
import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;
import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getMaxValueEffectByModifierType;

/**
 * 모든 에너미로직 반환값은 null 을 사용하지 않는다.
 * 이 로직의 어빌리티 메서드 반환값이 유일한 null 리턴이며, 해당 메서드가 실행되는것은 오류임.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class EnemyLogic {

    protected final BattleContext battleContext;

    protected final EnemyLogicResultMapper resultMapper;

    protected final DamageLogic damageLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final OmenLogic omenLogic;

    protected final BattleLogService battleLogService;

    protected final BaseActorRepository baseActorRepository;

    // 필수 오버라이드
    // 전투 시작시 효과
    public abstract List<ActorLogicResult> processBattleStart();

    // 통상공격
    protected abstract ActorLogicResult attack();

    // 오의
    protected abstract ActorLogicResult chargeAttack();

    // 아군이 ~ 할때 효과 (적은 기본적으로 전조 처리가 있기 때문에 1개 이상 반환, 로직 작성시 전조처리를 우선할것)
    public abstract List<ActorLogicResult> postProcessToPartyMove(ActorLogicResult partyMoveResult);

    // 적이 ~ 할때 효과
    // 이 효과에 대해 아군의 반응이 구현되지 않았으므로 고려하여 작성
    public abstract List<ActorLogicResult> postProcessToEnemyMove(ActorLogicResult enemyMoveResult);

    // 턴 종료시 효과
    public abstract List<ActorLogicResult> processTurnEnd();

    // 턴 종료후 전조 발동
    public abstract List<ActorLogicResult> activateOmen();

    /**
     * 적은 self() 에서 Enemy 로 캐스팅 후 반환
     *
     * @return
     */
    protected Enemy self() {
        return (Enemy) battleContext.getEnemy();
    }

    protected Move selfMove(MoveType moveType) {
        return this.self().getMove(moveType);
    }

    /**
     * 공격 행동을 수행
     * 오의게이지와 관계없이 nextStandby 가 set 되어있는 경우에 한해 오의를 사용. 나머지는 전부 일반공격
     *
     * @return
     */
    public ActorLogicResult processStrike() {
        Actor mainActor = battleContext.getMainActor();
        // 공격행동 봉인시 즉시 반환
        ActorLogicResult sealedStrikeResult = getEffectByModifierType(mainActor, StatusModifierType.STRIKE_SEALED)
                .map(battleStatus -> resultMapper.toResult(Move.getTransientMove(STRIKE_SEALED), null, Collections.emptyList(), null))
                .orElseGet(() -> null);
        if (sealedStrikeResult != null) return sealedStrikeResult;
        // 공격행동 결정 및 수행
        Enemy mainEnemy = (Enemy) mainActor;
        MoveType currentStandbyType = mainEnemy.getCurrentStandbyType();
        return currentStandbyType != null ?
                chargeAttack() :
                attack();
    }

    // 어빌리티 수행
    public ActorLogicResult processAbility(MoveType moveType) {
        return switch (moveType) {
            case FIRST_ABILITY -> firstAbility();
            case SECOND_ABILITY -> secondAbility();
            case THIRD_ABILITY -> thirdAbility();
            case FOURTH_ABILITY -> fourthAbility();
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
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAttack() {
        // 평타 횟수 (독립시행)
        Move attackMove = selfMove(
                Math.random() < self().getStatus().getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                        Math.random() < self().getStatus().getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK :
                                MoveType.SINGLE_ATTACK);
        // 타겟 설정
        List<Actor> targets = this.getAttackTargets(attackMove.isAllTarget(), attackMove.getHitCount(), battleContext.getFrontCharacters());
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemyDamage(targets, attackMove);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(targets, damageLogicResult.getDamages(), attackMove.getType());

        return DefaultActorLogicResult.builder()
                .resultMove(attackMove).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).build();
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @return
     */
    protected DefaultActorLogicResult defaultChargeAttack() {
        return defaultChargeAttack(null);
    }

    /**
     * 기본적인 오의 처리 (배율 수정)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param modifiedDamageRate
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(Double modifiedDamageRate) {
        Move chargeAttack = selfMove(self().getCurrentStandbyType().getChargeAttackType());
        // 타겟설정
        List<Actor> targets = getAttackTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), battleContext.getFrontCharacters());
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processEnemyDamage(targets, chargeAttack, modifiedDamageRate);
        // 스테이터스 타겟 설정 (중복제거)
        List<Actor> statusTargets = targets.stream().distinct().toList();
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(chargeAttack, statusTargets);
        // 오의게이지
        chargeGaugeLogic.afterEnemyAttack(targets, damageLogicResult.getDamages(), chargeAttack.getType());
        // 스탠바이 초기화 CHECK 다른 로직에서 스탠바이 상태를 확인하므로 마지막에 변경할것
        self().setCurrentStandbyType(null);
        self().setNextIncantStandbyType(null);
        return DefaultActorLogicResult.builder()
                .resultMove(chargeAttack).damageLogicResult(damageLogicResult).enemyAttackTargets(targets).setStatusResult(setStatusResult).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     * 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability) {
        return this.defaultAbility(ability, null, null);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화 및 히트수 변화 있음)
     * 데미지 배율 및 히트수 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount   : 변경할 히트수 (기본 히트수 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        // 타겟설정
        List<Actor> targets = new ArrayList<>();
        DamageLogicResult damageLogicResult = null;
        List<Actor> statusTargets = new ArrayList<>();

        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        if (hitCount > 0) { // 대부분 데미지 없기때문에 필요없을시 바로 스킵
            targets = getAttackTargets(ability.isAllTarget(), ability.getHitCount(), battleContext.getFrontCharacters());
            // 데미지 배율 변경확인
            double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
            // 데미지 계산
            damageLogicResult = damageLogic.processEnemyDamage(battleContext.getFrontCharacters(), ability, damageRate);
            // 상태효과 타겟
            statusTargets = targets.stream().distinct().toList();
        }
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(ability, statusTargets);

        // 적은 쿨타임 x

        return DefaultActorLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusResult(setStatusResult).build();
    }

    /**
     * 기본적인 전조 갱신
     * 전조 상태확인 -> 전조 해제 값 계산 -> 전조 해제 결과값 enemy 에 set -> 브레이크 여부판단 -> 최종무브 리턴
     * 전조 연산 자체는 omenLogic 으로 넘기며, 전조 값 연산 결과가 0 인경우 브레이크를 아닌경우 전조를 그대로 반환
     *
     * @param otherResult
     * @return Result.MoveType.STANDBY_X 또는 Result.MoveType.BREAK_X 또는 발생중인 전조 없으면 null
     */
    protected DefaultActorLogicResult defaultOmen(ActorLogicResult otherResult) {
        MoveType currentStandbyType = self().getCurrentStandbyType();
        if (currentStandbyType == null) return null; // 발생중인 전조 없음

        OmenResult omenResult = null;
        Move resultMove = selfMove(currentStandbyType); // 기본적으로 현재 전조를 반환
        Omen currentOmen = resultMove.getOmen();
        // 전조 연산
        int processedOmenValue = omenLogic.updateOmenValue(self(), otherResult);
        if (processedOmenValue == 0) {
            // 전조 해제, 중단 (브레이크)
            self().setCurrentStandbyType(null);
            self().setNextIncantStandbyType(null);
            if (currentOmen.getOmenType() == OmenType.CHARGE_ATTACK) chargeGaugeLogic.setChargeGauge(self(), 0);

            resultMove = selfMove(currentStandbyType.getBreakType());
        } else {
            omenResult = OmenResult.from(self());
        }
        return DefaultActorLogicResult.builder().resultMove(resultMove).resultOmen(omenResult).build();
    }

    public ActorLogicResult defaultDead(Actor enemy) {
        // 상태 갱신
        enemy.updateHp(Integer.MIN_VALUE);
        self().setCurrentStandbyType(null); // 전조 해제
        
        // 컨텍스트, currentOrder 갱신 -> 하지않음, 어차피 강제종료
        
        // 결과 반환
        Move deadMove = enemy.getMove(DEAD_DEFAULT);
        return resultMapper.toResult(deadMove);
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
        List<Actor> targets = null;

        // 감싸기 효과 적용 확인
        targets = getMaxValueEffectByModifierType(partyMembers, StatusModifierType.SUBSTITUTE)
                .map(substituteEffect -> isAllTarget
                        ? Collections.nCopies(partyMembers.size() * hitCount, substituteEffect.getActor())  // 전체타겟인 경우 전원분 감싸기 id
                        : Collections.nCopies(hitCount, substituteEffect.getActor()) // 전체타겟 아닌경우 히트수만큼 감싸기 id
                ).orElse(null);
        if (targets != null) return targets;

        // 적대심 효과 확인 (미구현)

        // 기본 타겟
        return isAllTarget
                ? IntStream.range(0, hitCount).mapToObj(i -> partyMembers).flatMap(List::stream).collect(Collectors.toList())
                : IntStream.range(0, hitCount).mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size()))).collect(Collectors.toList());
    }

    // 가변 오버라이드 (내부사용)
    protected ActorLogicResult firstAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult firstSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult secondSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult thirdSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fourthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult fifthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult sixthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult seventhSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult eighthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult ninthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

    protected ActorLogicResult tenthSupportAbility() {
        log.warn("No Enemy Selected");
        return null;
    }

}
