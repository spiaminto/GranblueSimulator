package com.gbf.granblue_simulator.logic.actor.character;


import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.BattleContext;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.exception.MoveValidationException;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.StatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Transactional
public class HairaLogic extends CharacterLogic {

    public HairaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        // 1어빌리티 봉인 (사용불가)
        mainActor.updateAbilityUsable(false, MoveType.FIRST_ABILITY);
        // 전투시작시 사포아비1 패시브 발동
        ActorLogicResult firstSupportAbilityResult = firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY));

        return List.of(firstSupportAbilityResult);
    }

    @Override
    protected ActorLogicResult attack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        if (mainActor.getStrikeCount() >= 2) {
            //서포어비3 자신의 2회째 공격행동부터 서포어비 3 스테이터스 (어쌔신) 적용 - 해당 효과는 턴 종료시 자동삭제
            List<BaseStatusEffect> thirdSupportAbilityBaseStatusEffects = mainActor.getBaseActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY).getStatusEffects();
            setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, thirdSupportAbilityBaseStatusEffects);
        }
        DefaultActorLogicResult defaultAttackResult = defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultAttackResult.getResultMove(), defaultAttackResult.getDamageLogicResult());
    }

    @Override
    protected ActorLogicResult chargeAttack(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        DefaultActorLogicResult defaultResult = defaultChargeAttack(mainActor, enemy, partyMembers, null);
        // 자신의 어빌리티 쿨타임 2턴 단축
        mainActor.modifyAbilityCooldowns(-2, MoveType.FIRST_ABILITY);
        mainActor.modifyAbilityCooldowns(-2, MoveType.SECOND_ABILITY);
        mainActor.modifyAbilityCooldowns(-2, MoveType.THIRD_ABILITY);
        return resultMapper.chargeAttackToResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult(), false);
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult partyMoveResult) {
        // 아군이 2회이상 행동할때마다 사포아비 2 발동
        if (partyMoveResult.getMoveType().getParentType() == MoveType.ATTACK || partyMoveResult.getMoveType() == MoveType.CHARGE_ATTACK_DEFAULT) {
            return partyMembers.stream()
                    .filter(partyMember -> partyMember.getId().equals(partyMoveResult.getMainBattleActorId()))
                    .map(Actor::getStrikeCount)
                    .map(strikeCount -> {
                        if (strikeCount == 2) { // 두번째 행동에서만 발동
                            mainActor.updateAbilityUsable(true, MoveType.FIRST_ABILITY); // 지보의 황성 스택이 오르면 1어빌 봉인해제
                            return secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY));
                        } else
                            return resultMapper.emptyResult();
                    })
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("아군 mainActor 가 없음"));
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(Actor mainActor, Actor enemy, List<Actor> partyMembers, ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(Actor mainActor, Actor enemy, List<Actor> partyMembers) {
        return List.of();
    }

    @Override // 자신이 지보의 황성 상태일때, 스택에 비례해 자신에게 재행동, 적에게 감전, 지보의 황성 삭제
    protected ActorLogicResult firstAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        return StatusUtil.getEffectByName(mainActor, "지보의 황성")
                .map(battleStatus -> {
                    int battleStatusLevel = battleStatus.getLevel();
                    int strikeCount = battleStatusLevel >= 5 ? 4 : battleStatusLevel >= 3 ? 3 : 2; // 스택 1~2 2회, 3~4 3회, 5 4회
                    List<BaseStatusEffect> selectedBaseStatusEffects = ability.getStatusEffects().stream() // 재행동 && 재행동 값이 계산된 값이 아닌 스테이터스 제외
                            .filter(status -> !(status.getStatusModifiers().containsKey(StatusModifierType.MULTI_STRIKE) && status.getStatusModifiers().get(StatusModifierType.MULTI_STRIKE).getValue() != strikeCount))
                            .toList();
                    DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, selectedBaseStatusEffects);
                    setStatusLogic.removeStatusEffect(mainActor, battleStatus); // 지보의 황성 삭제
                    mainActor.updateAbilityUsable(false, MoveType.FIRST_ABILITY); // 첫번째 어빌리티 사용불가
                    return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
                })
                .orElseThrow(() -> new MoveValidationException("지보의 황성 스택이 부족해 어빌리티를 사용할 수 없습니다."));
    }

    @Override // 아군 전체에 운룡효과
    protected ActorLogicResult secondAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    @Override // 자신에게 감싸기, 회피율상승, 마운트, 자신이외의 아군에 재행동
    protected ActorLogicResult thirdAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    @Override // 전투시작시 패시브
    protected ActorLogicResult firstSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.emptyResult();
    }

    @Override // 아군이 2회이상 행동할때마다 자신에게 지보의 황성, 오의게이지 20% 상승
    protected ActorLogicResult secondSupportAbility(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        chargeGaugeLogic.setChargeGauge(mainActor, mainActor.getChargeGauge() + 20); // 오의게이지 직접 조작
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    // 서포어비 3 자신의 두번째 공격행동부터 어쌔신 효과 -> attack 에서 갈음
}
