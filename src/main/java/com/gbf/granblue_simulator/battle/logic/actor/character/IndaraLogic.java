package com.gbf.granblue_simulator.battle.logic.actor.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
@Slf4j
@Transactional
public class IndaraLogic extends CharacterLogic {

    public IndaraLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override // 사포아비 1
    public List<ActorLogicResult> processBattleStart() {
        return List.of(firstSupportAbility());
    }

    @Override
    protected ActorLogicResult attack() {
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    @Override // 1아비 2아비 즉시 사용가능, 사문레벨 증가, 사문레벨 5일때 불휴활기 효과
    protected ActorLogicResult chargeAttack() {
        Actor self = battleContext.getMainActor();
        // 기본 오의처리 -> 사문효과만 적용
        List<BaseStatusEffect> selectedBaseStatusEffects = List.of(getBaseEffectByNameFromMove(selfMove(MoveType.CHARGE_ATTACK_DEFAULT), "사문"));
        DefaultActorLogicResult defaultResult = defaultChargeAttack(null, selectedBaseStatusEffects);

        // 사문 레벨이 5인경우 삭제후 불휴활기
        getEffectByName(self, "사문")
                .filter(statusEffect -> statusEffect.getLevel() >= 5)
                .ifPresent(statusEffect -> {
                    // defaultEffect의 각 상태효과 조작
                    List<ResultStatusEffectDto> addedStatusEffectsSelfInDefaultResult = defaultResult.getSetStatusResult().getAddedStatusesList().get(self.getCurrentOrder());
                    List<ResultStatusEffectDto> removedStatusEffectsSelfInDefaultResult = defaultResult.getSetStatusResult().getRemovedStatuesList().get(self.getCurrentOrder());

                    // 불휴활기 부여
                    ActorLogicResult firstSupportAbilityResult = firstSupportAbility();
                    List<ResultStatusEffectDto> firstSupportAbilityAddedStatusEffectsSelf = firstSupportAbilityResult.getAddedStatusEffectsList().get(self.getCurrentOrder()); // 불휴활기 포함됨
                    addedStatusEffectsSelfInDefaultResult.addAll(firstSupportAbilityAddedStatusEffectsSelf);

                    // 5레벨 사문은 [추가된] -> [제거된] 으로 변경
                    ResultStatusEffectDto samoonStatusEffectDto = ResultStatusEffectDto.of(statusEffect);
                    addedStatusEffectsSelfInDefaultResult.remove(samoonStatusEffectDto);
                    removedStatusEffectsSelfInDefaultResult.add(samoonStatusEffectDto);
                    setStatusLogic.removeStatusEffect(self, statusEffect);
                });

        // 자신이 불휴활기 상태인 경우 1, 2 어빌리티 쿨타임 초기화
        getEffectByName(self, "불휴활기").ifPresent(statusEffect -> self.updateAbilityCooldowns(0, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY));

        return resultMapper.fromDefaultResult(defaultResult);
    }

    @Override // 사포아비 3
    public ActorLogicResult postProcessToPartyMove(ActorLogicResult partyMoveResult) {
        // 아군이 "극독" 스테이터스 포함된 어빌리티 사용시 사포아비3 발동
        ActorLogicResult thirdSupportAbilityResult = battleContext.getFrontCharacters().stream()
                .filter(partyMoveResult::isFromActor)
                .findAny()
                .flatMap(partyMember ->
                        checkBaseEffectByNameFromMove(partyMember.getMove(partyMoveResult.getMove().getType()), "극독")
                                .map(baseEffect -> thirdSupportAbility())
                ).orElseGet(resultMapper::emptyResult);

        return thirdSupportAbilityResult;
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override // 사포아비2 불휴활기 해제시 수면, 사포아비 4 흉역10일때 디스펠
    public List<ActorLogicResult> processTurnEnd() {
        return List.of(secondSupportAbility(), fourthSupportAbility());
    }

    @Override // 데미지, 디버프 극독 7이상일때 데미지와 스테이터스 2회발동
    protected ActorLogicResult firstAbility() {
        Move ability = selfMove(MoveType.FIRST_ABILITY);
        int hitCount = ability.getHitCount();
        List<BaseStatusEffect> selectedBaseStatusEffects = new ArrayList<>(ability.getBaseStatusEffects());

        // 극독 레벨 7 이상일때, 데미지와 상태효과가 2회 발동
        boolean shouldApplyBonusHitAndStatusEffects = getEffectByName(battleContext.getEnemy(), "극독")
                .filter(battleStatus -> battleStatus.getLevel() >= 7)
                .isPresent();
        if (shouldApplyBonusHitAndStatusEffects) {
            selectedBaseStatusEffects.addAll(ability.getBaseStatusEffects());
            hitCount = ability.getHitCount() * 2;
        }

        // 어빌리티 본 처리
        DefaultActorLogicResult defaultResult = defaultAbility(ability, null, hitCount, selectedBaseStatusEffects);

        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        getEffectByName(self(), "불휴활기")
                .filter(statusEffect -> self().getAbilityUseCount(MoveType.FIRST_ABILITY) < 2)
                .ifPresent(statusEffect -> self().updateAbilityCooldowns(0, MoveType.FIRST_ABILITY));

        return resultMapper.fromDefaultResult(defaultResult);
    }

    @Override // 데미지, 오의게이지 10퍼 업 / 극독 7이상일때 히트수 두배
    protected ActorLogicResult secondAbility() {
        Move ability = selfMove(MoveType.SECOND_ABILITY);
        int hitCount = getEffectByName(battleContext.getEnemy(), "극독")
                .filter(battleStatus -> battleStatus.getLevel() >= 7)
                .map(battleStatus -> ability.getHitCount() * 2) // 히트수 두배
                .orElse(ability.getHitCount());
        // 어빌리티 본 처리
        DefaultActorLogicResult defaultResult = defaultAbility(ability, null, hitCount);
        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        getEffectByName(self(), "불휴활기")
                .filter(battleStatus -> battleStatus.getActor().getAbilityUseCount(MoveType.SECOND_ABILITY) < 2)
                .ifPresent(battleStatus -> self().updateAbilityCooldowns(0, MoveType.SECOND_ABILITY));
        return resultMapper.fromDefaultResult(defaultResult);
    }

    @Override // 아군버프, 폼버, 불휴활기 2턴감소
    protected ActorLogicResult thirdAbility() {
        Move ability = selfMove(MoveType.THIRD_ABILITY);
        DefaultActorLogicResult defaultResult = defaultAbility(ability);
        getEffectByName(self(), "불휴활기")
                .ifPresent(statusEffect -> {
                    setStatusLogic.shortenStatusEffectDuration(statusEffect, 2);
                    // 어빌리티 사용으로 인해 0이 된경우 수면 (표시하진 않음 어차피 어빌봉인이라 갱신되면 앎)
                    if (statusEffect.getDuration() == 0)
                        secondSupportAbility();
                });
        return resultMapper.fromDefaultResult(defaultResult);
    }

    @Override // 전투시작시 불휴활기
    protected ActorLogicResult firstSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.FIRST_SUPPORT_ABILITY)));
    }

    @Override // 턴 종료시 불휴활기 해제시 자신에게 수면
    protected ActorLogicResult secondSupportAbility() {
        return getEffectByName(self(), "불휴활기")
                .filter(battleStatus -> battleStatus.getDuration() <= 1)
                .map(battleStatus ->  // 턴 종료시 자신의 불휴활기 효과가 1턴 남았다면 수면 효과 적용 CHECK 강화효과 연장되면 문제생김
                        resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_SUPPORT_ABILITY)))
                ).orElseGet(resultMapper::emptyResult);
    }

    @Override // 아군이 극독 올리는 어빌 사용시 적의 흉역레벨 상승
    protected ActorLogicResult thirdSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.THIRD_SUPPORT_ABILITY)));
    }

    @Override // 적의 흉역 레벨이 10인 턴 종료시 디스펠효과
    protected ActorLogicResult fourthSupportAbility() {
        Move ability = battleContext.getMainActor().getMove(MoveType.FOURTH_SUPPORT_ABILITY);
        return getEffectByName(battleContext.getEnemy(), "흉역")
                .filter(battleStatus -> battleStatus.getLevel() >= 10)
                .map(statusEffect -> defaultAbility(ability))
                .filter(defaultResult -> !defaultResult.getSetStatusResult().getRemovedStatuesList().getFirst().isEmpty())
                .map(resultMapper::fromDefaultResult)
                .orElseGet(resultMapper::emptyResult);
    }
}
