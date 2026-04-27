package com.gbf.granblue_simulator.battle.logic.move.character.dark;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
@Slf4j
public class ShatoraSwimsuitLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040610000";

    protected ShatoraSwimsuitLogic(CharacterMoveLogicDependencies dependencies) {
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

    // 오심불란: 아군 전체에 추가데미지 10%,
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 되풀이해 생각하는 소: 5턴간 자신과 주인공이 반드시 트리플어택, 일반공격 데미지 상승 -8
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));

    }

    // [REACT_CHARACTER] 분사물통: 8.0배 데미지, 방어력 감소 ◆ 자신 또는 주인공이 트리플 어택시 자동발동 -8턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            if (!request.getOtherResult().isFromActor(ability.getActor())
                    && !request.getOtherResult().getMainActor().getBaseActor().isLeaderCharacter())
                return resultMapper.emptyResult();
            if (!checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3))
                return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 맹렬히 꿈꾸는 소: 3턴간 주인공의 방어성능 상승, 감싸기 효과 -7턴
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 해변의 소 신장: 턴 종료시, 자신과 주인공이 모두 오의를 사용했다면 왕자님을 생각하는 마음 레벨 1 상승
    // 왕자님을 생각하는 마음 레벨 당 공격력, 어빌리티 배율 20% 상승 (최대 Lv5)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        if (leaderCharacter.isAlreadyDead()
                || leaderCharacter.getStatusDetails().getExecutedChargeAttackCount() < 1
                || ability.getActor().getStatusDetails().getExecutedChargeAttackCount() < 1)
            return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 왕자님과 바캉스: 턴 종료시 자신과 주인공이 합쳐 4회 이상 행동했다면, 자신과 주인공의 공격력 대폭상승
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        if (leaderCharacter.isAlreadyDead()
                || leaderCharacter.getExecutedStrikeCount() + ability.getActor().getExecutedStrikeCount() < 4)
            return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
