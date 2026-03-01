package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
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
public class KengoLogic extends DefaultCharacterMoveLogic {

    private final String gid = "220301";

        protected KengoLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 오륜검: 적에게 4.5배 데미지, 아군 전체의 오의게이지 10% 상승. 자신에게 오의게이지 상승률 50% 증가, 자신을 제외한 아군 전체에 오의게이지 상승률 30% 증가 효과
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 사기 향상: 참전자 전체의 오의게이지 15% 상승
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 검선일여: 자신에게 검선일여 효과, 오의 게이지 50% 상승 ◆검선일여 효과중 반드시 연속공격, 트리플 어택 확률 상승, 크리티컬 확률 상승
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 비검 - 극일섬: 적에게 8배 데미지. 자신에게 카에시카타나 효과 ◆효과중 자신의 일반공격에 50% 추가 데미지 발생(특수항), 일반공격후 적에게 8배 데미지, CT 1 감소
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();
        List<BaseStatusEffect> toApplyEffect = effectsGroupByApplyOrder.get(0); // 기본 카에시카타나 효과

        if (checkCondition.triggered(request)) {
            // 트리거시 카에시카타나 효과 있다면 추가효과로 변경, 없다면 빈결과
            toApplyEffect = checkCondition.hasEffect(ability.getActor(), "카에시카타나")
                    .map(effect -> effectsGroupByApplyOrder.get(1))
                    .orElse(null);
            if (toApplyEffect == null) return resultMapper.emptyResult();
        }

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffect)));
    }

    // 무명참: 자신의 오의게이지를 40% 소모하여 자신에게 3회공격 효과
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 40) throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.");
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 운요: 자신의 오의 게이지 최대치를 200으로 변경, 자신이 오의 발동시 운요 레벨 1 상승 (최대 Lv5)
    // ◆ 레벨 당 공격력 10%, 더블어택 확률 3%, 트리플 어택 확률 3%, 오의 데미지 5% 가 상승하는 상태 [제거불가]
    // <수정> 서포트 어빌리티 1 '심공' 의 효과를 통합 (오의게이지 200)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK)) return  resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 부동지신: 턴 종료시 해당 턴에 아군 전체가 발동한 오의 횟수가 2회 이상일시, 발동한 오의 횟수에 비례해 다음의 강화효과를 순서대로 부여 ( 최대 5회까지, 공격력과 방어력 30% 상승 /  더블어택과 트리플어택 확률 15% 상승 / 오의 게이지 10% 상승 / 오의 데미지 상한 10% 상승 )
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int chargeAttackCount = battleContext.getFrontCharacters().stream()
                .mapToInt(actor -> actor.getStatusDetails().getExecutedChargeAttackCount())
                .sum();
        if (chargeAttackCount < 2) return resultMapper.emptyResult();
        List<BaseStatusEffect> toApplyEffects = new ArrayList<>();
        ability.getBaseMove().getEffectsGroupByApplyOrder().forEach((key, value) -> {
            if (key <= chargeAttackCount) {
                toApplyEffects.addAll(value); // applyOrder 2, 3, 4, 5
            }
        });
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

}
