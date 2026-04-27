package com.gbf.granblue_simulator.battle.logic.move.character.dark;

import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Transactional
@Component
@Slf4j
public class ReiLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040265000";

    protected ReiLogic(CharacterMoveLogicDependencies dependencies) {
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
        if (checkCondition.hasEffect(normalAttack.getActor(), "해탈").isPresent()) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 오안장악-적광정토: 데미지 없음, 적의 강화효과 1개 제거, 약화내성과 연속공격확률 감소
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 아라야시키: 자신이 즉시 오의사용가능, 오의 재발동(1회) 효과 - 8턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 해탈: 자신에게 고양효과 [/] 자신의 다음애 배치된 캐릭터가 2회행동 ◆자신의 일반공격 중지 -재사용불가
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 백안시: 해제가능한 전조의 모든 값을 현재의 절반으로 감소 ◆1 이하로 줄어들지 않음
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Enemy enemy = (Enemy) battleContext.getEnemy();

        if (enemy.getOmen() != null) {
            List<Integer> remainValues = new ArrayList<>(enemy.getOmen().getRemainValues());
            List<Integer> cancelConditionIndexes = enemy.getOmen().getCancelConditionIndexes();

            List<Integer> modifiedValues = IntStream.range(0, cancelConditionIndexes.size())
                    .mapToObj(i -> {
                        return Math.max(1, remainValues.get(i) / 2);
                    })
                    .toList();
            omenLogic.manualUpdateOmenValue(enemy, modifiedValues);
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 하늘의 역수: 아군 전체의 어빌리티 쿨타임 3턴 단축 - 재사용불가
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_ENEMY] 아이를 지켜보는 어머니: 적이 특수기 사용후 아군 전체의 체력 10,000 회복
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK))
            return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }


}
