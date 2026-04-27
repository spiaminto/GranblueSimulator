package com.gbf.granblue_simulator.battle.logic.move.character.earth;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@Transactional
public class GalleonLogic extends DefaultCharacterMoveLogic {


    private final String gid = "3040405000";

    protected GalleonLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(abilityKey(gid, 4), this::fourthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        if (checkCondition.hasEffect(normalAttack.getActor(), "『금』의 쐐기").isPresent()
                && battleContext.getCurrentTurn() % 2 == 0
                && !checkCondition.triggered(request) // 턴 진행없이 일반공격 진행시, 일반공격 수행
        ) {
            return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 천지격진: 데미지, 2턴간 아군전체의 피격데미지 3000 감소
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 축복의 입맞춤: 자신과 주인공에게 재생, 암석 골짜기의 축복 효과 - 7턴
    // 효과중 공격력 50% 상승, 일반공격데미지 50,000 상승
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 내리는 바위: 적에게 6.0배 데미지 X 3회, 강화효과 1개 무효화 - 5턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 『금』의 쐐기: 자신에게 『금』의 쐐기 효과 - 재사용불가
    // 효과중 홀수턴 마다 공격력 대폭상승, 반드시 트리플어택, 홀수 턴에만 일반공격을 수행
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        saveTriggeredMove(List.of(ability.getActor()), triggerAbilityKey(gid, 1));

        Map<Integer, List<BaseStatusEffect>> groupedEffects = ability.getBaseMove().getEffectsGroupByApplyOrder();
        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(groupedEffects.get(0));
        if (battleContext.getCurrentTurn() % 2 != 0) {
            toApplyEffects.addAll(groupedEffects.get(1)); // 어쌔신, 트리플어택은 홀수턴만 적용해줌
        }

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 지모의 은총: 자신이 턴 진행없이 일반공격 수행 ◆짝수턴에도 수행
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        DefaultMoveLogicResult defaultResult = defaultAbility(ability);
        return resultMapper.toResult(ResultMapperRequest.builder()
                .move(ability)
                .setStatusEffectResult(defaultResult.getSetStatusEffectResult())
                .damageLogicResult(defaultResult.getDamageLogicResult())
                .executeOptions(ResultMapperRequest.ExecuteOptions.attack(StatusEffectTargetType.SELF))
                .build());
    }

    // [TURN_FINISH] 금의 쐐기 효과: 홀수턴마다 (짝수턴 최종 종료시) 자신에게 일반공격 가능 표시
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (battleContext.getCurrentTurn() % 2 != 0) return resultMapper.emptyResult();
        return checkCondition.hasEffect(ability.getActor(), "『금』의 쐐기")
                .map(effect -> resultMapper.fromDefaultResult(defaultAbility(ability)))
                .orElseGet(() -> {
                    moveService.delete(ability);
                    return resultMapper.emptyResult();
                });
    }

    // [REACT_CHARACTER] 이외의 『금』: 페이탈체인 발동시 약화효과 1개 제거, 아군전체의 체력 10,000회복
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.FATAL_CHAIN))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
