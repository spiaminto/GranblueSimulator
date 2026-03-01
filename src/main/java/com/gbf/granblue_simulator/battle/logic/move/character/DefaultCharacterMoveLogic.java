package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.move.mapper.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicCheckCondition;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRegistry;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.*;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType.ALL_PARTY_MEMBERS;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType.IMMORTAL;

@Primary
@Component
@Slf4j
@Transactional
public class DefaultCharacterMoveLogic {

    protected final BattleContext battleContext;

    protected final MoveLogicRegistry moveLogicRegistry;
    protected final MoveLogicCheckCondition checkCondition;

    protected final DamageLogic damageLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final OmenLogic omenLogic;

    protected final MoveService moveService;
    protected final BaseMoveService baseMoveService;

    protected final CharacterLogicResultMapper resultMapper;

    public DefaultCharacterMoveLogic(CharacterMoveLogicDependencies dependencies) {
        this.battleContext = dependencies.getBattleContext();
        this.resultMapper = dependencies.getResultMapper();
        this.damageLogic = dependencies.getDamageLogic();
        this.setStatusLogic = dependencies.getSetStatusLogic();
        this.chargeGaugeLogic = dependencies.getChargeGaugeLogic();
        this.moveLogicRegistry = dependencies.getMoveLogicRegistry();
        this.checkCondition = dependencies.getMoveLogicCheckCondition();
        this.omenLogic = dependencies.getOmenLogic();
        this.moveService = dependencies.getMoveService();
        this.baseMoveService = dependencies.getBaseMoveService();
    }

    /**
     * 지정된 캐릭터의 move 를 실행. <br>
     * 오의재발동시 CHARGE_ATTACK_DEFAULT, 턴 진행없이 공격 시 SINGLE_ATTACK 으로 진입
     *
     * @param move
     * @return
     */
    public MoveLogicResult processMove(Move move) {
        return this.processMove(move, null);
    }

    /**
     * 지정된 캐릭터의 move 를 실행. 반응용 <br>
     *
     * @param move
     * @param otherResult 없으면 null
     * @return
     */
    public MoveLogicResult processMove(Move move, MoveLogicResult otherResult) {

        battleContext.setCurrentMainActor(move.getActor());

        MoveType moveType = move.getType();
        final String moveLogicId = move.getBaseMove().getLogicId();
        MoveLogicResult result = switch (moveType.getParentType()) {
            case FATAL_CHAIN -> defaultFatalChain(move);
            case ATTACK, ABILITY, SUPPORT_ABILITY, CHARGE_ATTACK ->
                    moveLogicRegistry.get(moveLogicId).process(MoveLogicRequest.of(move, otherResult));
            default -> {
                log.warn("not valid move, moveType = {}", moveType);
                yield resultMapper.emptyResult();
            }
        };

        Enemy enemy = (Enemy) battleContext.getEnemy();
        if (!result.isEmpty() && enemy.getOmen() != null) {
            OmenResult omenResult = result.getOmenResult(); // 전조 처리전 전조상태
            omenLogic.updateOmenByOtherResult(enemy, result);

            OmenResult processedOmenResult = enemy.getOmen() == null
                    ? OmenResult.breakOmen(omenResult.getStandbyMoveType())
                    : OmenResult.from(enemy);
            result.updateOmenResult(processedOmenResult);
        }

        return result;
    }

    /**
     * 캐릭터사망 처리 <br>
     * 1. 캐릭터 사망여부 판별 및 '지금' 사망했을시 사망처리 <br>
     * 2. 캐릭터가 이미 사망햇는지 여부 판멸 및 이미 사망했을시 null 반환
     *
     * @param character 현재 reactingActor (반응 처리를 수행할 actor)
     * @return 사망시 MoveType.DEAD_DEFAULT, 사망 없을시 null
     */
    public MoveLogicResult processDead(Actor character) {
        log.info("[processDead] called, character = {}", character);
        MoveLogicResult deadResult = null; // 캐릭터가 불사 등으로 버틸경우 null 반환 가능
        if (character.isNowDead()) {

            battleContext.setCurrentMainActor(character);

            deadResult = this.defaultDead(character);
            deadResult = deadResult.isEmpty() ? null : deadResult;
        }
        return deadResult;
    }

