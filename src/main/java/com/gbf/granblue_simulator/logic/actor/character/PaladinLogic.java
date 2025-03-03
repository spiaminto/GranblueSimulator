package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.ChargeGaugeLogic;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaladinLogic implements CharacterLogic {

    private final SetStatusLogic setStatusLogic;
    private final DamageLogic damageLogic;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final CharacterLogicResultMapper resultMapper;
    private final ActorLogicUtil actorLogicUtil;


    @Override
    public ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 데미지
        Move attackMove = actorLogicUtil.determineAttackMove(mainActor);
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, attackMove);

        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, attackMove.getType());

        return resultMapper.attackToResult(mainActor, enemy, partyMembers, attackMove, damageLogicResult);
    }

    @Override // 아군전체 데미지 컷
    public ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = mainActor.getActor().getMoves().get(MoveType.FIRST_ABILITY);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses());

        // TODO 참전자 스테이터스 적용

        // 쿨타임 적용
        mainActor.setFirstAbilityCoolDown(ability.getCoolDown());

        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusResult);
    }

    @Override // 자기자신 감싸기, 베리어
    public ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = mainActor.getActor().getMoves().get(MoveType.SECOND_ABILITY);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses());
        // 쿨타임 적용
        mainActor.setSecondAbilityCoolDown(ability.getCoolDown());

        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusResult);
    }

    @Override // 아군전체 피데미지 감소
    public ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move ability = mainActor.getActor().getMoves().get(MoveType.THIRD_ABILITY);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, ability.getStatuses());
        // 쿨타임 적용
        mainActor.setThirdAbilityCoolDown(ability.getCoolDown());

        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, setStatusResult);
    }

    @Override // 아군 전체 스트렝스, 베리어
    public ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        Move chargeAttack = mainActor.getActor().getMoves().get(MoveType.CHARGE_ATTACK);
        // 데미지 계싼
        DamageLogicResult damageLogicResult = damageLogic.process(mainActor, enemy, chargeAttack);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, enemy, partyMembers, chargeAttack.getStatuses());
        // 오의게이지
        chargeGaugeLogic.afterAttack(mainActor, partyMembers, chargeAttack.getType());

        return resultMapper.toResult(mainActor, enemy, partyMembers, chargeAttack, damageLogicResult, setStatusResult);
    }

    @Override
    public ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // onBattleStart 로 갈음
        return null;
    }

    @Override
    public ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // onBattleStart 로 갈음
        return null;
    }

    @Override
    public ActorLogicResult thirdSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
        return null;
    }

    @Override
    public ActorLogicResult postProcessOtherMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
        return null;
    }

    @Override
    public ActorLogicResult postProcessEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 없음
        return null;
    }

    @Override
    public ActorLogicResult onBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        setStatusLogic.initStatus(mainActor);

        Map<MoveType, Move> moves = mainActor.getActor().getMoves();
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, moves.get(MoveType.FIRST_SUPPORT_ABILITY).getStatuses());
        setStatusLogic.setStatus(mainActor, enemy, partyMembers, moves.get(MoveType.SECOND_SUPPORT_ABILITY).getStatuses());
        return null;
    }

    @Override
    public ActorLogicResult onTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return null;
    }

}
