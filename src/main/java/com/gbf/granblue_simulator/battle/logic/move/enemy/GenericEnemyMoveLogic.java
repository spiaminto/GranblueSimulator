package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Transactional
public class GenericEnemyMoveLogic extends DefaultEnemyMoveLogic {

    protected GenericEnemyMoveLogic(EnemyMoveLogicDependencies enemyMoveLogicDependencies) {
        super(enemyMoveLogicDependencies);
        registerLogics();
    }

    protected void registerLogics() {
        // 로직명은 임시값. 별도로 관리 예정
        moveLogicRegistry.register("enemy_reflect", this::reflect);
    }

    // [REACT_PARTY_MEMBERS] 적의 리플렉트
    protected MoveLogicResult reflect(MoveLogicRequest request) {
        Move move = request.getMove();
        if (move.getType().getParentType() == MoveType.SUMMON) return resultMapper.emptyResult(); // 솬석에는 반응 안함
        
        MoveLogicResult otherResult = request.getOtherResult();
        Actor character = otherResult.getMainActor();
        Actor self = battleContext.getEnemy();
        return checkCondition.hasEffect(self, "데미지 반사")
                .map(reflectEffect -> {
                    if (otherResult.getDamages().isEmpty()) return resultMapper.emptyResult();

                    double reflectDamage = reflectEffect.getBaseStatusEffect().getFirstModifier().getInitValue();
                    int reflectCount = Math.min(otherResult.getDamages().size(), reflectEffect.getLevel());
                    double totalReflectDamage = reflectDamage * reflectCount;
                    // 리플렉트 데미지 만큼 데미지를 입히는 데미지 효과 하나 만들어서 적용시킴
                    BaseStatusEffect damageEffect = BaseStatusEffect.builder()
                            .uniqueFrame(false)
                            .durationType(StatusDurationType.TURN)
                            .duration(0)
                            .name("데미지 반사")
                            .targetType(StatusEffectTargetType.PARTY_MEMBERS)
                            .statusModifiers(Map.of(
                                    StatusModifierType.ACT_DAMAGE,
                                    StatusModifier.builder()
                                            .type(StatusModifierType.ACT_DAMAGE)
                                            .value(totalReflectDamage)
                                            .build()))
                            .build();
                    SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.withEnemyTargets(List.of(damageEffect), List.of(character)));

                    //리플렉트 횟수만큼 리플렉트 효과 레벨 감소
                    SetStatusEffectResult reflectLevelDownResult = setStatusLogic.subtractStatusEffectLevel(self, reflectCount, reflectEffect);
                    if (reflectEffect.getLevel() > 0) {
                        setStatusEffectResult.merge(reflectLevelDownResult); // 없어질때는 효과 x
                    }

                    return resultMapper.toResult(ResultMapperRequest.of(move, setStatusEffectResult));
                }).orElseGet(resultMapper::emptyResult);
    }

}
