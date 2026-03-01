package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
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
public class ShalemSwimsuitLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040351000";

        protected ShalemSwimsuitLogic(CharacterMoveLogicDependencies dependencies) {
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

    // 케이오스 임펙트: 적에게 수속성 4.5배 데미지, 강화효과 1개 제거 ◆적에게 부여된 약화효과 1개 당 데미지 배율 0.5배 가산 (최대 5배 가산)
    // <수정> 슬로우 효과 삭제
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        long debuffCount = battleContext.getEnemy().getStatusEffects().stream().filter(statusEffect -> statusEffect.getBaseStatusEffect().getType() == StatusEffectType.DEBUFF).count();
        double damageRate = chargeAttack.getBaseMove().getDamageRate() + (0.5 * Math.min(debuffCount, 10));
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.withDamageRate(chargeAttack,  damageRate)));
    }

    // 트레파스: 적에게 공격력, 방어력 감소(누적) 효과, 랜덤 약화효과 1개 부여. 자신의 오의 게이지 10% 감소
    // 랜덤약화효과: 독, 부식, (매료) 마비-30%, (공포), (암흑), 특수기데미지 감소, 공격력 감소, 방어력 감소,
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 10) throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.");
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();
        // 기본 효과 : 오의게이지 감소, 공방깎
        List<BaseStatusEffect> toApplyEffects = effectsGroupByApplyOrder.get(0);
        // 랜덤 효과
        List<BaseStatusEffect> randomEffects = effectsGroupByApplyOrder.get(1);
        if (randomEffects.isEmpty()) throw new MoveProcessingException("[ShalemSwimsuitLogic.firstAbility] has no random effect");
        toApplyEffects.add(randomEffects.get((int) (Math.random() * randomEffects.size())));

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 카르마: 적에게 10배 데미지, 아군 전체의 어빌리티 쿨타임 3턴 단축, 모든 소환석 쿨타임 5턴 연장
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 하레퀴온: 적에게 장악 효과 (필중), 더블어택 확률 감소, 트리플 어택 확률 감소, 명중률 감소 효과
    // <수정> 효과량 감소 기존 더블/트리플 100% 감소 -> 50% 감소, 명중률은 30 동일
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 괄목하라 인간들이여: 자신의 최대 HP 가 감소, 공격력 상승(특수항), 반드시 연속공격, 트리플 어택 확률 증가, 오의게이지 상승률 증가
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [SELF_STRIKE_START] 끝없는 여름에 방황하는 미아: 적에게 부여된 약화효과의 갯수에 비례해 자신의 오의데미지 상한 상승. 자신의 공격행동 시작시 적에게 부여된 약화 효과가 10개 이상일때, 자신의 오의가 재발동
    // <구현> 공격 행동 시작시 버프 0턴 패시브 부여하도록 설정, 오의 데미지 증가는 오의에 배율증가로 추가
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        long debuffCount = battleContext.getEnemy().getStatusEffects().stream().filter(statusEffect -> statusEffect.getBaseStatusEffect().getType() == StatusEffectType.DEBUFF).count();
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();
        // 오의데미지/상한 상승
        List<BaseStatusEffect> toApplyEffects = effectsGroupByApplyOrder.get(0);
        if (debuffCount >= 10) {
            // 오의 재발동
            toApplyEffects.addAll(effectsGroupByApplyOrder.get(1));
        }
        defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects));
        return resultMapper.emptyResult(); // 패시브효과, 결과 반환 x
    }



}
