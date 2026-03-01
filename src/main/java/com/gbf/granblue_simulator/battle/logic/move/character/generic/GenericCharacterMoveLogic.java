package com.gbf.granblue_simulator.battle.logic.move.character.generic;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.repository.BaseStatusEffectRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GenericCharacterMoveLogic extends DefaultCharacterMoveLogic {
    private final MoveService moveService;
    private final BaseStatusEffectRepository baseStatusEffectRepository;

    protected GenericCharacterMoveLogic(CharacterMoveLogicDependencies dependencies, MoveService moveService, BaseStatusEffectRepository baseStatusEffectRepository) {
        super(dependencies);
        this.moveService = moveService;
        this.baseStatusEffectRepository = baseStatusEffectRepository;
        registerLogics();
    }

    protected void registerLogics() {
        // 로직명은 임시값. 별도로 관리 예정
        moveLogicRegistry.register("generic_counter", this::counter);
        
        // 보류
        // moveLogicRegistry.register("generic_level_down_status_effect", this::levelDownStatusEffect);
        // moveLogicRegistry.register("generic_apply_status_effect", this::applyStatusEffect);
    }

    // 힐, 흡수, 카운터 등 각종 공용 Move 위치

    // [REACT_ENEMY] 카운터
    public MoveLogicResult counter(MoveLogicRequest request) {
        Move move = request.getMove();
        MoveLogicResult otherResult = request.getOtherResult();
        Actor self = move.getActor();
        return checkCondition.hasEffect(self, "카운터")
                .map(counterEffect -> {
                    if (otherResult.getMove().getBaseMove().getLogicId().equals("enemy_counter") // CHECK 카운터에 카운터 x, 더 좋은 방법 고민 필요
                            || !checkCondition.isDamagedByEnemy(otherResult, self)) return resultMapper.emptyResult();

                    int counterCount = (int) counterEffect.getBaseStatusEffect().getFirstModifier().getInitValue();
                    DefaultMoveLogicResult counterResult = defaultAbility(DefaultMoveRequest.withHitCount(move, counterCount));
                    return resultMapper.fromDefaultResult(counterResult);
                }).orElseGet(() -> {
                    moveService.delete(move);
                    return resultMapper.emptyResult();
                });
    }






















    // 보류된 Generic Move ================================================================================================================================
    // 사용시 특정 캐릭터 로직의 registerLogics 에서 genericCharacterMoveLogic::levelDownStatusEffect 등으로 등록

    // [RUNTIME] 자신이 STATUS_EFFECT_NAME 효과중 LEVEL_DOWN_CONDITION 의 값이 LEVEL_DOWN_THRESHOLD 이상 일시 레벨이 감소, 레벨 0 이 되면 해당 효과 제거
    /*
    //      아래와 같이 findByLogicIdTriggerType, TrackingCondition 추가하여 사용
//            BaseMove baseMove = baseMoveService.findByLogicId("generic_level_down_status_effect");
//            triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character)
//                    .mapTriggerType(TriggerType.TURN_FINISH).mapConditionTracker(Map.of(
//                            TrackingCondition.STATUS_EFFECT_NAME, "튜로스 아지리스",
//                            TrackingCondition.LEVEL_DOWN_CONDITION, TrackingCondition.HIT_COUNT_BY_ENEMY.name(),
//                            TrackingCondition.LEVEL_DOWN_THRESHOLD, 1,
//                            TrackingCondition.HIT_COUNT_BY_ENEMY, 0
//                    ))
     */
    public MoveLogicResult levelDownStatusEffect(MoveLogicRequest request) {
        Move move = request.getMove();
        Actor self = move.getActor();
        Map<TrackingCondition, Object> conditionTracker = move.getConditionTracker();
        String trackingStatusEffectName = TrackingConditionUtil.getString(conditionTracker, TrackingCondition.STATUS_EFFECT_NAME);
        return checkCondition.hasEffect(self, trackingStatusEffectName)
                .map(trackingEffect -> {
                    String trackingConditionNameToDelete = TrackingConditionUtil.getString(conditionTracker, TrackingCondition.LEVEL_DOWN_CONDITION);
                    int currentTrackingValue = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.valueOfOrDefault(trackingConditionNameToDelete));
                    int thresholdValue = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.LEVEL_DOWN_THRESHOLD);
                    if (currentTrackingValue < thresholdValue) return resultMapper.emptyResult();

                    TrackingConditionUtil.resetCondition(conditionTracker, TrackingCondition.valueOfOrDefault(trackingConditionNameToDelete));
                    SetStatusEffectResult subtractResult = setStatusLogic.subtractStatusEffectLevel(self, 1, trackingEffect); // 레벨 0 이 되면 제거 코드 포함
                    return resultMapper.toResult(ResultMapperRequest.of(move, subtractResult));
                }).orElseGet(() -> {
                    moveService.delete(move);
                    return resultMapper.emptyResult();
                });
    }

    // [RUNTIME] 자신의 APPLY_CONDITION 의 값이 APPLY_THRESHOLD 이상 일시 STATUS_NAME 효과 부여

    // 예시) 5턴마다 자신에게 재공격 효과 부여
    // APPLY_CONDITION : PASSED_TURN_COUNT
    // PASSED_TURN_COUNT : 로직에서 계산후 전달, int
    // APPLY_THRESHOLD : 5
    // STATUS_EFFECT_NAME: "재공격"
    // [TriggerType] : TURN_FINISHED (턴 종료시 1회 호출)
    // CHECK 현재 조건없이 무한 유지중, setStatusEffect() 는 대상 지정 없는 메서드임. (BaseStatusEffect.targetType 에 타겟 미리 지정됨)
    // 1. 자신이 XX 효과중 또는 적이 YY 했을때 등 부여조건이 다양해 일단 캐릭터 로직에서 조건 확인후 부여 예정.
    // 2. 마찬가지로 삭제도 XX 효과 해제시 또는 적이 YY 햇을때 등으로 해제 조건이 다양해 일단 구현하면서 확인하고 구조를 확정할 예정
    // 3. 효과 중복체크는 하지 않음 (무조건 재부여)
    public MoveLogicResult applyStatusEffect(MoveLogicRequest request) {
        Move move = request.getMove();
        Map<TrackingCondition, Object> conditionTracker = move.getConditionTracker();

        String trackingApplyCondition = TrackingConditionUtil.getString(conditionTracker, TrackingCondition.APPLY_CONDITION);
        int currentTrackingValue = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.valueOfOrDefault(trackingApplyCondition));
        int thresholdValue = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.APPLY_THRESHOLD);
        if (currentTrackingValue < thresholdValue) return resultMapper.emptyResult();

        long baseStatusEffectId = TrackingConditionUtil.getLong(conditionTracker, TrackingCondition.APPLY_STATUS_EFFECT_ID);
        return baseStatusEffectRepository.findById(baseStatusEffectId)
                .map(baseStatusEffect -> {
                    TrackingConditionUtil.resetCondition(conditionTracker, TrackingCondition.valueOfOrDefault(trackingApplyCondition));
                    SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(List.of(baseStatusEffect));
                    return resultMapper.toResult(ResultMapperRequest.of(move, setStatusEffectResult));
                }).orElseGet(resultMapper::emptyResult); // 효과 부여못하면 reset 안됨. 디버깅시 확인
    }


}
