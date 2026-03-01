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

import static com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition.*;

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
        moveLogicRegistry.register(abilityKey(gid, 5), this::fifthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 광란노도: 자신에게 재행동 효과 / 아군 전체에 데미지 상한 상승 효과
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 웨폰 버스트: 자신의 공격력, 오의데미지 배율, 오의데미지 상한이 상승하고 오의를 즉시 사용가능
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 펠로시티 로어: 자신의 오의게이지를 30% 소모하여 아군 전체의 공격력, 연속공격확률 상승. 추격A 효과
    // <수정> 특기무기 -> 아군전체, 레이지4와 통합(데미지상한 제외)
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 30) throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.", true);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_TURN_END] 바나호그: 적에게 2배 데미지 4회. 방어력, 속성 방어력 감소 효과 ◆아군 공격 턴 종료시 아군의 데미지 히트수 합이 40회 이상일경우 자동발동
    // <수정> 검 도끼 양쪽 모두 효과적용
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            int hitCount = TrackingConditionUtil.getInt(ability.getConditionTracker(), HIT_COUNT_BY_CHARACTER);
            int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), HIT_COUNT_BY_CHARACTER);
            if (hitCount < threshold) return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 울프 헤진: 자신의 오의게이지를 100% 소모하여 자신에게 울프헤진 효과 부여, 자신의 모든 어빌리티 봉인
    // 효과중 공격력 상승(특수항), 방어력 상승, 추격 30%(특수항), 반드시 트리플 어택, 약화효과 내성 100% 상승
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (ability.getActor().getChargeGauge() < 100) throw new MoveValidationException("오의게이지가 부족하여 어빌리티를 사용할 수 없습니다.", true);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [SELF_STRIKE_END] 비스트 팽: 적에게 4배 데미지. 공격력 감소(누적), 방어력 감소(누적) 효과 / 자신에게 데미지 상한 상승 효과 ◆울프 헤진 효과중 일반공격 후 자동발동
    protected MoveLogicResult fifthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request) && !checkCondition.isMoveType(request.getOtherResult(), MoveType.NORMAL_ATTACK)) {
            return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 투쟁본능: 기본 공격력, 체력, 연속공격확률 상승
    // <구현> 기초스테이터스 상승, 상태효과 x , 결과 x ,잡 마스터피스 등을 반영, 공격력 20000, 최대 hp 20%, 더블어택
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        return resultMapper.emptyResult();
    }

    // [BATTLE_START] 트램블: 트리플 어택 수행시 공격데미지 10000 상승
    // <구현> 패시브
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
