package com.gbf.granblue_simulator.battle.logic.move.character.earth;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Slf4j
@Transactional
public class BaishuraLogic extends DefaultCharacterMoveLogic {


    private final String gid = "3040630000";

    protected BaishuraLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 약사여래의 법우: 데미지, 두번째 어빌리티가 자동발동
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 백년 루리광: 1배 X 12회 데미지, 방어력 감소(누적), 아군 오의게이지 20% 상승 -7턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 제병안락: 아군의 체력 1500 회복, 오의게이지 20% 상승 -7턴
    // ◆오의 사용후 자동발동
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request) && !checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 두루 비추는 광명: 아군의 방어성능 상승 (영속) -재사용불가
    // 선생보장 효과: 방어력 50% 증가, 오의 데미지 상한상승
    // ◆약사의 빛 5Lv 일때 사용가능
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.hasEffectLevel(ability.getActor(), "약사의 빛", 5).isEmpty())
            throw new MoveValidationException("약사의 빛 Lv5 에 도달하지 않아 어빌리티를 사용할 수 없습니다.", true);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER] 약사의 좌: 오의 게이지 최대치가 200% [/]자신을 제외한 아군이 오의 사용시 루리광 레벨 1 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (request.getOtherResult().isFromActor(ability.getActor())
                || !checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 영원의창광: 턴 종료시 루리광 레벨이 5일경우, 자신의 첫번째 어빌리티 쿨타임 초기화, 약사의 빛 레벨 1 상승
    // 약사의 빛 레벨에 비례해 아군전체의 오의데미지 배율 10%, 오의데미지 상한 4% 상승 최대 5Lv
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        Optional<StatusEffect> effectOptional = checkCondition.hasEffectLevel(self, "루리광", 5);
        if (effectOptional.isEmpty()) return resultMapper.emptyResult();

        StatusEffect effect = effectOptional.get();
        setStatusLogic.removeStatusEffect(self, effect); // 루리광 삭제
        self.getFirstMove(MoveType.FIRST_ABILITY).updateCooldown(0); // 쿨타임 초기화

        return resultMapper.fromDefaultResult(defaultAbility(ability)); // 쿨타임 초기화, 약사의 빛 레벨 상승
    }

}
