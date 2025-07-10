package com.gbf.granblue_simulator.logic.actor.character;


import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
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

    public HairaLogic(CharacterLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        // 전투시작시 사포아비1 패시브 발동
        ActorLogicResult firstSupportAbilityResult = firstSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY));

        return List.of(firstSupportAbilityResult);
    }

    @Override
    protected ActorLogicResult attack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        if (mainActor.getStrikeCount() >= 2) {
            //서포어비3 자신의 2회째 공격행동부터 서포어비 3 스테이터스 (어쌔신) 적용 - 해당 효과는 턴 종료시 자동삭제
            List<Status> thirdSupportAbilityStatuses = mainActor.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY).getStatuses();
            setStatusLogic.setStatus(mainActor, enemy, partyMembers, thirdSupportAbilityStatuses);
        }
        DefaultActorLogicResult defaultAttackResult = defaultAttack(mainActor, enemy, partyMembers);
        return resultMapper.attackToResult(mainActor, enemy, partyMembers, defaultAttackResult.getResultMove(), defaultAttackResult.getDamageLogicResult());
    }

    @Override
    protected ActorLogicResult chargeAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        DefaultActorLogicResult defaultResult = defaultChargeAttack(mainActor, enemy, partyMembers, null);
        // 자신의 어빌리티 쿨타임 2턴 단축
        mainActor.updateAbilityCoolDown(mainActor.getFirstAbilityCoolDown() - 2, MoveType.FIRST_ABILITY);
        mainActor.updateAbilityCoolDown(mainActor.getSecondAbilityCoolDown() - 2, MoveType.SECOND_ABILITY);
        mainActor.updateAbilityCoolDown(mainActor.getThirdAbilityCoolDown() - 2, MoveType.THIRD_ABILITY);
        return resultMapper.chargeAttackToResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult(), false);
    }

    @Override
    public ActorLogicResult postProcessToPartyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult partyMoveResult) {
        // 아군이 2회이상 행동할때마다 사포아비 2 발동
        if (partyMoveResult.getMoveType().getParentType() == MoveType.ATTACK || partyMoveResult.getMoveType() == MoveType.CHARGE_ATTACK_DEFAULT) {
            return partyMembers.stream()
                    .filter(partyMember -> partyMember.getId().equals(partyMoveResult.getMainBattleActorId()))
                    .map(BattleActor::getStrikeCount)
                    .map(strikeCount -> {
                        if (strikeCount == 2) // 두번째 행동에서만 발동
                            return secondSupportAbility(mainActor, enemy, partyMembers, mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY));
                        else
                            return resultMapper.emptyResult();
                    })
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("아군 mainActor 가 없음"));
        }
        return resultMapper.emptyResult();
    }

    @Override
    public ActorLogicResult postProcessToEnemyMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, ActorLogicResult enemyMoveResult) {
        return resultMapper.emptyResult();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers) {
        return List.of();
    }

    @Override // 자신이 지보의 황성 상태일때, 스택에 비례해 자신에게 재행동, 적에게 감전, 지보의 황성 삭제
    protected ActorLogicResult firstAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        return StatusUtil.getBattleStatusByName(mainActor, "지보의 황성")
                .map(battleStatus -> {
                    int battleStatusLevel = battleStatus.getLevel();
                    int strikeCount = battleStatusLevel >= 5 ? 4 : battleStatusLevel >= 3 ? 3 : 2; // 스택 1~2 2회, 3~4 3회, 5 4회
                    List<Status> selectedStatuses = ability.getStatuses().stream() // 재행동 && 재행동 값이 계산된 값이 아닌 스테이터스 제외
                            .filter(status -> !(status.getStatusEffects().containsKey(StatusEffectType.MULTI_STRIKE) && status.getStatusEffects().get(StatusEffectType.MULTI_STRIKE).getValue() != strikeCount))
                            .toList();
                    DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability, selectedStatuses);
                    setStatusLogic.removeBattleStatus(mainActor, battleStatus); // 지보의 황성 삭제
                    return resultMapper.toResult(mainActor, enemy, partyMembers, ability, defaultResult.getDamageLogicResult(), defaultResult.getSetStatusResult());
                })
                .orElseThrow(() -> new IllegalArgumentException("어빌리티를 사용할 수 없습니다."));
    }

    @Override // 아군 전체에 운룡효과
    protected ActorLogicResult secondAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    @Override // 자신에게 감싸기, 회피율상승, 마운트, 자신이외의 아군에 재행동
    protected ActorLogicResult thirdAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    @Override // 전투시작시 패시브
    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        defaultAbility(mainActor, enemy, partyMembers, ability);
        return resultMapper.emptyResult();
    }

    @Override // 아군이 2회이상 행동할때마다 자신에게 지보의 황성, 오의게이지 20% 상승
    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move ability) {
        DefaultActorLogicResult defaultResult = defaultAbility(mainActor, enemy, partyMembers, ability);
        chargeGaugeLogic.addChargeGauge(mainActor, 20); // 오의게이지 직접 조작
        return resultMapper.toResult(mainActor, enemy, partyMembers, defaultResult.getResultMove(), null, defaultResult.getSetStatusResult());
    }

    // 서포어비 3 자신의 두번째 공격행동부터 어쌔신 효과 -> attack 에서 갈음
}
