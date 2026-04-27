package com.gbf.granblue_simulator.battle.logic.move.character.earth;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
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

@Slf4j
@Transactional
@Component
public class SharlemChristmasLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040497000";

    protected SharlemChristmasLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 케이오스 일루미네이트: 극대 데미지, 아군 오의게이지 15% 증가 세번쨰 어빌리티 쿨타임 1턴 단축
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        chargeAttack.getActor().getFirstMove(MoveType.THIRD_ABILITY).modifyCooldown(-1);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 섀도우 슈라우드: 적에게 연속공격 확률 5% 다운 (누적), 최대 Lv5 -6턴
    // 자신이 란쥬란 필드 효과중 약화효과 부여횟수 2배
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(ability.getBaseMove().getBaseStatusEffects());
        checkCondition.hasEffect(ability.getActor(), "란쥬란 필드").ifPresent(effect -> {
            toApplyEffects.addAll(ability.getBaseMove().getBaseStatusEffects());
        });
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 도라 레기온: 8배 X 2회 데미지, 강화효과 1개 무효화 -7턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 란쥬란 필드: 자신에게 감싸기 효과, 란쥬란 필드 전개 ◆효과중 일반공격 중지, 피격 데미지 90% 컷, 턴 종료시 자신의 첫번째 두번째 어빌리티의 쿨타임 초기화 -8턴
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        saveTriggeredMove(List.of(ability.getActor()), triggerAbilityKey(gid, 1));
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 란쥬란 필드 효과: 턴 종료시 자신의 1, 2번째 어빌리티 쿨타임 초기화
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return checkCondition.hasEffect(ability.getActor(), "란쥬란 필드").map(effect -> {
            ability.getActor().getFirstMove(MoveType.FIRST_ABILITY).updateCooldown(0);
            ability.getActor().getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        }).orElseGet(() -> {
            moveService.delete(ability);
            return resultMapper.emptyResult();
        });
    }

    // 기억속의 축제: 자신이 반드시 트리플 어택
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }


}
