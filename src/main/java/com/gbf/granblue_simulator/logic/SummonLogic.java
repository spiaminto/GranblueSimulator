package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.logic.actor.character.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.DamageLogic;
import com.gbf.granblue_simulator.logic.common.SetStatusLogic;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class SummonLogic {

    private final DamageLogic damageLogic;
    private final SetStatusLogic setStatusLogic;
    private final CharacterLogicResultMapper resultMapper;


    public ActorLogicResult processSummon(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move summonMove, boolean isUnionSummon) {
        // 데미지
        DamageLogicResult damageLogicResult = isUnionSummon
                ? null // 합체소환시 데미지 없음
                :damageLogic.process(mainActor, enemy, summonMove);
        // 스테이터스 적용
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, enemy, partyMembers, summonMove.getStatusEffects());
        // 쿨타임 적용
        // ..
        return resultMapper.toResult(mainActor, enemy, partyMembers, summonMove, damageLogicResult, setStatusResult);
    }
}
