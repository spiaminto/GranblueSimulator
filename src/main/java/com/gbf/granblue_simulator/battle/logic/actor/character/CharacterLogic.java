package com.gbf.granblue_simulator.battle.logic.actor.character;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.*;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.STRIKE_SEALED;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType.ALL_PARTY_MEMBERS;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType.*;

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
    public abstract List<ActorLogicResult> processBattleStart();

    // 통상공격
    protected abstract ActorLogicResult attack();

    // 오의
    protected abstract ActorLogicResult chargeAttack();

    // 아군이 ~ 할때 효과
    public abstract ActorLogicResult postProcessToPartyMove(ActorLogicResult partyMoveResult);

    // 적이 ~ 할때 효과
    public abstract ActorLogicResult postProcessToEnemyMove(ActorLogicResult enemyMoveResult);

    // 턴 종료시 효과
    public abstract List<ActorLogicResult> processTurnEnd();

    // 페이탈 체인
    public ActorLogicResult processFatalChain(Move fatalChain) {
        return defaultFatalChain(fatalChain);
    }

    // 헬퍼
    protected Actor self() {
        return battleContext.getMainActor();
    }

    protected Move selfMove(MoveType moveType) {
        return this.self().getMove(moveType);
    }


    /**
     * 공격 행동을 수행
     *
     * @return
     */
    public ActorLogicResult processStrike() {
        Actor mainActor = battleContext.getMainActor();
        // 가드시 반환
        if (mainActor.isGuardOn()) return defaultGuard();

        // 공격행동 봉인 여부 체크 및 반환
        double calcedStrikeSealed = mainActor.getStatus().getStatusDetails().getCalcedStrikeSealed();
        boolean isStrikeSealed = Math.random() < calcedStrikeSealed;
        if (isStrikeSealed) return resultMapper.toResult(Move.getTransientMove(STRIKE_SEALED), null, null);

        // 공격행동 결정 및 수행
        boolean readyChargeAttack = mainActor.getChargeGauge() >= mainActor.getBaseActor().getMaxChargeGauge();
        boolean chargeAttackSealed = mainActor.getStatus().getStatusDetails().getCalcedChargeAttackSealed();
        boolean isChargeAttackOn = mainActor.getMember().isChargeAttackOn(); // 오의 발동 on 여부
        ActorLogicResult strikeResult = isChargeAttackOn && readyChargeAttack && !chargeAttackSealed ? chargeAttack() : attack();

        mainActor.increaseExecutedStrikeCount();
        return strikeResult;
    }

    /**
     * 통상 공격을 수행
     *
     * @return
     */
    public ActorLogicResult processAttack() {
        return attack();
    }

    /**
     * 오의를 수행 (후행동)
     *
     * @return
     */
    public ActorLogicResult processChargeAttack() {
        return chargeAttack();
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
        Actor self = this.self();
        // 평타 횟수 (독립시행)
        Move attackMove = selfMove(
                Math.random() < self.getStatus().getTripleAttackRate() ? MoveType.TRIPLE_ATTACK :
                        Math.random() < self.getStatus().getDoubleAttackRate() ? MoveType.DOUBLE_ATTACK :
                                MoveType.SINGLE_ATTACK
        );
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(attackMove);
        // 오의게이지
        chargeGaugeLogic.afterAttack(attackMove.getType());
        return DefaultActorLogicResult.builder().resultMove(attackMove).damageLogicResult(damageLogicResult).build();
    }

    protected DefaultActorLogicResult defaultChargeAttack() {
        return defaultChargeAttack(null, null);
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param modifiedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(Double modifiedDamageRate) {
        return defaultChargeAttack(modifiedDamageRate, null);
    }

    /**
     * 기본적인 오의 처리 (스테이터스 선택식)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 선택된 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param modifiedDamageRate : 변경할 오의 배율 (기본배율 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultChargeAttack(Double modifiedDamageRate, List<BaseStatusEffect> selectedBaseStatusEffects) {
        Move chargeAttack = selfMove(CHARGE_ATTACK_DEFAULT);
        // 오의 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : chargeAttack.getDamageRate();
        // 스테이터스 변경 확인
        List<BaseStatusEffect> baseStatusEffects = selectedBaseStatusEffects != null ? selectedBaseStatusEffects : chargeAttack.getBaseStatusEffects();
        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(chargeAttack.getType(), chargeAttack.getElementType(), damageRate, chargeAttack.getHitCount());
        // 스테이터스 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(baseStatusEffects);
        // 오의게이지
        chargeGaugeLogic.afterAttack(chargeAttack.getType());
        // 오의 재발동
        boolean isMultiChargeAttack = getEffectByModifierType(self(), MULTI_CHARGE_ATTACK)
                .map(battleStatus -> {
                    setStatusLogic.removeStatusEffect(self(), battleStatus); // 오의 재발동 스테이터스 삭제
                    return true;
                }).orElseGet(() -> false);

        return DefaultActorLogicResult.builder().resultMove(chargeAttack).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).executeChargeAttack(isMultiChargeAttack).build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability) {
        return this.defaultAbility(ability, null, null, null);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (스테이터스 선택식)
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability, List<BaseStatusEffect> selectedBaseStatusEffects) {
        return this.defaultAbility(ability, null, null, selectedBaseStatusEffects);
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화, 히트수 변화 있음)
     *
     * @param modifiedDamageRate : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount   : 변경할 히트수 (기본 히트수 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability, Double modifiedDamageRate, Integer modifiedHitCount) {
        return this.defaultAbility(ability, modifiedDamageRate, modifiedHitCount, null);
    }


    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화, 히트수 변화, 스테이터스 변화 모두 있음)
     * 데미지 배율, 히트수, 스테이터스 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @param ability
     * @param modifiedDamageRate        : 변경할 어빌리티 배율 (기본배율 사용시 null)
     * @param modifiedHitCount          : 변경할 히트수 (기본 히트수 사용시 null)
     * @param selectedBaseStatusEffects : 변경(선택)할 스테이터스 (어빌리티의 기본 모든 스테이터스 사용시 null)
     * @return DefaultActorLogicResult
     */
    protected DefaultActorLogicResult defaultAbility(Move ability, Double modifiedDamageRate, Integer modifiedHitCount, List<BaseStatusEffect> selectedBaseStatusEffects) {
        // 데미지 배율 변경확인
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : ability.getDamageRate();
        // 히트수 변경 확인
        int hitCount = modifiedHitCount != null ? modifiedHitCount : ability.getHitCount();
        // 스테이터스 변경 확인
        List<BaseStatusEffect> baseStatusEffects = selectedBaseStatusEffects != null ? selectedBaseStatusEffects : ability.getBaseStatusEffects();
        // 데미지 계산
        MoveType abilityType = ability.getType();
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processPartyDamage(abilityType, ability.getElementType(), damageRate, hitCount) : null;
        // 스테이터스 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(baseStatusEffects);
        // 쿨다운, 사용횟수 설정 (커맨드 수행시에만 설정)
        if (abilityType.getParentType() == MoveType.ABILITY && self().getCommandType() == ability.getType()) {
            self().updateAbilityCooldowns(ability.getCoolDown(), abilityType);
            self().increaseAbilityUseCount(abilityType);
        }

        // 참전자 상태효과 확인
        if (self().getBaseActor().isLeaderCharacter()) { // 참전자 버프는 주인공만 제한적으로 사용
            List<BaseStatusEffect> forAllBaseStatusEffects = baseStatusEffects.stream()
                    .filter(baseStatusEffect -> baseStatusEffect.getTargetType() == ALL_PARTY_MEMBERS)
                    .toList(); // 참전자버프 + 일반버프 섞여있는경우도 있으므로 분리해서 등록
            if (!forAllBaseStatusEffects.isEmpty())
                registerForAllMove(ability);
        }

        return DefaultActorLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }

    /**
     * 참전자 효과 등록 <br>
     *
     * @param move 참전자 효과가 들어있는 move
     */
    public void registerForAllMove(Move move) {
        Member refMember = self().getMember();
        Room room = refMember.getRoom();
        List<Member> targetMembers = room.getMembers().stream()
                .filter(member -> !refMember.getId().equals(member.getId()))
                .toList();
        if (targetMembers.isEmpty()) return; // 동기화 대상 없음

        targetMembers.forEach(member -> {
            member.getPendingForAllMoves().add(Member.PendingForAllMove.builder()
                    .moveId(move.getId())
                    .sourceMemberId(member.getId())
                    .sourceUsername(member.getUser().getUsername())
                    .build());
            // move 단위로 저장해놓고, 플레이어 각각 필요한경우 사용 및 초기화
        });
    }

    /**
     * 페이탈 체인 처리 <br>
     * 캐릭터 concrete logic 을 타지 않고 여기서 처리 종결 <br>
     * battleContext.mainActor 을 주체로 실행하지만, 데미지 고정 + 디버프 필중이기 때문에 주체의 상태에 영향받지 않음
     *
     * @param fatalChain
     * @return
     */
    protected ActorLogicResult defaultFatalChain(Move fatalChain) {
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(fatalChain);
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(fatalChain);
        chargeGaugeLogic.setFatalChainGauge(0); // 페이탈 체인 게이지 초기화
        return resultMapper.toResult(fatalChain, damageLogicResult, setStatusEffectResult);
    }

    /**
     * 캐릭터 사망처리
     *
     * @param mainActor
     * @return MoveType.DEAD_DEFAULT, 불사 등의 상태효과로 사망처리가 취소됬을 시 MoveType.NONE
     */
    public ActorLogicResult defaultDead(Actor mainActor) {
        StatusEffect immortalStatus = getEffectByModifierType(mainActor, IMMORTAL).orElse(null);
        if (immortalStatus != null) {
            setStatusLogic.removeStatusEffect(mainActor, immortalStatus); // 불사효과 삭제
            mainActor.updateHp(1); // 체력 1
            return resultMapper.emptyResult(); // 안해도됨 (프론트처리 없음)
        } else {
            // 사망처리 -> currentOrder 로 뒤로 보내서 front 에서 제외시킴
            Integer deadActorCurrentOrder = mainActor.getCurrentOrder();
            mainActor.updateCurrentOrder(deadActorCurrentOrder + 100);
            // 체력 변경
            mainActor.updateHp(Integer.MIN_VALUE);
            // 서브멤버 존재시, 사망 캐릭터의 currentOrder 로 변경
            Actor firstSubCharacter = battleContext.getSubCharacters().stream().findFirst()
                    .map(firstSubMember -> {
                        firstSubMember.updateCurrentOrder(deadActorCurrentOrder);
                        return firstSubMember;
                    }).orElseGet(() -> null);
            // 컨텍스트 갱신
            battleContext.frontCharacterDead(mainActor, firstSubCharacter);
            // 결과 반환
            Move deadMove = mainActor.getMove(DEAD_DEFAULT);
            return resultMapper.toResult(deadMove, null, null);
        }
    }

    protected ActorLogicResult defaultGuard() {
        return resultMapper.toResult(Move.getTransientMove(GUARD_DEFAULT), null, null);
    }

    // 가변 오버라이드 (내부사용)
    protected ActorLogicResult firstAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult secondAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult thirdAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fourthAbility() {
        log.warn("No Character Selected");
        return null;
    }

    // 서포트 어빌리티는 로직 내부에서만 사용됨

    protected ActorLogicResult firstSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult secondSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult thirdSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fourthSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult fifthSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult sixthSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult seventhSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }

    protected ActorLogicResult eighthSupportAbility() {
        log.warn("No Character Selected");
        return null;
    }
}
