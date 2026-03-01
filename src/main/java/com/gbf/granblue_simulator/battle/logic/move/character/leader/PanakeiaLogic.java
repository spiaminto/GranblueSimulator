package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Transactional
@Slf4j
public class PanakeiaLogic extends DefaultCharacterMoveLogic {


    private final String gid = "120401";

    protected PanakeiaLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(abilityKey(gid, 5), this::fifthAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 2), this::secondTriggerAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 페리스토레피타이: 적에게 4.5배 데미지, 강화효과 1개 삭제 / 아군전체에 피격데미지 감소 효과
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // [SELF_MOVE] 위타 블레위스: 참전자 전체의 HP 회복 (언데드, 강압 무시) / 아군 전체의 오의게이지 20% 상승, 스트렝스 효과, 부여된 약화 효과의 효과시간 1턴 단축 ◆자동발동시, HP 회복 대상을 아군 전체로 변경
    // 스트렝스 최대 20%
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Map<Integer, List<BaseStatusEffect>> effectsByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();
        List<BaseStatusEffect> toApplyEffects = effectsByApplyOrder.get(0); // 기본
        if (checkCondition.triggered(request)) {
            if (!checkCondition.isTriggeredByLogicId(request.getOtherResult(), triggerAbilityKey(gid, 1)))
                return resultMapper.emptyResult();
            toApplyEffects.addAll(effectsByApplyOrder.get(1)); // 아군 전체 힐
        } else {
            toApplyEffects.addAll(effectsByApplyOrder.get(2)); // 참전자 힐
        }

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 아미티코 포스: 아군전체의 트리플 어택 확률 상승, 디스펠가드(1회), 마운트 효과 / 자신에게 세랍틱스탠스 효과부여 
    // 수정: 쿨타임 감소
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        // 트리거 무브 추가
        BaseMove baseMove = baseMoveService.findByLogicId(triggerAbilityKey(gid, 1));
        Move triggerMove = Move.fromBaseMove(baseMove).mapActor(ability.getActor());
        moveService.saveTriggeredMoves(List.of(triggerMove));

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [ENEMY_STRIKE_END] (ABILITY_PRE) 세랍틱스탠스 효과: 적이 특수기 사용시 효과레벨을 1 소모해 자신의 첫번째 어빌리티 자동발동
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK))
            return resultMapper.emptyResult();

        return checkCondition.hasEffect(ability.getActor(), "세랍틱스탠스")
                .map(statusEffect -> {
                    SetStatusEffectResult subtractStatusResult = setStatusLogic.subtractStatusEffectLevel(ability.getActor(), 1, statusEffect);
                    return resultMapper.toResult(ResultMapperRequest.of(ability, subtractStatusResult)); // 첫번째 어빌리티 트리거됨
                }).orElseGet(resultMapper::emptyResult);
    }

    // 메가리포티아: 적에게 10배 데미지, 강화효과 3개 삭제 / 아군 전체의 약화효과를 모두 삭제하고 HP 를 전부 회복
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 프네우마: 아군 전체에 프네우마 효과 ◆턴 종료시 자신이 해당 턴에 체력회복 효과를 받았다면 쿨타임 1턴 단축
    // 효과중 공격력 50%, 방어력 50%, 더블어택 확률 100%, 트리플어택 확률 25% 상승, 일반공격에 20% 추가데미지 발생(특수항) 자신의 회복상한 25% 상승
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            int takenHealCount = TrackingConditionUtil.getInt(ability.getConditionTracker(), TrackingCondition.TAKEN_HEAL_EFFECT_COUNT);
            if (takenHealCount > 0) {
                ability.modifyCooldown(-1);
            }
            return resultMapper.emptyResult();
        }

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 카테드랄: 아군전체에 베리어 효과, 카테드랄 효과
    // 효과중 재생, 디스펠 가드, 피격데미지를 수속성으로 변환, 수속성 내성 상승 (2회 피데미지시 해제)
    protected MoveLogicResult fifthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        // 카테드랄 효과의 트리거 무브 추가
        BaseMove baseMove = baseMoveService.findByLogicId(triggerAbilityKey(gid, 2));
        List<Move> triggeredMoves = new ArrayList<>();
        battleContext.getFrontCharacters().forEach(character -> {
            triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character));
        });
        moveService.saveTriggeredMoves(triggeredMoves);

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] (STATUS_POST) 카테드랄 효과: 2회 피데미지시 해제
    protected MoveLogicResult secondTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return checkCondition.hasEffect(ability.getActor(), "카테드랄")
                .map(statusEffect -> {
                    Map<TrackingCondition, Object> conditionTracker = ability.getConditionTracker();
                    int hitCount = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.HIT_COUNT_BY_ENEMY);
                    if (hitCount <= 0) return resultMapper.emptyResult();

                    SetStatusEffectResult subtractResult = setStatusLogic.subtractStatusEffectLevel(ability.getActor(), 1, statusEffect);
                    if (statusEffect.getActor() == null) {
                        moveService.delete(ability);
                    }
                    return resultMapper.toResult(ResultMapperRequest.of(ability, subtractResult));
                }).orElseGet(resultMapper::emptyResult);
    }

    // [BATTLE_START] 조력의 파동: 자신의 회복상한 25% 상승, 아군 전체의 더블어택 확률 25%, 트리플 어택 확률 15% 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 파사의 파동: 자신이 반드시 트리플어택, 추격
    // <삭제>

    // [TURN_END] 치유의 파동: 턴 종료시 30% 확률로 아군전체의 약화효과를 1개 회복
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        boolean isActivated = Math.random() < 0.3;
        if (!isActivated) return resultMapper.emptyResult();

        long removableDebuffCount = battleContext.getFrontCharacters().stream().mapToLong(actor -> actor.getStatusEffects().stream()
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getType().isDebuff() && statusEffect.getBaseStatusEffect().isRemovable())
                .count()
        ).sum();
        if (removableDebuffCount <= 0) return resultMapper.emptyResult(); // 삭제 가능할때만 발동

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }


}
