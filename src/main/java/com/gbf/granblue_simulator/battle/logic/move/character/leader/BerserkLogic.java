package com.gbf.granblue_simulator.battle.logic.move.character.leader;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@Slf4j
public class BerserkLogic extends DefaultCharacterMoveLogic {

    private final String gid = "100301";

    protected BerserkLogic(CharacterMoveLogicDependencies dependencies) {
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
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 광란노도: 3턴간 자신이 2회행동, 데미지상한 10% 상승
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 웨폰 버스트: 자신이 오의 즉시 사용가능 - 5턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 펠로시티 로어: 1턴간 참전자 전체의 트리플 어택 확률 50% 상승 - 5턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 울프 헤진: 4턴간 자신의 트리플어택 확률 상승, 울프헤진 효과 / 모든 어빌리티 봉인 - 6턴
    // 효과중 일반공격 데미지 50,000 상승
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 100)
            throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.", true);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [SELF_STRIKE_END] 비스트 팽: 적에게 5배 데미지, 방어력 감소(누적) 효과 - 7턴
    // ◆울프 헤진 효과중 일반공격 후 자동발동
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            if (!checkCondition.isMoveType(request.getOtherResult(), MoveType.NORMAL_ATTACK)
                    || checkCondition.hasEffect(ability.getActor(), "울프헤진").isEmpty()) {
                return resultMapper.emptyResult();
            }
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 투쟁본능: 기본 공격력, 체력, 연속공격확률 상승
    // 주인공 더블어택 0.4 -> 0.7, 트리플어택 0.2 -> 0.4, 공격력 15000 -> 20000, 체력 25000 -> 30000
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        return resultMapper.emptyResult();
    }

    // logicId = 5 로 변경됨
    // [CHARACTER_TURN_END] 바나호그: 적에게 2배 데미지 4회. 방어력, 속성 방어력 감소 효과 - 5턴
    // ◆아군 공격 턴 종료시 아군의 데미지 히트수 합이 40회 이상일경우 자동발동
    // <수정> 검 도끼 양쪽 모두 효과적용 -
//    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
//        Move ability = request.getMove();
//        if (checkCondition.triggered(request)) {
//            int hitCount = TrackingConditionUtil.getInt(ability.getConditionTracker(), HIT_COUNT_BY_CHARACTER);
//            int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), HIT_COUNT_BY_CHARACTER);
//            if (hitCount < threshold) return resultMapper.emptyResult();
//        }
//        return resultMapper.fromDefaultResult(defaultAbility(ability));
//    }

    // [BATTLE_START] 트램블: 트리플 어택 수행시 공격데미지 10000 상승
    // <구현> 패시브
//    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
//        Move ability = request.getMove();
//        return resultMapper.fromDefaultResult(defaultAbility(ability));
//    }

}
