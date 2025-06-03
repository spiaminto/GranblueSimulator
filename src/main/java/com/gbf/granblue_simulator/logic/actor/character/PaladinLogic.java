package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@Transactional
@Slf4j
public class PaladinLogic extends CharacterLogic {

    private final CalcStatusLogic calcStatusLogic;

    public PaladinLogic(StatusUtil statusUtil, CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, CalcStatusLogic calcStatusLogic) {
        super(statusUtil, resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic);
        this.calcStatusLogic = calcStatusLogic;
    }

    @Override
    public List<ActorLogicResult> processBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        calcStatusLogic.initStatus(mainActor);
        // 전투 시작시 서포어비 1, 2 발동
        return List.of(
                firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY)),
                secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY)));
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult());
    }

    @Override // 아군 전체 스트렝스, 베리어
    public ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultChargeAttack(mainActor, enemy, partyMembers, null);
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultActorLogicResult.getResultMove(), defaultActorLogicResult.getDamageLogicResult(), defaultActorLogicResult.getSetStatusResult());
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult partyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return Collections.emptyList();
    }

    @Override // 아군전체 데미지 컷 // TODO 참전자 스테이터스 적용
    protected ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move firstAbility) {
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, firstAbility);
        return resultMapper.toResult(mainActor, enemy, partyMembers, firstAbility, null, defaultActorLogicResult.getSetStatusResult());
    }

    @Override // 자기자신 감싸기, 베리어
    protected ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move secondAbility) {
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, secondAbility);
        return resultMapper.toResult(mainActor, enemy, partyMembers, secondAbility, null, defaultActorLogicResult.getSetStatusResult());
    }

    @Override // 아군전체 피데미지 감소
    protected ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move thirdAbility) {
        DefaultActorLogicResult defaultActorLogicResult = defaultAbility(mainActor, enemy, partyMembers, thirdAbility);
        return resultMapper.toResult(mainActor, enemy, partyMembers, thirdAbility, null, defaultActorLogicResult.getSetStatusResult());
    }

    @Override // 전투 시작시 자신에게 방패의 수호 효과 *방패의 수호: 자신의 피격 데미지 최대치를 10000으로 고정 / 자신의 더블어택과 트리플어택 확률 25% 상승 (소거불가)
    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultActorLogicResult.getSetStatusResult());
    }

    @Override // 전투 시작시 자신에게 불사신 효과 *불사신 효과: 체력이 0이 되었을때 1회에 한해 체력1 로 버팀
    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultActorLogicResult = super.defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, ability, null, defaultActorLogicResult.getSetStatusResult());
    }



}
