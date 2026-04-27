package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
@Component
public class FighterOriginLogic extends DefaultCharacterMoveLogic {

    private final String gid = "100501";

    protected FighterOriginLogic(CharacterMoveLogicDependencies dependencies) {
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

    // 무궁한 푸른검: 13.5배, 2턴간 자신에게 분할데미지(3회) 효과
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 웨폰 버스트 IV:  자신이 오의 즉시 사용가능, 2턴간 50% 추가데미지 효과 - 6턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 차지 컨버전스: 1턴간 참전자 전체의 트리플 어택 확률 30% 상승, 30% 추가데미지 효과 - 6턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_SELF] 돈 브레이크: 적에게 5.5배 데미지, 피격데미지 상승 / 1턴간 자신의 적대심 상승 - 7턴
    // 자신이 언리미티드 부스트 효과중, 일반공격 후 자동발동
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            if (!checkCondition.isMoveType(request.getOtherResult(), MoveType.NORMAL_ATTACK))
                return resultMapper.emptyResult();
            if (checkCondition.hasEffect(ability.getActor(), "언리미티드 부스트").isEmpty())
                return resultMapper.emptyResult();
        }

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 언리미티드 부스트: 자신에게 2회행동, 언리미티드 부스트 효과
    // 효과중 일반공격 데미지 100% 상승,
    // 투심레벨 5일때만 사용가능, 재사용불가
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.hasEffectLevel(ability.getActor(), "투심", 5).isEmpty())
            throw new MoveValidationException("투심레벨이 부족해 사용할 수 없습니다.", true);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 라이징 하트: 자신의 기초 더블어택 확률 100% [/] 턴 종료시 아군전체가 적에게 40회 이상 데미지를 입혔다면, 자신의 투심레벨 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.hasEffectLevel(ability.getActor(), "투심", 5).isPresent()) return resultMapper.emptyResult();

        int currentValue = TrackingConditionUtil.getInt(ability.getConditionTracker(), TrackingCondition.HIT_COUNT_BY_CHARACTER);
        int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), TrackingCondition.HIT_COUNT_BY_CHARACTER);
        if (currentValue < threshold) return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [BATTLE_START] 프라이드 오브 파이터: 기초 더블어택 100%, 상시분할데미지
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }
}
