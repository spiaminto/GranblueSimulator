package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EuropaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040190000";
    private final BaseMoveService baseMoveService;
    private final MoveService moveService;

    protected EuropaLogic(CharacterMoveLogicDependencies dependencies, BaseMoveService baseMoveService, MoveService moveService) {
        super(dependencies);
        this.baseMoveService = baseMoveService;
        this.moveService = moveService;
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
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 2), this::secondTriggerAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 토라스 블론테: 적에게 수속성 4.5배 데미지, 빙결 효과 부여. 아군 전체에 뒷별의 잔광 효과 부여
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 마나 블래스트: 적에게 수속성 2배 데미지 3회, 빙결 효과. 아군 전체의 체력 2000 회복, 약화 효과를 1개 회복 ◆ 적의 빙결 레벨 5 이상시, 데미지 횟수 2배
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return checkCondition.hasEffectLevel(battleContext.getEnemy(), "빙결", 5)
                .map(statusEffect -> resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withHitCount(ability, ability.getBaseMove().getHitCount() * 2))))
                .orElseGet(() -> resultMapper.fromDefaultResult(defaultAbility(ability)));
    }

    // 튜로스 아지리스: 아군 전체에 튜로스 아지리스 효과 부여 ◆ 효과중 공격력 상승 (15%, 별항) 피격 데미지를 화속성으로 변환, 화속성 데미지 50% 경감, 강화 효과가 무효화 되지 않음. 3턴 피데미지시 해제
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        // 튜로스 아지리스 효과의 트리거 무브 추가
        BaseMove baseMove = baseMoveService.findByLogicId(triggerAbilityKey(gid, 1));
        List<Move> triggeredMoves = new ArrayList<>();
        battleContext.getFrontCharacters().forEach(character -> {
            triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character));
        });
        moveService.saveTriggeredMoves(triggeredMoves);

        DefaultMoveLogicResult defaultResult = defaultAbility(ability);
        battleContext.getFrontCharacters().forEach(character -> {
            setStatusLogic.addStatusEffectsLevel(character, 3, StatusUtil.getEffectsByName(character, "튜로스 아지리스").toArray(new StatusEffect[0]));
        });

        return resultMapper.fromDefaultResult(defaultResult); // 기본 효과 처리 반환
    }

    // [TURN_END]<STATUS_POST> 튜로스 아지리스 효과: 피격된 턴 종료시 자신의 효과 레벨 1 감소, 효과레벨이 0이 되면 효과 삭제
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        Move move = request.getMove();
        return checkCondition.hasEffect(move.getActor(), "튜로스 아지리스")
                .map(statusEffect -> {
                    Map<TrackingCondition, Object> conditionTracker = move.getConditionTracker();
                    int hitCount = TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.HIT_COUNT_BY_ENEMY);
                    if (hitCount <= 0) return resultMapper.emptyResult();

                    SetStatusEffectResult subtractResult = setStatusLogic.subtractStatusEffectLevel(move.getActor(), 1, statusEffect);
                    if (statusEffect.getActor() == null) {
                        moveService.delete(move);
                    }
                    return resultMapper.toResult(ResultMapperRequest.of(move, subtractResult));
                }).orElseGet(resultMapper::emptyResult);
    }

    // 플레아데스: 적에게 수속성 7배 데미지. 자신에게 트리플 어택 확률 상승 (100%), 수속성 추격A (70%) 효과 ◆적의 빙결 레벨이 10일때, 난격 3히트 효과 추가
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        List<BaseStatusEffect> baseStatusEffects = new ArrayList<>();
        if (checkCondition.hasEffectLevel(battleContext.getEnemy(), "빙결", 10).isPresent()) {
            baseStatusEffects.addAll(ability.getBaseMove().getBaseStatusEffects()); // 난격효과 포함 (applyOrder = 1)
        } else {
            baseStatusEffects.addAll(ability.getBaseMove().getBaseStatusEffects().stream().filter(effect -> effect.getApplyOrder() == 0).toList());
        }
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, baseStatusEffects)));
    }

    // 에우크라톤 포스: 자신과 자신의 다음에 배치된 캐릭터에게 자애의 별빛 효과 [재사용 불가] ◆자신의 성화레벨이 10일때 사용가능
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.hasEffectLevel(self, "성화", 10).isEmpty()) throw new MoveValidationException("성화 레벨이 부족해 어빌리티를 사용할 수 없습니다.");

        // 자애의 별빛 효과 트리거 무브 추가
        List<Actor> selfAndNextCharacter = battleContext.getFrontCharacters().stream()
                .filter(character -> character.getId().equals(self.getId()) || character.getCurrentOrder().equals(self.getCurrentOrder() + 1)).toList();
        saveTriggeredMove(selfAndNextCharacter, triggerAbilityKey(gid, 2));

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [TURN_FINISH] 자애의 별빛 효과: 5턴마다 자애의 별빛 효과중인 캐릭터에게 재공격 효과 부여
    protected MoveLogicResult secondTriggerAbility(MoveLogicRequest request) {
        Move move = request.getMove();
        Map<TrackingCondition, Object> conditionTracker = move.getConditionTracker();
        return checkCondition.hasEffect(move.getActor(), "자애의 별빛")
                .filter(statusEffect -> TrackingConditionUtil.getInt(conditionTracker, TrackingCondition.PASSED_TURN_COUNT) >= 5)
                .map(statusEffect -> {
                    TrackingConditionUtil.resetCondition(conditionTracker, TrackingCondition.PASSED_TURN_COUNT);
                    return resultMapper.fromDefaultResult(defaultAbility(move));
                }).orElseGet(resultMapper::emptyResult); // 자애의 별빛 효과는 영속 / 해제불가
    }

    // [REACT_SELF] 성천의 성화: 자신이 트리플 어택 또는 어빌리티 사용시 성화레벨 1 상승. ◆성화 효과중 레벨에 비례해 자신의 공격력, 방어력, 회복력 상승. 수속성 추격 효과(서포트 항)
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        boolean isTripleAttack = checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3);
        boolean usedAbility = checkCondition.isUsedAbility(request.getOtherResult(), battleContext.getCommandAbilityId());
        boolean isEffectMaxLevel = checkCondition.isEffectMaxLevel(ability.getActor(), "성화");
        if (!isEffectMaxLevel && (isTripleAttack || usedAbility)) {
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        }
        return resultMapper.emptyResult();
    }

    // <기존> 신에게 사로잡힌 아름다운 공주: 아군이 수속성 공격 UP 효과 30% 가산, 효과중 데미지 상한 10% 상승
    // 신에게 사로잡힌 아름다운 공주: 트리플 어택시 적에게 1.5배 데미지 2회, 자신의 첫번째 어빌리티 쿨타임 1턴 감소
    // <수정> 기존 효과 대신 성천의 성화 효과 중 트리플 어택시 데미지를 이쪽으로 변경
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return checkCondition.isNormalAttackAndAttackCountIs(request.getOtherResult(), 3)
                ? resultMapper.fromDefaultResult(defaultAbility(ability))
                : resultMapper.emptyResult();
    }
}
