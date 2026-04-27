package com.gbf.granblue_simulator.battle.logic.move.summon;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicCheckCondition;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.move.mapper.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRegistry;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SummonDefaultLogic {

    protected final BattleContext battleContext;

    protected final MoveLogicRegistry moveLogicRegistry;
    protected final MoveLogicCheckCondition checkCondition;

    protected final DamageLogic damageLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final OmenLogic omenLogic;

    protected final MoveService moveService;
    protected final BaseMoveService baseMoveService;

    protected final CharacterLogicResultMapper resultMapper;

    public SummonDefaultLogic(CharacterMoveLogicDependencies dependencies) {
        this.battleContext = dependencies.getBattleContext();
        this.resultMapper = dependencies.getResultMapper();
        this.damageLogic = dependencies.getDamageLogic();
        this.setStatusLogic = dependencies.getSetStatusLogic();
        this.chargeGaugeLogic = dependencies.getChargeGaugeLogic();
        this.moveLogicRegistry = dependencies.getMoveLogicRegistry();
        this.checkCondition = dependencies.getMoveLogicCheckCondition();
        this.omenLogic = dependencies.getOmenLogic();
        this.moveService = dependencies.getMoveService();
        this.baseMoveService = dependencies.getBaseMoveService();
    }


    public MoveLogicResult processSummon(Move move) {
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        battleContext.setCurrentMainActor(leaderCharacter);
        MoveLogicResult result = moveLogicRegistry.get(move.getBaseMove().getLogicId()).process(MoveLogicRequest.of(move, null));

        // 전조 처리
        Enemy enemy = (Enemy) battleContext.getEnemy();
        if (enemy.getOmen() != null) {
            OmenResult omenResult = result.getOmenResult(); // 전조 처리전 전조상태
            omenLogic.updateOmenByOtherResult(enemy, result);

            OmenResult processedOmenResult = enemy.getOmen() == null
                    ? OmenResult.breakOmen(omenResult)
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
        double damageRate = move.getBaseMove().getDamageRate();
        int hitCount = move.getBaseMove().getHitCount();
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processPartyDamage(move, damageRate, hitCount) : null;
        // 상태효과 적용
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(move.getBaseMove().getBaseStatusEffects());
        // 쿨타임 적용
        move.updateCooldown(move.getBaseMove().getCoolDown());
        return resultMapper.toResult(ResultMapperRequest.of(move, damageLogicResult, setStatusEffectResult));
    }

    /**
     * 트리거 어빌리티 처리용
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAbility(DefaultMoveRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        BaseMove baseMove = ability.getBaseMove();

        // 데미지 배율, 히트수 변경확인
        Double modifiedDamageRate = request.getModifiedDamageRate();
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : baseMove.getDamageRate();
        Integer modifiedHitCount = request.getModifiedHitCount();
        int hitCount = modifiedHitCount != null ? modifiedHitCount : baseMove.getHitCount();

        // 데미지 계산
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processPartyDamage(ability, damageRate, hitCount) : null;

        // 스테이터스 적용
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : baseMove.getBaseStatusEffects();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.builder().baseStatusEffects(toApplyEffects).targetLevel(request.getPlusLevel()).build());

        return DefaultMoveLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }

    /**
     * 타겟에 logicId 로 조회한 트리거 무브를 저장
     */
    protected void saveTriggeredMove(List<Actor> targets, String moveLogicId) {
        BaseMove baseMove = baseMoveService.findByLogicId(moveLogicId);
        List<Move> triggeredMoves = new ArrayList<>();
        targets.forEach(character -> triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character)));
        moveService.saveTriggeredMoves(triggeredMoves);
    }
}
