package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class SummonLogic {

    private final DamageLogic damageLogic;
    private final SetStatusLogic setStatusLogic;
    private final CharacterLogicResultMapper resultMapper;
    private final BattleContext battleContext;


    public ActorLogicResult processSummon(Move summonMove, boolean isUnionSummon) {
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        battleContext.setCurrentMainActor(leaderCharacter);
        int summonIndex = leaderCharacter.getSummonMoveIds().indexOf(summonMove.getId());
        if (summonIndex < 0) throw new MoveValidationException("[processSummon] 소환할 수 없는 대상 summonIndex = " + summonIndex + "summon = " + summonMove.getName());

        if (isUnionSummon) { // 합체소환 : 데미지 x, 쿨다운 x, 상태효과만 적용
            SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(summonMove.getBaseStatusEffects());
            return resultMapper.toUnionSummonResult(summonMove, null, setStatusEffectResult);
        }

        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(summonMove);
        // 상태효과 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(summonMove.getBaseStatusEffects());
        // 쿨타임 적용
        leaderCharacter.updateSummonCoolDown(summonMove.getCoolDown(), summonIndex);

        return resultMapper.toResult(summonMove, damageLogicResult, setStatusEffectResult);
    }
}
