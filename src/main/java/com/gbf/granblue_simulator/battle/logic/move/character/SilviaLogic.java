package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional
@Slf4j
public class SilviaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040613000";

    protected SilviaLogic(CharacterMoveLogicDependencies dependencies) {
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

    // D - 오블리탈레이트: 적에게 4.5배 데미지, 강화 효과 1개 해제. 자신의 두번째 어빌리티 쿨타임 초기화
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        chargeAttack.getActor().getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // [REACT_SELF] 엣지 오브 엔포서: 적에게 9배 데미지, 공격력 감소(누적), 방어력 감소(누적), 랜덤 효과 1개 부여
    // <수정> 효과량 하향 기존 10 10 /40 40 -> 5 5 / 15 15, 랜덤 약화효과 2개 부여 -> 1개 부여 및 랜덤효과를 작열/공격력감소/방어력감소/명중률다운 으로 제한
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        // 자신의 첫번째 서포트 어빌리티 발동시 이 어빌리티가 자동발동
        if (checkCondition.triggered(request) && !checkCondition.isMoveType(request.getOtherResult(), MoveType.FIRST_SUPPORT_ABILITY)) return resultMapper.emptyResult();

        List<BaseStatusEffect> allEffects = ability.getBaseMove().getBaseStatusEffects();
        // 기본 효과
        List<BaseStatusEffect> toApplyEffects = allEffects.stream()
                .filter(effect -> effect.getApplyOrder() == 0)
                .collect(Collectors.toCollection(ArrayList::new));
        // 랜덤 효과
        List<BaseStatusEffect> randomEffects = allEffects.stream()
                .filter(effect -> effect.getApplyOrder() == 1)
                .toList();
         if (randomEffects.isEmpty()) throw new MoveProcessingException("[SiliviaLogic.firstAbility] has no random effect");
        toApplyEffects.add(randomEffects.get((int) (Math.random() * randomEffects.size())));

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // 크라이시스 퍼세큐트: 아군 전체의 오의 게이지 20% 상승, 체력을 2000 회복
    // <수정> 약화효과 2턴 단축 삭제
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 월드 컨빅션: 자신의 오의게이지 200% 상승, 피데미지 무효 (2회), 재공격 효과 부여 ◆자신이 단죄 레벨 5일때 사용시 자신의 첫번째, 두번째 어빌리티 쿨타임 초기화, 어빌리티 재사용 효과
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        List<BaseStatusEffect> selectedEffects = checkCondition.hasEffectLevel(ability.getActor(), "단죄", 5)
                .map(effect -> {
                    Move firstAbility = ability.getActor().getFirstMove(MoveType.FIRST_ABILITY);
                    firstAbility.updateCooldown(0);
                    firstAbility.clearCurrentTurnUseCount();
                    Move secondAbility = ability.getActor().getFirstMove(MoveType.SECOND_ABILITY);
                    secondAbility.updateCooldown(0);
                    secondAbility.clearCurrentTurnUseCount();
                    return ability.getBaseMove().getBaseStatusEffects();
                })
                .orElseGet(() -> ability.getBaseMove().getBaseStatusEffects().stream().filter(effect -> effect.getApplyOrder() == 0).toList());
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, selectedEffects)));
    }

    // [CHARACTER_STRIKE_ALL_END] 타천사의 감시자: 자신의 오의게이지 최대치가 200% 로 변경. 오의를 2회이상 사용한 아군의 모든 공격행동 종료시 자신의 오의 게이지 20% 상승, 첫번째 어빌리티가 자동발동.
    // <수정> 최대치 200% 는 베이스스텟으로 처리,
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (request.getOtherResult().getMainActor().getStatusDetails().getExecutedChargeAttackCount() < 2) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_END] 단죄의 사명: 아군이 5회 이상 오의를 사용한 턴 종료시 자신의 단죄레벨 1 상승, 세번째 어빌리티 쿨타임 1턴 단축 ◆단죄 레벨 당 아군 전체의 공격력(별항) 4%, 방어력 5%, 오의 데미지 상한 5%, 오의 공격 데미지 5%, 오의 공격데미지 10만 상승 (최대 Lv5)
    // <수정> 효과량 변경. 기존 공격력 4, 방어 10, 오의데미지 20, 오의상한 10, 오의특수상한 6, 오의 요다메 2만 가산 (오의 데미지 의 경우 없으므로 오의 공격데미지로 대체、특수상한 삭제 -> 나중에 오의가 너무 약하면 추가예정)
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int chargeAttackCount = battleContext.getAllCharacters().stream().mapToInt(actor -> actor.getStatusDetails().getExecutedChargeAttackCount()).sum();
        if (chargeAttackCount < 5) return resultMapper.emptyResult();

        ability.getActor().getFirstMove(MoveType.THIRD_ABILITY).modifyCooldown(-1);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }
}
