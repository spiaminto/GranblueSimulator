package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.BattleContext;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.StatusEffectDto;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@Slf4j
@Transactional
public class IndaraLogic extends CharacterLogic {

    public IndaraLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override // 사포아비 1
    public List<ActorLogicResult> processBattleStart(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        return List.of(firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.FIRST_SUPPORT_ABILITY)));
    }

    @Override
    protected ActorLogicResult attack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        DefaultActorLogicResult defaultAttackResult = defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultAttackResult.getResultMove(), defaultAttackResult.getDamageLogicResult());
    }

    @Override // 1아비 2아비 즉시 사용가능, 사문레벨 증가, 사문레벨 5일때 불휴활기 효과
    protected ActorLogicResult chargeAttack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        // 기본 오의처리 -> 사문효과만 적용
        List<BaseStatusEffect> selectedBaseStatusEffects = List.of(getBaseEffectByName(mainActor, MoveType.CHARGE_ATTACK_DEFAULT, "사문"));
        DefaultActorLogicResult defaultResult = defaultChargeAttack(mainActor, enemy, partyMembers, null, selectedBaseStatusEffects);
        // 사문 레벨이 5인경우 삭제후 불휴활기
        getEffectByName(mainActor, "사문")
                .filter(battleStatus -> battleStatus.getLevel() >= 5)
                .ifPresent(battleStatus -> {
                    ActorLogicResult firstSupportAbilityResult = firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.FIRST_SUPPORT_ABILITY));
                    List<StatusEffectDto> firstSupportAbilityAddedStatusMe = firstSupportAbilityResult.getAddedStatusEffectsList().get(mainActor.getCurrentOrder()); // 불휴활기
                    StatusEffectDto samoonStatusEffectDto = StatusEffectDto.of(battleStatus);
                    // 보여주기 위해 각 결과에 스테이터스 삽입
                    // 1. 사문 레벨 5
                    defaultResult.getSetStatusResult().getAddedStatusesList().get(mainActor.getCurrentOrder()).remove(samoonStatusEffectDto);
                    // 2. 불휴활기
                    defaultResult.getSetStatusResult().getAddedStatusesList().get(mainActor.getCurrentOrder()).addAll(firstSupportAbilityAddedStatusMe);
                    // 3. 사문 레벨 5 제거
                    defaultResult.getSetStatusResult().getRemovedStatuesList().get(mainActor.getCurrentOrder()).add(samoonStatusEffectDto);
                    setStatusLogic.removeStatusEffect(mainActor, battleStatus);
                });
        // 자신이 불휴활기 상태인 경우 1, 2 어빌리티 쿨타임 초기화
        getEffectByName(mainActor, "불휴활기")
                .ifPresent(battleStatus -> {
                    mainActor.modifyAbilityCooldowns(0, MoveType.FIRST_ABILITY);
                    mainActor.modifyAbilityCooldowns(0, MoveType.SECOND_ABILITY);
                });
        return resultMapper.chargeAttackToResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult(), defaultResult.isExecuteChargeAttack());
    }

    @Override // 사포아비 3
    public ActorLogicResult postProcessToPartyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult partyMoveResult) {
        // 아군이 "극독" 스테이터스 포함된 어빌리티 사용시 사포아비3 발동
        ActorLogicResult thirdSupportAbilityResult = partyMembers.stream()
                .filter(partyMember -> partyMember.getId().equals(partyMoveResult.getMainBattleActorId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("이전 행동 메인엑터 없음"))
                .getMove(partyMoveResult.getMoveType())
                .getStatusEffects().stream().filter(status -> "극독".equals(status.getName()))
                .findFirst().map(status -> thirdSupportAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.THIRD_SUPPORT_ABILITY))
                ).orElseGet(resultMapper::emptyResult);

        return thirdSupportAbilityResult;
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override // 사포아비2 불휴활기 해제시 수면, 사포아비 4 흉역10일때 디스펠
    public List<ActorLogicResult> processTurnEnd(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        return List.of(
                secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.SECOND_SUPPORT_ABILITY)),
                fourthSupportAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.FOURTH_SUPPORT_ABILITY)));
    }

    @Override // 데미지, 디버프 극독 7이상일때 데미지와 스테이터스 2회발동
    protected ActorLogicResult firstAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        List<BaseStatusEffect> selectedBaseStatusEffects = new ArrayList<>(ability.getStatusEffects());
        int hitCount = getEffectByName(enemy, "극독")
                .filter(battleStatus -> battleStatus.getLevel() >= 7)
                .map(battleStatus -> { // 히트수, 스테이터스 2배
                    selectedBaseStatusEffects.addAll(ability.getStatusEffects());
                    return ability.getHitCount() * 2;
                }).orElse(ability.getHitCount());
        // 어빌리티 본 처리
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, null, hitCount, selectedBaseStatusEffects);
        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        getEffectByName(mainActor, "불휴활기")
                .filter(battleStatus -> battleStatus.getActor().getAbilityUseCount(MoveType.FIRST_ABILITY) < 2)
                .ifPresent(battleStatus -> mainActor.modifyAbilityCooldowns(0, MoveType.FIRST_ABILITY));
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
    }

    @Override // 데미지, 오의게이지 10퍼 업 / 극독 7이상일때 히트수 두배
    protected ActorLogicResult secondAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        int hitCount = getEffectByName(enemy, "극독")
                .filter(battleStatus -> battleStatus.getLevel() >= 7)
                .map(battleStatus -> ability.getHitCount() * 2) // 히트수 두배
                .orElse(ability.getHitCount());
        // 어빌리티 본 처리
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, null, hitCount);
        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        getEffectByName(mainActor, "불휴활기")
                .filter(battleStatus -> battleStatus.getActor().getAbilityUseCount(MoveType.SECOND_ABILITY) < 2)
                .ifPresent(battleStatus -> mainActor.modifyAbilityCooldowns(0, MoveType.SECOND_ABILITY));
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
    }

    @Override // 아군버프, 폼버, 불휴활기 2턴감소
    protected ActorLogicResult thirdAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        getEffectsByName(mainActor, "불휴활기")
                .forEach(battleStatus -> {
                    setStatusLogic.shortenStatusEffectDuration(battleStatus, 2);
                    // 어빌리티 사용으로 인해 0이 된경우 수면 (표시하진 않음 어차피 어빌봉인이라 갱신되면 앎)
                    if (battleStatus.getDuration() == 0)
                        secondAbility(mainActor, enemy, partyMembers, mainActor.getMove(MoveType.SECOND_SUPPORT_ABILITY));
                });
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultResult.getSetStatusResult());
    }

    @Override // 전투시작시 불휴활기
    protected ActorLogicResult firstSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultResult.getSetStatusResult());
    }

    @Override // 턴 종료시 불휴활기 해제시 자신에게 수면
    protected ActorLogicResult secondSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        return getEffectByName(mainActor, "불휴활기")
                .filter(battleStatus -> battleStatus.getDuration() <= 1)
                .map(battleStatus -> { // 턴 종료시 자신의 불휴활기 효과가 1턴 남았다면 수면 효과 적용 CHECK 강화효과 연장되면 문제생김
                            DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
                            return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultResult.getSetStatusResult());
                        }
                ).orElseGet(resultMapper::emptyResult);
    }

    @Override // 아군이 극독 올리는 어빌 사용시 적의 흉역레벨 상승
    protected ActorLogicResult thirdSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultResult.getSetStatusResult());
    }

    @Override // 적의 흉역 레벨이 10인 턴 종료시 디스펠효과
    protected ActorLogicResult fourthSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        return getEffectByName(enemy, "흉역")
                .filter(battleStatus -> battleStatus.getLevel() >= 10)
                .map(battleStatus -> {
                    DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
                    if (defaultResult.getSetStatusResult().getRemovedStatuesList().isEmpty()) return resultMapper.emptyResult(); // 디스펠 못햇으면 빈결과
                    else return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultResult.getSetStatusResult());
                }).orElseGet(resultMapper::emptyResult);
    }
}
