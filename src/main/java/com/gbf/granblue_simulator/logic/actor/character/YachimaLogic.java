package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.BattleContext;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@Transactional
@Slf4j
public class YachimaLogic extends CharacterLogic {

    public YachimaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult());
    }

    // 데미지, 1어빌발동, 레코데이션 싱크시 오의배율 극대로 변화
    @Override
    public ActorLogicResult chargeAttack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        // 오의 배율 변화
        Double damageRate = hasEffectByName(mainActor, "레코데이션 싱크") ? 12.5 : null;
        DefaultActorLogicResult chargeAttackResult = super.defaultChargeAttack(mainActor, enemy, partyMembers, damageRate);
        return resultMapper.toResult(mainActor, enemy, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), chargeAttackResult.getSetStatusResult());
    }

    // 자신이 레코데이션 싱크 효과중 오의 발동 후 1어빌 자동발동
    protected ActorLogicResult chargeAttackAfter(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        if (hasEffectByName(mainActor, "레코데이션")) {
            return firstAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FIRST_ABILITY));
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult partyMoveResult) {
        if (partyMoveResult.getMainBattleActorId().equals(mainActor.getId())) { // 행동 주체가 자신일때
            if (partyMoveResult.getMoveType().getParentType() == MoveType.ATTACK) { // 일반공격시
                if (hasEffectByName(mainActor, "레코데이션")) { // 레코데이션 효과중
                    return fourthSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FOURTH_SUPPORT_ABILITY));
                } else {// 레코디이션 효과 없을때, 자신이 일반공격시 서포트 어빌리티 1 발동
                    return firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY));
                }
            }
            if (partyMoveResult.getMoveType() == MoveType.CHARGE_ATTACK_DEFAULT) { // 오의사용시 1어비 자동발동
                return chargeAttackAfter(mainActor, enemy, partyMembers, null);
            }
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult enemyMoveResult) {
        // 적에게 데미지를 받으면 서포어비 2 발동
        return secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY));
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        // 턴 종료시 서포어비 3 확인
        return List.of(thirdSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY)));
    }

    // 어빌리티

    // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    @Override
    protected ActorLogicResult firstAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move firstAbility) {
        // 알파레벨에 비례해 히트수 증가
        int hitCount = firstAbility.getHitCount() + getLevelByName(mainActor, "알파");
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, firstAbility, null, hitCount);
        return resultMapper.toResult(mainActor, enemy, partyMembers, firstAbility, defaultActorLogicResult.getDamageLogicResult(), defaultActorLogicResult.getSetStatusResult());
    }


    // 아군 전체 방어, 뎀컷, 디스펠가드
    @Override
    protected ActorLogicResult secondAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move secondAbility) {
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, secondAbility);
        return resultMapper.toResult(mainActor, enemy, partyMembers, secondAbility, null, defaultActorLogicResult.getSetStatusResult());
    }

    // 자신 요다메상승, 턴 진행 없이 통상공격실행 (레코데이션 효과중 전체화)
    @Override
    protected ActorLogicResult thirdAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move thirdAbility) {
        StatusEffectTargetType afterMoveTarget = StatusEffectTargetType.SELF; // 턴진행 없이 통상공격 실행 타겟
        SetStatusResult setStatusResult = null;
        if (hasEffectByName(mainActor, "레코데이션 싱크")) {
            // 레코데이션 싱크 효과중 효과 전체화
            setStatusResult = setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, thirdAbility.getStatusEffects(), StatusEffectTargetType.PARTY_MEMBERS);
            afterMoveTarget = StatusEffectTargetType.PARTY_MEMBERS;
        } else {
            // 레코데이션 싱크 x 자신만 적용
            setStatusResult = setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, thirdAbility.getStatusEffects());
        }
        // 쿨타임 적용
        mainActor.modifyAbilityCooldowns(thirdAbility.getCoolDown(), MoveType.THIRD_ABILITY);
        return resultMapper.toResultWithExecuteAttack(mainActor, enemy, partyMembers, thirdAbility, null, setStatusResult, afterMoveTarget);
    }

    // 자신이 공격행동시 사포아비1 적용 (알파레벨 증가)
    @Override
    protected ActorLogicResult firstSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        return isReachedMaxLevelByName(mainActor, "알파") ?
                resultMapper.emptyResult() :
                resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, ability.getStatusEffects()));
    }

    // 자신이 적에게 공격받을시 델타레벨 증가
    @Override
    protected ActorLogicResult secondSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        return isReachedMaxLevelByName(mainActor, "델타") ?
                resultMapper.emptyResult() :
                resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, ability.getStatusEffects()));
    }

    // 자신이 알파레벨, 델타레벨 최대치인경우 턴 종료시 레코데이션 싱크 효과, 알파 델타를 아군 전체에 적용
    @Override
    protected ActorLogicResult thirdSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        Optional<StatusEffect> recordationSyncOptional = getEffectByName(mainActor, "레코데이션");
        if (recordationSyncOptional.isEmpty()) {
            if (isReachedMaxLevelByName(mainActor, "알파") && isReachedMaxLevelByName(mainActor, "델타")) {
                // 레코데이션 싱크 적용
                SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, ability.getStatusEffects());
                // 자신을 포함한 아군 전체에게 알파, 델타 효과 재적용 (타겟 아군 전체로 변경)
                BaseStatusEffect baseStatusEffectAlpha = mainActor.getBaseActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY).getStatusEffects().getFirst();
                BaseStatusEffect baseStatusEffectDelta = mainActor.getBaseActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY).getStatusEffects().getFirst();
                setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, List.of(baseStatusEffectAlpha, baseStatusEffectDelta), StatusEffectTargetType.PARTY_MEMBERS); // 이쪽결과는 이펙트 표시 x

                // 재적용 된 알파, 델타 레벨 4로 변경 및 스탯 갱신
                partyMembers.forEach(partyMember -> setStatusLogic.addStatusEffectsLevel(
                        partyMember, 3,
                        getEffectByName(partyMember, "알파").orElse(null),
                        getEffectByName(partyMember, "델타").orElse(null)));

                // 자신의 3어빌 쿨타임 0으로 감소
                mainActor.modifyAbilityCooldowns(0, MoveType.THIRD_ABILITY);
                return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusResult);
            }
        }
        return resultMapper.emptyResult();
    }

    // 자신이 레코데이션 싱크 효과중 통상공격 후 5배 데미지 3회, 방어력 다운
    @Override
    protected ActorLogicResult fourthSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, null, null);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
    }
}
