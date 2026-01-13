package com.gbf.granblue_simulator.battle.logic.actor.character;


import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByName;

@Slf4j
@Component
@Transactional
public class HairaLogic extends CharacterLogic {

    public HairaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override
    public List<ActorLogicResult> processBattleStart() {
        // 전투시작시 사포아비1 패시브 발동
        ActorLogicResult firstSupportAbilityResult = firstSupportAbility();

        return List.of(firstSupportAbilityResult);
    }

    @Override
    protected ActorLogicResult attack() {
        if (self().getExecutedStrikeCount() >= 1) {
            //서포어비3 자신의 2회째 공격행동부터 서포어비 3 스테이터스 (어쌔신) 적용 - 해당 효과는 턴 종료시 자동삭제
            List<BaseStatusEffect> thirdSupportAbilityBaseStatusEffects = selfMove(MoveType.THIRD_SUPPORT_ABILITY).getBaseStatusEffects();
            setStatusLogic.setStatusEffect(thirdSupportAbilityBaseStatusEffects);
        }
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    @Override
    protected ActorLogicResult chargeAttack() {
        // 자신의 어빌리티 쿨타임 2턴 단축
        self().modifyAbilityCooldowns(-2, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY, MoveType.THIRD_ABILITY);
        return resultMapper.fromDefaultResult(defaultChargeAttack());
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(ActorLogicResult partyMoveResult) {
        // 아군이 2회이상 행동할때마다 사포아비 2 발동
        Move resultMove = partyMoveResult.getMove();
        if (resultMove.getParentType() == MoveType.ATTACK || resultMove.getType() == MoveType.CHARGE_ATTACK_DEFAULT) { // 미리 좀 거르고 시작
            return battleContext.getFrontCharacters().stream()
                    .filter(partyMoveResult::isFromActor)
                    .findAny()
                    .filter(actor -> actor.getExecutedStrikeCount() >= 2 && actor.getExecutedStrikeCount() == actor.getStatus().getStatusDetails().getEndStrikeCount()) // 2회 이상공격시, 마지막 공격행동떄 설정
                    .map(actor -> secondSupportAbility())
                    .orElse(resultMapper.emptyResult());
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd() {
        return List.of();
    }

    @Override // 자신이 지보의 황성 상태일때, 스택에 비례해 자신에게 재행동, 적에게 감전, 지보의 황성 삭제,
    protected ActorLogicResult firstAbility() {
        Move ability = selfMove(MoveType.FIRST_ABILITY);
        return getEffectByName(self(), "지보의 황성")
                .map(statusEffect -> {
                    StatusModifierType multiStrikeModifier = statusEffect.getLevel() >= 5 ? StatusModifierType.QUADRUPLE_STRIKE
                            : statusEffect.getLevel() >= 3 ? StatusModifierType.TRIPLE_STRIKE
                            : StatusModifierType.DOUBLE_STRIKE; // 스택 1~2 2회, 3~4 3회, 5 4회
                    List<BaseStatusEffect> selectedBaseStatusEffects = ability.getBaseStatusEffects().stream() // !(버프 && 선택된 다회행동이 아닌경우)
                            .filter(status -> !(status.getType() == StatusEffectType.BUFF && status.getStatusModifiers().get(multiStrikeModifier) == null))
                            .toList();
                    setStatusLogic.removeStatusEffect(self(), statusEffect); // 지보의 황성 삭제
                    return resultMapper.fromDefaultResult(defaultAbility(ability, selectedBaseStatusEffects));
                })
                .orElseThrow(() -> new MoveValidationException("지보의 황성 스택이 부족해 어빌리티를 사용할 수 없습니다.", true));
    }

    @Override // 아군 전체에 운룡효과
    protected ActorLogicResult secondAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_ABILITY)));
    }

    @Override // 자신에게 감싸기, 회피율상승, 마운트, 자신이외의 아군에 재행동
    protected ActorLogicResult thirdAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.THIRD_ABILITY)));
    }

    @Override // 전투시작시 패시브
    protected ActorLogicResult firstSupportAbility() {
        defaultAbility(selfMove(MoveType.FIRST_SUPPORT_ABILITY));
        return resultMapper.emptyResult();
    }

    @Override // 아군이 2회이상 행동할때마다 자신에게 지보의 황성, 오의게이지 20% 상승,
    protected ActorLogicResult secondSupportAbility() {
        // 오의게이지 증가
        chargeGaugeLogic.setChargeGauge(self(), self().getChargeGauge() + 20);
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_SUPPORT_ABILITY)));
    }

    // 서포어비 3 자신의 두번째 공격행동부터 어쌔신 효과 -> attack 에서 갈음
}
