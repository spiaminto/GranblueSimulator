package com.gbf.granblue_simulator.battle.logic.move.character.earth;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sound.midi.Track;
import java.util.List;
import java.util.Objects;

@Slf4j
@Transactional
@Component
public class OliviaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040508000";

    protected OliviaLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 2), this::secondTriggerAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 폴른 슬래시: 데미지, 첫번째 어빌리티 쿨타임 1턴 단축
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        chargeAttack.getActor().getFirstMove(MoveType.FIRST_ABILITY).modifyCooldown(-1);
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 디퍼 댄 더스크: 5.0배 데미지, 6턴간 공 방 DA TA 다운 - 11턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 클로즈 오브 던: 자신과 자신의 다음 캐릭터가 어스름의 가호 효과 - 8턴
    // 공격력과 트리플어택 확률 25% 상승, 일반공격 후 토속성 5배 데미지
    // 사용후 이 어빌리티가 투게더 어즈 에버 로 변경
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();

        // 트리거 어빌리티 저장
        List<Actor> targets = battleContext.getFrontCharacters().stream()
                .filter(character -> Objects.equals(character.getCurrentOrder(), self.getCurrentOrder() + 1))
                .findAny()
                .map(character -> List.of(self, character))
                .orElse(List.of(self));
        saveTriggeredMove(targets, triggerAbilityKey(gid, 1));

        // 수행
        MoveLogicResult result = resultMapper.fromDefaultResult(defaultAbility(ability));

        // 무브 변경
        moveService.delete(ability);
        BaseMove nextBaseMove = baseMoveService.findByLogicId(abilityKey(gid, 4));
        Move nextMove = Move.fromBaseMove(nextBaseMove).mapType(MoveType.SECOND_ABILITY).mapActor(self);
        nextMove.updateCooldown(ability.getBaseMove().getCoolDown()); // 쿨다운 적용
        moveService.saveAll(List.of(nextMove));

        result.updateChangedMoveIdWithDeletedMoveId(nextMove.getId(), ability.getId());

        return result;
    }

    // [REACT_SELF] 어스름의 가호 효과
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ATTACK))
            return resultMapper.emptyResult();
        if (checkCondition.hasEffect(ability.getActor(), "어스름의 가호").isEmpty()) {
            moveService.delete(ability);
            return resultMapper.emptyResult();
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 앤드 포 에버: 1턴간 자신에게 추가데미지 30% 효과, 턴 진행없이 일반공격을 수행 - 6턴
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        DefaultMoveLogicResult defaultResult = defaultAbility(ability);
        return resultMapper.toResult(ResultMapperRequest
                .of(ability,
                        defaultResult.getDamageLogicResult(),
                        defaultResult.getSetStatusEffectResult(),
                        ResultMapperRequest.ExecuteOptions.attack(StatusEffectTargetType.SELF)));
    }

    // 투게더 어즈 에버: 자신과 자신 다음의 캐릭터의 어스름의 가호 효과 해제후, 어스름의 날개 효과
    // 공격력과 트리플어택 확률 100% 상승, 일반공격 후 토속성 5배 X 2회 데미지
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();

        List<Actor> targets = battleContext.getFrontCharacters().stream()
                .filter(character -> Objects.equals(character.getCurrentOrder(), self.getCurrentOrder() + 1))
                .findAny()
                .map(character -> List.of(self, character))
                .orElse(List.of(self));
        targets.forEach(target -> checkCondition.hasEffect(target, "어스름의 가호").ifPresent(effect -> setStatusLogic.removeStatusEffect(target, effect)));
        saveTriggeredMove(targets, triggerAbilityKey(gid, 2));

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    /*
    frame_6
:
ƒ ()
frame_14
:
ƒ ()
frame_41
:
ƒ ()
frame_55
:
ƒ ()
frame_122
:
ƒ ()
frame_153
:
ƒ ()
frame_154
:
ƒ ()
frame_155
:
ƒ ()
frame_156
:
ƒ ()
frame_185
:
ƒ ()
frame_186
:
ƒ ()
frame_187
:
ƒ ()
frame_189
:
ƒ ()
frame_191
:
ƒ ()
frame_193
:
ƒ ()
frame_194
:
ƒ ()
frame_196
:
ƒ ()
frame_198
:
ƒ ()
frame_199
:
ƒ ()
frame_220
:
ƒ ()
     */

    // [REACT_SELF] 어스름의 날개 효과
    protected MoveLogicResult secondTriggerAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ATTACK))
            return resultMapper.emptyResult();
        // 삭제불가 영속효과라 일단 삭제안함
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 봉인된 열쇠의 타천사: 턴 종료시, 자신이 트리플 어택을 사용했다면 분할데미지(2회) 효과
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        int currentValue = TrackingConditionUtil.getInt(ability.getConditionTracker(), TrackingCondition.TRIPLE_ATTACK_COUNT);
        int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), TrackingCondition.TRIPLE_ATTACK_COUNT);
        if (currentValue < threshold) return resultMapper.emptyResult();

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

}