    public MoveLogicResult guard(Actor character) {
        battleContext.setCurrentMainActor(character);
        return resultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(battleContext.getMainActor(), GUARD_DEFAULT)));
    }

    public MoveLogicResult strikeSealed(Actor character) {
        battleContext.setCurrentMainActor(character);
        return resultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(battleContext.getMainActor(), STRIKE_SEALED)));
    }

    // =================================================================================================

    protected DefaultMoveLogicResult defaultAttack(Move move) {
        return this.defaultAttack(DefaultMoveRequest.from(move));
    }

    /**
     * 기본적인 공격처리<br>
     *
     * @param request
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAttack(DefaultMoveRequest request) {
        Move normalAttack = request.getMove();
        int hitCount = getNormalAttackCount(normalAttack);
        double damageRate = request.getModifiedDamageRate() != null ? request.getModifiedDamageRate() : normalAttack.getBaseMove().getDamageRate();
        DamageLogicResult damageLogicResult = processDamage(normalAttack, damageRate, hitCount);

        SetStatusEffectResult setStatusEffectResult = null;
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : normalAttack.getBaseMove().getBaseStatusEffects();
        if (!toApplyEffects.isEmpty()) { // 대부분 없으므로 미리거름
            setStatusEffectResult = setStatusLogic.setStatusEffect(normalAttack.getBaseMove().getBaseStatusEffects());
        }

        return DefaultMoveLogicResult.builder().resultMove(normalAttack).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }


    protected DefaultMoveLogicResult defaultChargeAttack(Move move) {
        return defaultChargeAttack(DefaultMoveRequest.from(move));
    }

    /**
     * 기본적인 오의 처리
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 선택된 스테이터스 추가 -> 오의게이지 갱신
     *
     * @return DefaultResult
     */
    protected DefaultMoveLogicResult defaultChargeAttack(DefaultMoveRequest request) {
        Move chargeAttack = request.getMove();
        Actor self = chargeAttack.getActor();
        BaseMove baseMove = chargeAttack.getBaseMove();

        // 오의 배율 변경확인
        Double modifiedDamageRate = request.getModifiedDamageRate();
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : baseMove.getDamageRate();

        // 데미지 계산
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(chargeAttack, damageRate, baseMove.getHitCount());

        // 스테이터스 적용
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : baseMove.getBaseStatusEffects();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.builder().baseStatusEffects(toApplyEffects).toLevel(request.getToEffectLevel()).build());

        // 오의게이지 -> 게이지 증가를 나중에
        chargeGaugeLogic.afterChargeAttack();

        return DefaultMoveLogicResult.builder().resultMove(chargeAttack).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }

    protected DefaultMoveLogicResult defaultAbility(Move move) {
        return defaultAbility(DefaultMoveRequest.from(move));
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화, 히트수 변화, 스테이터스 변화 모두 있음)
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAbility(DefaultMoveRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        BaseMove baseMove = ability.getBaseMove();

        // 데미지 배율, 히트수 변경확인
        Double modifiedDamageRate = request.getModifiedDamageRate();
        double damageRate = modifiedDamageRate != null ? modifiedDamageRate : baseMove.getDamageRate();
        Integer modifiedHitCount = request.getModifiedHitCount();
        int hitCount = modifiedHitCount != null ? modifiedHitCount : baseMove.getHitCount();

        // 데미지 계산
        MoveType abilityType = ability.getType();
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                damageLogic.processPartyDamage(ability, damageRate, hitCount) : null;

        // 스테이터스 적용
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : baseMove.getBaseStatusEffects();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.builder().baseStatusEffects(toApplyEffects).toLevel(request.getToEffectLevel()).build());

        // 쿨다운, 사용횟수 설정 (커맨드 수행시에만 설정)
        if (abilityType.getParentType() == MoveType.ABILITY && ability.getId().equals(battleContext.getCommandAbilityId())) {
            ability.increaseUseCount();
            if (ability.getUseCount() >= ability.getActor().getStatusDetails().getMaxAbilityUseCount()) {
                ability.updateCooldown(ability.getBaseMove().getCoolDown());
            }
        }

        // 참전자 상태효과 확인
        if (self.getBaseActor().isLeaderCharacter()) { // 참전자 버프는 주인공만 제한적으로 사용
            List<BaseStatusEffect> forAllBaseStatusEffects = toApplyEffects.stream()
                    .filter(baseStatusEffect -> baseStatusEffect.getTargetType() == ALL_PARTY_MEMBERS)
                    .toList(); // 참전자버프 + 일반버프 섞여있는경우도 있으므로 분리해서 등록
            if (!forAllBaseStatusEffects.isEmpty())
                registerForAllMove(ability);
        }
        return DefaultMoveLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }

    protected int getNormalAttackCount(Move move) {
        Actor self = move.getActor();
        return Math.random() < self.getStatus().getTripleAttackRate()
                ? 3
                : Math.random() < self.getStatus().getDoubleAttackRate()
                ? 2 : 1;
    }

    protected DamageLogicResult processDamage(Move move) {
        return this.processDamage(move, move.getBaseMove().getDamageRate(), move.getBaseMove().getHitCount());
    }

    protected DamageLogicResult processDamage(Move move, double damageRate, int hitCount) {
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(move, damageRate, hitCount);
        if (move.getType().getParentType() == ATTACK || move.getType().getParentType() == CHARGE_ATTACK) {
            chargeGaugeLogic.afterNormalAttack(hitCount);
        }
        return damageLogicResult;
    }

    protected SetStatusEffectResult processSetStatusEffect(Move move) {
        return processSetStatusEffect(move.getBaseMove().getBaseStatusEffects(), 0);
    }

    protected SetStatusEffectResult processSetStatusEffect(List<BaseStatusEffect> toApplyEffects, int toLevel) {
        return setStatusLogic.setStatusEffect(SetEffectRequest.builder()
                .baseStatusEffects(toApplyEffects)
                .toLevel(toLevel)
                .build());
    }

    /**
     * 쿨타임, 사용횟수 설정 (커맨드 수행시에만 설정)
     * @param ability
     */
    protected void postProcessAbility(Move ability) {
        MoveType abilityType = ability.getType();
        if (abilityType.getParentType() == MoveType.ABILITY && ability.getId().equals(battleContext.getCommandAbilityId())) {
            ability.updateCooldown(ability.getBaseMove().getCoolDown());
            ability.increaseUseCount();
        }
    }


    /**
     * 참전자 효과 등록 <br>
     * CHECK BaseMove 로 등록중
     *
     * @param move 참전자 효과가 들어있는 move
     */
    protected void registerForAllMove(Move move) {
        Member sourceMember = move.getActor().getMember();
        Room room = sourceMember.getRoom();
        List<Member> targetMembers = room.getMembers().stream()
                .filter(member -> !sourceMember.getId().equals(member.getId()))
                .toList();
        if (targetMembers.isEmpty()) return; // 동기화 대상 없음

        targetMembers.forEach(member -> {
            member.getPendingForAllMoves().add(Member.PendingForAllMove.builder()
                    .moveId(move.getBaseMove().getId())
                    .sourceMemberId(sourceMember.getId())
                    .sourceUsername(sourceMember.getUser().getUsername())
                    .build());
            // move 단위로 저장해놓고, 플레이어 각각 필요한경우 사용 및 초기화
        });
    }

    /**
     * 페이탈 체인 처리 <br>
     * 캐릭터 concrete logic 을 타지 않고 여기서 처리 종결 <br>
     * battleContext.mainActor 을 주체로 실행하지만, 데미지 고정 + 디버프 필중이기 때문에 주체의 상태에 영향받지 않음
     *
     * @param fatalChain
     * @return
     */
    protected MoveLogicResult defaultFatalChain(Move fatalChain) {
        DamageLogicResult damageLogicResult = damageLogic.processPartyDamage(fatalChain);
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(fatalChain.getBaseMove().getBaseStatusEffects());
        chargeGaugeLogic.setFatalChainGauge(0); // 페이탈 체인 게이지 초기화
        return resultMapper.toResult(ResultMapperRequest.of(fatalChain, damageLogicResult, setStatusEffectResult));
    }

    /**
     * 캐릭터 사망처리
     *
     * @param mainActor
     * @return MoveType.DEAD_DEFAULT, 불사 등의 상태효과로 사망처리가 취소됬을 시 MoveType.NONE
     */
    protected MoveLogicResult defaultDead(Actor mainActor) {
        StatusEffect immortalStatus = getEffectByModifierType(mainActor, IMMORTAL).orElse(null);
        if (immortalStatus != null) {
            setStatusLogic.removeStatusEffect(mainActor, immortalStatus); // 불사효과 삭제
            mainActor.updateHp(1); // 체력 1
            return resultMapper.emptyResult(); // 안해도됨 (프론트처리 없음)
        } else {
            // 사망처리 -> currentOrder 로 뒤로 보내서 front 에서 제외시킴
            Integer deadActorCurrentOrder = mainActor.getCurrentOrder();
            mainActor.updateCurrentOrder(deadActorCurrentOrder + 100);
            // 체력 변경
            mainActor.updateHp(Integer.MIN_VALUE);
            // 컨텍스트 갱신
            battleContext.frontCharacterDead(mainActor);
            // 결과 반환
            Move deadMove = Move.getTransientMove(mainActor, DEAD_DEFAULT);
            return resultMapper.toResult(ResultMapperRequest.from(deadMove));
        }
    }

    /**
     * 타겟에 logicId 로 조회한 트리거 무브를 저장
     */
    protected void saveTriggeredMove(List<Actor> targets, String moveLogicId) {
        BaseMove baseMove = baseMoveService.findByLogicId(moveLogicId);
        List<Move> triggeredMoves = new ArrayList<>();
        targets.forEach(character -> triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(character)));
        moveService.saveTriggeredMoves(triggeredMoves);
    }

    // 헬퍼
    protected String abilityKey(String gid, int order) {
        return "ab_" + gid + "_" + order;
    }

    protected String supportAbilityKey(String gid, int order) {
        return "sa_" + gid + "_" + order;
    }

    protected String chargeAttackKey(String gid) {
        return "nsp_" + gid;
    }

    protected String normalAttackKey(String gid) {
        return "phit_" + gid;
    }

    protected String triggerAbilityKey(String gid, int order) {
        return "tr_" + gid + "_" + order;
    }

}
