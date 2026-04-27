package com.gbf.granblue_simulator.battle.logic.move.character.dark;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Transactional
@Component
@Slf4j
public class OrorojyaiaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040536000";

    protected OrorojyaiaLogic(CharacterMoveLogicDependencies dependencies) {
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
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 아키에스 폴룩스: 데미지 적의 피격데미지 상승
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 미스티카 오럼: 4턴간 아군 전체의 공격력, 크리티컬 확률 상승 - 6턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_STRIKE_END] 이암 디 플라보: 8배 데미지, 공격력, 방어력 감소(누적) ◆인과간섭 레벨 5일때, 주인공이 공격행동 후 자동발동
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            Actor leaderCharacter = battleContext.getLeaderCharacter();
            if (leaderCharacter.isAlreadyDead()
                    || !request.getOtherResult().isFromActor(leaderCharacter)
                    || checkCondition.hasEffectLevel(ability.getActor(), "인과간섭", 5).isEmpty())
                return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 카 더 트랙트: 아군 전체의 어빌리티 쿨타임 1턴 단축, 주인공에게 렌덤 강화효과 ◆3회행동 / 오의 즉시사용가능 + 재발동 / 어빌리티 쿨타임 2턴 추가 단축 중 1 - 8턴
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Map<Integer, List<BaseStatusEffect>> groupedEffects = ability.getBaseMove().getEffectsGroupByApplyOrder();
        List<BaseStatusEffect> toApplyEffects = groupedEffects.get(0);

        int applyOrder = List.of(1, 2).get(ThreadLocalRandom.current().nextInt(2));
        toApplyEffects.addAll(groupedEffects.get(applyOrder));

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // [REACT_SELF] 시간과 인과의 쐐기: 자신이 어빌리티 사용시 인과간섭 레벨 1 상승
    // 레벨에 비례해 자신과 주인공의 공격데미지 4%, 트리플 어택 확률 10% 상승 (최대 5)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isUsedAbility(request.getOtherResult(), battleContext.getCommandAbilityId()))
            return resultMapper.emptyResult();
        if (checkCondition.hasEffectLevel(ability.getActor(), "인과간섭", 5).isPresent()) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }
}
