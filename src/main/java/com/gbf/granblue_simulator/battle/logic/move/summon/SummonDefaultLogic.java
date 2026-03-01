package com.gbf.granblue_simulator.battle.logic.move.summon;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.mapper.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRegistry;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SummonDefaultLogic {

    protected final DamageLogic damageLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final CharacterLogicResultMapper resultMapper;
    protected final BattleContext battleContext;
    protected final MoveLogicRegistry logicRegistry;
    protected final OmenLogic omenLogic;

    public SummonDefaultLogic(CharacterMoveLogicDependencies dependencies) {
        this.damageLogic = dependencies.getDamageLogic();
        this.setStatusLogic = dependencies.getSetStatusLogic();
        this.resultMapper = dependencies.getResultMapper();
        this.battleContext = dependencies.getBattleContext();
        this.logicRegistry = dependencies.getMoveLogicRegistry();
        this.omenLogic = dependencies.getOmenLogic();
    }


    public MoveLogicResult processSummon(Move move) {
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        battleContext.setCurrentMainActor(leaderCharacter);
        MoveLogicResult result = logicRegistry.get(move.getBaseMove().getLogicId()).process(MoveLogicRequest.of(move, null));

        // 전조 처리
        Enemy enemy = (Enemy) battleContext.getEnemy();
        if (enemy.getOmen() != null) {
            OmenResult omenResult = result.getOmenResult(); // 전조 처리전 전조상태
            omenLogic.updateOmenByOtherResult(enemy, result);

            OmenResult processedOmenResult = enemy.getOmen() == null
                    ? OmenResult.breakOmen(omenResult.getStandbyMoveType())
                    : OmenResult.from(enemy);
            result.updateOmenResult(processedOmenResult);
        }

        return result;
    }

    /**
     * 기본 소환 처리, unionSummon 시에도 데미지 처리 됨
     *
     * @param move
     * @return
     */
    protected MoveLogicResult defaultSummon(Move move) {
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(move);
        // 상태효과 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(move.getBaseMove().getBaseStatusEffects());
        // 쿨타임 적용
        move.updateCooldown(move.getBaseMove().getCoolDown());
        return resultMapper.toResult(ResultMapperRequest.of(move, damageLogicResult, setStatusEffectResult));
    }
}
