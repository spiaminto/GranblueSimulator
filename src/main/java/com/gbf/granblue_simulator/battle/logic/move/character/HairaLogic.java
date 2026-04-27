package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HairaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040502000";

    protected HairaLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 근하신년: 데미지, 자신에게 2턴간 2회행동, 3턴간 오의 봉인
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 와룡봉희: 자신의 지보의 황성 레벨에 비례해 공격행동 횟수 증가 ◆지보의 황성 레벨 전부 소모 - 7턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        return checkCondition.hasEffect(self, "지보의 황성")
                .map(statusEffect -> {
                    StatusModifierType multiStrikeModifier = statusEffect.getLevel() >= 3 ? StatusModifierType.TRIPLE_STRIKE
                            : StatusModifierType.DOUBLE_STRIKE; // 스택 1~2: 2회, 3: 3회
                    List<BaseStatusEffect> selectedBaseStatusEffects = ability.getBaseMove().getBaseStatusEffects().stream() // !(버프 && 선택된 다회행동이 아닌경우)
                            .filter(status -> !(status.getType() == StatusEffectType.BUFF && status.getModifiers().get(multiStrikeModifier) == null))
                            .toList();
                    setStatusLogic.removeStatusEffect(self, statusEffect); // 지보의 황성 삭제
                    return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, selectedBaseStatusEffects)));
                })
                .orElseThrow(() -> new MoveValidationException("지보의 황성 스택이 부족해 어빌리티를 사용할 수 없습니다.", true));
    }

    // 운룡증변: 1턴간 자신이 감싸기, 회피율 증가 [/]4턴간 아군 전체에 재생효과 - 8턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 잠룡천상: 1턴간 자신 이외의 아군전체가 2회행동[/]4턴간 아군 전체의 공격성능 강화 - 9턴
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 진신궁의 주인: 트리플 어택확정, 일반공격으로 오의게이지 상승하지 않음 
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 지보의 황성: 아군이 2회 이상 행동할때마다 자신에게 지보의 황성 레벨 상승, 오의게이지 20% 증가
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        MoveLogicResult otherResult = request.getOtherResult();
        List<Actor> frontCharacters = battleContext.getFrontCharacters();
        long doubleStrikeCount = frontCharacters.stream()
                .mapToInt(character -> character.getStatusDetails().getEndStrikeCount())
                .filter(count -> count >= 2)
                .count();
        if (doubleStrikeCount == 0) return resultMapper.emptyResult();

        chargeGaugeLogic.modifyChargeGauge(ability.getActor(), 20);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [SELF_STRIKE_START] 동남동의 수호신: 자신이 공격행동시, 두번째 공격행동부터 자신에게 어쌔신 효과
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (!checkCondition.executedStrikeMoreThan(self, 1)) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
