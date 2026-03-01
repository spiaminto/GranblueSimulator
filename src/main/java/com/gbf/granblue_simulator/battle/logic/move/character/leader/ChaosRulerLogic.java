package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@Transactional
@Slf4j
public class ChaosRulerLogic extends DefaultCharacterMoveLogic {

    private final String gid = "150301";

    protected ChaosRulerLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 변환자재: 적에게 4.5배 데미지, 아군 전체의 연속공격 확률 증가
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 딜레이: 적에게 6배 데미지, CT 1 감소, 공격력 감소(누적), 방어력 감소(누적)
    // <수정> 쿨타임 5 -> 6턴으로 증가
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 언프레딕트: 적의 공격력, 방어력 감소, 랜덤 약화효과 1개 부여
    // 랜덤약화: 더블어택확률 감소, 트리플 어택 확률 감소, 작열, 암흑 (전부 참전자)
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();
        // 기본 효과 : 공방깎
        List<BaseStatusEffect> toApplyEffects = effectsGroupByApplyOrder.get(0);
        // 랜덤 효과
        List<BaseStatusEffect> randomEffects = effectsGroupByApplyOrder.get(1);
        if (randomEffects.isEmpty())
            throw new MoveProcessingException("[ChaosRuler.secondAbility] has no random effect");
        toApplyEffects.add(randomEffects.get((int) (Math.random() * randomEffects.size())));

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 이노미티: 적이 이번턴에 가하는 속성데미지를 0으로 고정, 자신의 어빌리티를 2턴간 봉인
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [SELF_STRIKE_END] 브릭 디스오더: 적에게 4배 데미지 2회, 속성 방어력 감소, 연속공격확률 감소(누적) ◆적에게 부여된 약화효과가 10개 이상일때, 자신의 일반공격 후 자동발동
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            if (!checkCondition.isMoveType(request.getOtherResult(), MoveType.NORMAL_ATTACK)) return resultMapper.emptyResult();
            long debuffCount = battleContext.getEnemy().getStatusEffects().stream().filter(statusEffect -> statusEffect.getBaseStatusEffect().getType() == StatusEffectType.DEBUFF).count();
            if (debuffCount < 10) return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 프레임 오브 카오스: 적에게 다양한 약화효과 부여
    // 약화효과 내성 감소, 상실, 독, 감전, 화상, 빙결, 극독, 명중률감소(개인), 특수기데미지감소(개인), 피데미지 증가(개인)
    protected MoveLogicResult fifthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 데몰리쉬: 자신의 약화효과 성공률 50% 상승, 약화효과 내성 50% 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_TURN_START] 헤이스티 액션: 아군 공격턴 시작시 적에게 부여된 약화효과가 10개 이상일때, 자신에게 재행동 효과 부여
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        long debuffCount = battleContext.getEnemy().getStatusEffects().stream().filter(statusEffect -> statusEffect.getBaseStatusEffect().getType() == StatusEffectType.DEBUFF).count();
        if (debuffCount < 10) return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }
    
    // <수정> 서포트 어빌리티 어빌리티 쿨감 + 약체 어빌쿨감은 기존 어빌리티의 쿨타임을 감소시키는것으로 갈음

}
