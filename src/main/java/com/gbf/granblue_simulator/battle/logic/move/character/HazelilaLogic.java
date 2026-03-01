package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional
@Slf4j
public class HazelilaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040168000";

    protected HazelilaLogic(CharacterMoveLogicDependencies dependencies) {
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
        // moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        // moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggeredAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 이루지온 포르몬트: 적에게 4.5배 데미지. 아군전체에 달빛 효과 부여. 자신의 세번째 어빌리티의 쿨타임 초기화
    // <수정> 연장을 없애고, 달빛효과의 효과턴을 4턴으로 고정. 재부여시 4턴으로 초기화 및 레벨상승, / 달빛 효과: 레벨 당 공격력(별항) 10%, 방어력 10%, 화속성 데미지 경감 5%, 화속성 데미지 컷 5% 가 상승하고, 일반공격에 10% 만큼 추가데미지(서포트 항)가 발생하는 상태 (최대 Lv3) [해제불가]
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        Move thirdAbility = chargeAttack.getActor().getFirstMove(MoveType.THIRD_ABILITY);
        thirdAbility.updateCooldown(0);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 보아즈: 적에게 1배 데미지 5회, 월영의 환각 효과 부여. 아군 전체의 체력을 4000 회복 ◆월영의 환각 효과중 레벨 당 자신의 공격력, 방어력, 약화 효과 내성, 명중률 감소 (최대 3Lv)
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 야힌: 자신과 남은 체력 비율이 가장 낮은 아군에게 피데미지 무효 (1회), 마운트, 베리어 효과 부여
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 페이즈 오브 더 문: 아군전체의 오의 게이지 15% 상승, 수속성 공격력 상승, 월광의 눈물 효과 부여
    // <수정> 월광의 눈물에서 어빌리티항 추격 삭제
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        DefaultMoveLogicResult defaultResult = defaultAbility(ability);

        checkCondition.hasEffect(ability.getActor(), "토라의 서").ifPresent(effect -> {
            if (ability.getCurrentTurnUseCount() <= 1) {
                ability.updateCooldown(0);
            }
        });
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 루나틱 벤데타: 자신에게 월광의 거울빛 효과 부여 ◆효과중 오의 재발동, 아군이 연속 공격시 적에게 1.5배 데미지 (달빛 효과 레벨 당 1히트) [선 쿨타임 10턴, 재사용 불가]
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        saveTriggeredMove(List.of(ability.getActor()), triggerAbilityKey(this.gid, 1));
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_STRIKE_END] 월광의 거울빛 효과: 아군이 연속 공격시 적에게 1.5배 데미지 (달빛 효과 레벨 당 1히트)
    protected MoveLogicResult firstTriggeredAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        MoveLogicResult otherStrikeResult = request.getOtherResult();
        if (!checkCondition.isNormalAttackAndAttackCountGreaterThan(otherStrikeResult, 1)) {
            return resultMapper.emptyResult();
        }

        return checkCondition.hasEffect(ability.getActor(), "달빛")
                .map(effect -> {
                    int hitCount = effect.getLevel();
                    return resultMapper.fromDefaultResult(
                            defaultAbility(DefaultMoveRequest.withHitCount(ability, hitCount)));
                })
                .orElseGet(resultMapper::emptyResult);
    }

    // 달바다의 깃옷: 아군이 달빛 레벨에 비례해 방어성능 상승
    // <수정> 애초에 달빛 효과에 해당 효과 가산 및 삭제
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.emptyResult();
    }

    // 여왕의 정위치: 전투 시작시 자신에게 토라의 서 효과 부여 ◆효과중 자신의 세번째 어빌리티를 1턴에 2회 사용가능
    // <수정> 루나틱 벤데타 선쿨타임 여기서 적용
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Move fourthAbility = ability.getActor().getFirstMoveByLogicId(abilityKey(this.gid, 4));
        if (fourthAbility != null) {
            fourthAbility.updateCooldown(10);
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 달의 거울: 아군이 달빛 레벨에 비례해 추격
    // <수정> 애초에 달빛 효과에 해당 효과 가산 및 삭제
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.emptyResult();
    }

}
