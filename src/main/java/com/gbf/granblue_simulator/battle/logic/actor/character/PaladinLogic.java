package com.gbf.granblue_simulator.battle.logic.actor.character;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.*;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@Transactional
@Slf4j
public class PaladinLogic extends CharacterLogic {

    public PaladinLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, BattleContext battleContext) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, battleContext);
    }

    @Override
    public List<ActorLogicResult> processBattleStart() {
        // 전투 시작시 서포어비 1, 2 발동
        return List.of(firstSupportAbility(), secondSupportAbility());
    }

    @Override
    public ActorLogicResult attack() {
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    @Override // 아군 전체 스트렝스, 베리어
    public ActorLogicResult chargeAttack() {
        return resultMapper.fromDefaultResult(defaultChargeAttack());
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(ActorLogicResult partyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd() {
        return Collections.emptyList();
    }

    @Override // 참전자 전체 데미지 컷
    protected ActorLogicResult firstAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.FIRST_ABILITY)));
    }

    @Override // 자기자신 감싸기, 베리어
    protected ActorLogicResult secondAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_ABILITY)));
    }

    @Override // 아군전체 피데미지 감소
    protected ActorLogicResult thirdAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.THIRD_ABILITY)));
    }

    @Override // 전투 시작시 자신에게 방패의 수호 효과 *방패의 수호: 자신의 피격 데미지 최대치를 10000으로 고정 / 자신의 더블어택과 트리플어택 확률 25% 상승 (소거불가)
    protected ActorLogicResult firstSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.FIRST_SUPPORT_ABILITY)));
    }

    @Override // 전투 시작시 자신에게 불사신 효과 *불사신 효과: 체력이 0이 되었을때 1회에 한해 체력1 로 버팀
    protected ActorLogicResult secondSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.SECOND_SUPPORT_ABILITY)));
    }
}
