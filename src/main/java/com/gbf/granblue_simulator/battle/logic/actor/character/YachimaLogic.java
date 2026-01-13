package com.gbf.granblue_simulator.battle.logic.actor.character;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
@Transactional
@Slf4j
public class YachimaLogic extends CharacterLogic {

    public YachimaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override
    public List<ActorLogicResult> processBattleStart() {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack() {
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    // 데미지, 1어빌발동, 레코데이션 싱크시 오의배율 극대로 변화
    @Override
    public ActorLogicResult chargeAttack() {
        // 레코데이션 싱크 중 배율 극대
        Double damageRate = hasEffectByName(battleContext.getMainActor(), "레코데이션 싱크") ? 12.5 : null;
        return resultMapper.fromDefaultResult(defaultChargeAttack(damageRate));
    }

    // 자신이 레코데이션 싱크 효과중 오의 발동 후 1어빌 자동발동
    protected ActorLogicResult chargeAttackAfter() {
        return hasEffectByName(self(), "레코데이션 싱크") ? firstAbility() : resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(ActorLogicResult partyMoveResult) {
        if (!partyMoveResult.isFromActor(self())) return resultMapper.emptyResult();
        Move resultMove = partyMoveResult.getMove();

        boolean hasRecordationSync = hasEffectByName(self(), "레코데이션 싱크");
        if (!hasRecordationSync) {
            if (resultMove.getType() == MoveType.TRIPLE_ATTACK) return firstSupportAbility();
            else return resultMapper.emptyResult();
        }

        // 레코데이션 싱크 효과 있음
        MoveType moveParentType = resultMove.getParentType();
        if (moveParentType == MoveType.ATTACK) return fourthSupportAbility();
        else if (moveParentType == MoveType.CHARGE_ATTACK) return chargeAttackAfter();

        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(ActorLogicResult enemyMoveResult) {
        // 적에게 데미지를 받으면 서포어비 2 발동
        if (!enemyMoveResult.getDamages().isEmpty() && enemyMoveResult.getEnemyAttackTargets().contains(self())) {
            return secondSupportAbility();
        }
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd() {
        // 턴 종료시 서포어비 3 확인
        return List.of(thirdSupportAbility());
    }

    // 어빌리티

    // 적에게 데미지, 자신에게 추격, 적에게 디그레이드스피넬
    @Override
    protected ActorLogicResult firstAbility() {
        // 알파레벨에 비례해 히트수 증가
        Move ability = self().getMove(MoveType.FIRST_ABILITY);
        int hitCount = ability.getHitCount() + getLevelByName(self(), "알파");
        return resultMapper.fromDefaultResult(defaultAbility(ability, null, hitCount));
    }


    // 아군 전체 방어, 뎀컷, 디스펠가드
    @Override
    protected ActorLogicResult secondAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_ABILITY)));
    }

    // 자신 요다메상승, 턴 진행 없이 통상공격실행 (레코데이션 효과중 전체화)
    @Override
    protected ActorLogicResult thirdAbility() {
        Move ability = selfMove(MoveType.THIRD_ABILITY);
        StatusEffectTargetType applyEffectTarget = hasEffectByName(self(), "레코데이션 싱크")
                ? StatusEffectTargetType.PARTY_MEMBERS : StatusEffectTargetType.SELF; // 상태효과, 턴진행 없이 통상공격 실행 타겟

        defaultAbility(ability, List.of());// 빈 상태효과 전달, 상태효과는 직접적용
        // 타겟에 맞게 상태효과 직접 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(ability.getBaseStatusEffects(), applyEffectTarget);

        return resultMapper.toResultWithExecuteAttack(ability, null, setStatusEffectResult, applyEffectTarget);
    }

    // 자신이 공격행동시 사포아비1 적용 (알파레벨 증가)
    @Override
    protected ActorLogicResult firstSupportAbility() {
        DefaultActorLogicResult defaultResult = defaultAbility(selfMove(MoveType.FIRST_SUPPORT_ABILITY));
        return isReachedMaxLevelByName(self(), "알파") ?
                resultMapper.emptyResult() : // 자신의 알파레벨이 만렙이면 스킵
                resultMapper.fromDefaultResult(defaultResult);
    }

    // 자신이 적에게 공격받을시 델타레벨 증가
    @Override
    protected ActorLogicResult secondSupportAbility() {
        DefaultActorLogicResult defaultResult = defaultAbility(selfMove(MoveType.SECOND_SUPPORT_ABILITY));
        return isReachedMaxLevelByName(self(), "델타") ?
                resultMapper.emptyResult() :
                resultMapper.fromDefaultResult(defaultResult);
    }

    // 자신이 알파레벨, 델타레벨 최대치인경우 턴 종료시 레코데이션 싱크 효과, 알파 델타를 아군 전체에 적용
    @Override
    protected ActorLogicResult thirdSupportAbility() {
        return getEffectByName(self(), "레코데이션 싱크")
                .map(statusEffect -> resultMapper.emptyResult())
                .orElseGet(() -> {
                    if (!(isReachedMaxLevelByName(self(), "알파") && isReachedMaxLevelByName(self(), "델타")))
                        return resultMapper.emptyResult();

                    // 자신을 포함한 아군 전체에게 알파, 델타 효과 재적용 (타겟 아군 전체로 변경)
                    BaseStatusEffect alpha = getBaseEffectByNameFromMove(selfMove(MoveType.FIRST_SUPPORT_ABILITY), "알파");
                    BaseStatusEffect delta = getBaseEffectByNameFromMove(selfMove(MoveType.SECOND_SUPPORT_ABILITY), "델타");
                    setStatusLogic.setStatusEffect(List.of(alpha, delta), StatusEffectTargetType.PARTY_MEMBERS); // 이쪽결과는 이펙트 표시 x

                    // 재적용 된 알파, 델타 레벨 4로 변경 및 스탯 갱신
                    battleContext.getFrontCharacters().forEach(partyMember -> setStatusLogic.addStatusEffectsLevel(
                            partyMember, 3,
                            getEffectByName(partyMember, "알파").orElse(null),
                            getEffectByName(partyMember, "델타").orElse(null)));

                    // 자신의 3어빌 쿨타임 0으로 감소
                    self().updateAbilityCooldowns(0, MoveType.THIRD_ABILITY);
                    // 레코데이션 싱크 적용
                    return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.THIRD_SUPPORT_ABILITY)));
                });
    }

    // 자신이 레코데이션 싱크 효과중 통상공격 후 5배 데미지 3회, 방어력 다운
    @Override
    protected ActorLogicResult fourthSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.FOURTH_SUPPORT_ABILITY)));
    }
}
