package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Transactional
@Slf4j
public class GabrielLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040492000";

    protected GabrielLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggeredAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 타이달 워드: 적에게 4.5배 데미지. 아군 전체에 수속성 공격력 상승, 현재 진행 턴 중 마운트 효과
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 저지멘트 스피어: 적에게 수속성 1배 데미지 5회, 정화의 격류 효과
    // ◆정화의 격류 효과: 자신의 공격력과 방어력이 10% 감소(하한 무시), 피격 데미지가 2000 상승하는 상태
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 옐로우 얀: 아군 전체에 화속성 데미지 70% 컷, 격류의 보호막 효과
    // ◆격류의 보호막: 일반공격에 20% 추가데미지(특수항) 발생, 방어력 50% 상승, TA 확률 50% 상승, 화속성 피격 데미지 10000 고정 [3턴 피데미지시 해제]
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        // 격류의 보호막 효과 트리거 무브 추가
        BaseMove baseMove = baseMoveService.findByLogicId(triggerAbilityKey(gid, 1));
        List<Move> triggeredMoves = new ArrayList<>();
        battleContext.getFrontCharacters().forEach(character -> {
            triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character));
        });
        moveService.saveTriggeredMoves(triggeredMoves);

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END]<STATUS_POST> 격류의 보호막 효과: 피격된 턴 종료시 자신의 격류의 보호막 효과 레벨 1 감소, 효과레벨이 0 이 되면 효과 삭제
    protected MoveLogicResult firstTriggeredAbility(MoveLogicRequest request) {
        Move move = request.getMove();
        return checkCondition.hasEffect(move.getActor(), "격류의 보호막")
                .map(statusEffect -> {
                    Map<TrackingCondition, Object> conditionTracker = move.getConditionTracker();
                    int hitCount = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.HIT_COUNT_BY_ENEMY);
                    if (hitCount <= 0) return resultMapper.emptyResult();

                    SetStatusEffectResult subtractResult = setStatusLogic.subtractStatusEffectLevel(move.getActor(), 1, statusEffect);
                    if (statusEffect.getActor() == null) {
                        moveService.delete(move);
                    }
                    return resultMapper.toResult(ResultMapperRequest.of(move, subtractResult));
                }).orElseGet(resultMapper::emptyResult);
    }

    // [REACT_SELF] 말튜리온: 적에게 5배 수속성 데미지 2회, 강화효과 1개 해제, 빙결레벨 상승
    // <수정> 배율 8배 -> 5배
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        // 트리거 되었을때, 서포어비 2 로 인한 트리거가 아니면 발동하지 않음
        if (checkCondition.triggered(request) && !checkCondition.isTriggeredByLogicId(request.getOtherResult(), supportAbilityKey(this.gid, 2)))
            return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER] <STATUS> 파고스: 아군이 트리플 어택 시 자신의 파고스 레벨 1 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3))
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        return resultMapper.emptyResult();
    }

    // [REACT_CHARACTER] <ABILITY> 휴돌 아르케: 자신의 일반공격 후 파고스 레벨 4를 소비하여 자신의 세번째 어빌리티 자동 발동
    // 서포트 어빌리티 1보다 나중에 발동시키기 위해 트리거를 REACT_SELF 대신 REACT_CHARACTER 로 변경
    // <수정> 가호 상승 효과 삭제
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        // 자기자신 && 일반공격 확인
        if (!request.getOtherResult().getMainActor().getId().equals(ability.getActor().getId())
                || !checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ATTACK))
            return resultMapper.emptyResult();

        // 파고스 효과 확인
        return checkCondition.hasEffectLevel(ability.getActor(), "파고스", 4)
                .map(statusEffect -> {
                    SetStatusEffectResult subtractStatusResult = setStatusLogic.subtractStatusEffectLevel(ability.getActor(), 4, statusEffect);
                    return resultMapper.toResult(ResultMapperRequest.of(ability, subtractStatusResult));
                }).orElseGet(resultMapper::emptyResult);
    }

}
