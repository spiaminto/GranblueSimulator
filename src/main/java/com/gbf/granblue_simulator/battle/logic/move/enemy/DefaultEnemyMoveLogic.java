package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicCheckCondition;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRegistry;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.move.mapper.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
import com.gbf.granblue_simulator.metadata.service.BaseEnemyService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.*;

@Component
@Transactional
@Slf4j
@Primary
public class DefaultEnemyMoveLogic {

    protected final BattleContext battleContext;

    protected final MoveLogicRegistry moveLogicRegistry;
    protected final MoveLogicCheckCondition checkCondition;

    protected final DamageLogic damageLogic;
    protected final SetStatusLogic setStatusLogic;
    protected final ChargeGaugeLogic chargeGaugeLogic;
    protected final OmenLogic omenLogic;

    protected final BattleLogService battleLogService;
    protected final MoveService moveService;
    protected final BaseMoveService baseMoveService;
    protected final BaseEnemyService baseEnemyService; // 가능한 baseActorService 대신 baseEnemyService 사용

    protected final EnemyLogicResultMapper resultMapper;

    protected DefaultEnemyMoveLogic(EnemyMoveLogicDependencies dependencies) {
        this.battleContext = dependencies.getBattleContext();
        this.resultMapper = dependencies.getResultMapper();
        this.damageLogic = dependencies.getDamageLogic();
        this.setStatusLogic = dependencies.getSetStatusLogic();
        this.chargeGaugeLogic = dependencies.getChargeGaugeLogic();
        this.moveLogicRegistry = dependencies.getMoveLogicRegistry();
        this.checkCondition = dependencies.getCheckCondition();
        this.omenLogic = dependencies.getOmenLogic();
        this.battleLogService = dependencies.getBattleLogService();
        this.moveService = dependencies.getMoveService();
        this.baseMoveService = dependencies.getBaseMoveService();
        this.baseEnemyService = dependencies.getBaseEnemyService();
    }

    protected void preProcess(Actor actor) {
        battleContext.setCurrentMainActor(actor);
    }

    /**
     * 턴 종료시 전조를 트리거
     *
     * @return
     */
    public MoveLogicResult triggerOmen(Move move) {
        return moveLogicRegistry.get(move.getBaseMove().getLogicId()).process(MoveLogicRequest.of(move, null));
    }

    /**
     * 공격 행동을 수행
     *
     * @return
     */
    public MoveLogicResult processStrike() {
        Enemy self = (Enemy) battleContext.getEnemy();
        preProcess(self);

        // 공격행동 봉인 여부 체크 및 반환
        double calcedStrikeSealed = self.getStatus().getStatusDetails().getCalcedStrikeSealed();
        boolean isStrikeSealed = Math.random() < calcedStrikeSealed;
        if (isStrikeSealed)
            return resultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(self, STRIKE_SEALED)));

        // 공격행동 결정 및 수행
        log.info("[processStrike] self.omen = {}", self.getOmen());
        if (self.getOmen() != null)
            log.info("chargeAttackType = {}", self.getFirstMove(self.getOmen().getStandbyType().getChargeAttackType()));
        return self.getOmen() != null ?
                processMove(self.getFirstMove(self.getOmen().getStandbyType().getChargeAttackType()), null) :
                processMove(self.getFirstMove(NORMAL_ATTACK), null);
    }

    /**
     * 지정된 적 move 를 실행 <br>
     * 적은 '오의 재발동', '턴 진행 없이 일반공격' 이 없으므로 특수기 및 일반공격은 전부 processStrike() 사용
     *
     * @param move
     * @param otherResult 없으면 null
     * @return
     */
    public MoveLogicResult processMove(Move move, MoveLogicResult otherResult) {
        preProcess(move.getActor());
        return moveLogicRegistry.get(move.getBaseMove().getLogicId()).process(MoveLogicRequest.of(move, otherResult));
    }

    /**
     * 적 사망처리 <br>
     * 적은 사망처리시 반드시 사망결과를 반환, not null
     *
     * @return deadResult (not null)
     */
    public MoveLogicResult processDead(Actor enemy) {
        preProcess(enemy);

        return defaultDead(enemy);
    }

    protected DefaultMoveLogicResult defaultAttack(Move move) {
        return defaultAttack(DefaultMoveRequest.from(move));
    }

    /**
     * 기본적인 공격처리
     * 공격 행동 결정(평타횟수) -> 데미지 계산 -> 오의게이지 갱신
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAttack(DefaultMoveRequest request) {
        Move attackMove = request.getMove();
        int hitCount = getNormalAttackCount(attackMove);
        double damageRate = request.getModifiedDamageRate() != null ? request.getModifiedDamageRate() : attackMove.getBaseMove().getDamageRate();
        DamageLogicResult damageLogicResult = processDamage(attackMove, damageRate, hitCount);

        return DefaultMoveLogicResult.builder()
                .resultMove(attackMove)
                .damageLogicResult(damageLogicResult)
                .build();
    }

    protected DefaultMoveLogicResult defaultChargeAttack(Move move) {
        return defaultChargeAttack(DefaultMoveRequest.builder().move(move).build());
    }

    /**
     * 기본적인 오의 처리 (배율 수정)
     * 오의 및 데미지 배율 결정 -> 데미지 계산 -> 스테이터스 추가 -> 오의게이지 갱신
     *
     * @param request
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultChargeAttack(DefaultMoveRequest request) {
        Move chargeAttack = request.getMove(); // CHARGE_ATTACK_DEFAULT 고정
        BaseMove baseMove = chargeAttack.getBaseMove();
        Enemy self = (Enemy) chargeAttack.getActor();

        // 데미지 계산
        int hitCount = request.getModifiedHitCount() != null ? request.getModifiedHitCount() : baseMove.getHitCount();
        double damageRate = request.getModifiedDamageRate() != null ? request.getModifiedDamageRate() : baseMove.getDamageRate();
        DamageLogicResult damageLogicResult = hitCount > 0 ?
                processDamage(chargeAttack, damageRate, hitCount)
                : null;

        // 스테이터스 적용
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : baseMove.getBaseStatusEffects();
        List<Actor> targets = damageLogicResult == null ? null : damageLogicResult.getEnemyAttackTargets().stream().distinct().toList();
        int toEffectLevel = request.getPlusLevel();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(
                SetEffectRequest.builder().baseStatusEffects(toApplyEffects).enemyAttackTargets(targets).targetLevel(toEffectLevel).build());

        // 전조 삭제 CHECK 다른 로직에서 전조 상태를 확인하므로 마지막에 변경할것
        omenLogic.removeCurrentOmen(self);

        return DefaultMoveLogicResult.builder()
                .resultMove(chargeAttack)
                .damageLogicResult(damageLogicResult)
                .setStatusEffectResult(setStatusEffectResult)
                .build();
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리
     * 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAbility(Move ability) {
        return this.defaultAbility(DefaultMoveRequest.builder().move(ability).build());
    }

    /**
     * 기본적인 어빌리티, 서포트 어빌리티 처리 (배율 변화 및 히트수 변화 있음)
     * 데미지 배율 및 히트수 확인 -> 데미지 계산 -> 스테이터스 추가 -> 쿨타임 적용
     *
     * @return DefaultActorLogicResult
     */
    protected DefaultMoveLogicResult defaultAbility(DefaultMoveRequest request) {
        Move ability = request.getMove();
        BaseMove baseMove = ability.getBaseMove();

        int hitCount = request.getModifiedHitCount() != null ? request.getModifiedHitCount() : baseMove.getHitCount();
        double damageRate = request.getModifiedDamageRate() != null ? request.getModifiedDamageRate() : baseMove.getDamageRate();
        DamageLogicResult damageLogicResult = hitCount > 0
                ? processDamage(ability, damageRate, hitCount)
                : null;

        // 스테이터스 적용
        List<BaseStatusEffect> toApplyEffects = request.getSelectedBaseEffects() != null ? request.getSelectedBaseEffects() : baseMove.getBaseStatusEffects();
        List<Actor> targets = damageLogicResult == null ? null : damageLogicResult.getEnemyAttackTargets().stream().distinct().toList();
        int toEffectLevel = request.getPlusLevel();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(
                SetEffectRequest.builder().baseStatusEffects(toApplyEffects).enemyAttackTargets(targets).targetLevel(toEffectLevel).build());

        return DefaultMoveLogicResult.builder().resultMove(ability).damageLogicResult(damageLogicResult).setStatusEffectResult(setStatusEffectResult).build();
    }

    protected int getNormalAttackCount(Move move) {
        Actor self = move.getActor();
        return Math.random() < self.getStatus().getTripleAttackRate()
                ? 3
                : Math.random() < self.getStatus().getDoubleAttackRate()
                ? 2 : 1;
    }

    protected DamageLogicResult processDamage(Move move, double damageRate, int hitCount) {
        DamageLogicResult damageLogicResult = damageLogic.processEnemyDamage(move, damageRate, hitCount);
        if (move.getType().getParentType() == ATTACK || move.getType().getParentType() == CHARGE_ATTACK) {
            chargeGaugeLogic.afterEnemyAttack(damageLogicResult.getEnemyAttackTargets(), damageLogicResult.getDamages());
        }
        return damageLogicResult;
    }

    public MoveLogicResult defaultDead(Actor enemy) {
        Enemy self = (Enemy) enemy;
        // 상태 갱신
        enemy.updateHp(Integer.MIN_VALUE);
        // 전조 해제
        if (self.getOmen() != null) {
            omenLogic.removeCurrentOmen(self);
        }

        // 컨텍스트, currentOrder 갱신 -> 하지않음, 어차피 강제종료

        // 방 상태 변경
        Room room = self.getMember().getRoom();
        room.changeStatus(RoomStatus.CLEARED);

        // 결과 반환
        return resultMapper.toResult(ResultMapperRequest.from(Move.getTransientMove(self, DEAD_DEFAULT)));
    }

    /**
     * 멤버수에 따른 보정 적용 또는 해제 <br>
     * 적 로직 등 외부에서는 효과에 의존하지말고, battleContext.getMember.getRoom.getEnterUserCount 에 의존바람
     */
    public void applyMemberCount() {
        Member member = battleContext.getMember();
        if (member.getRoom().getRoomStatus() == RoomStatus.TUTORIAL) return;

        int maxUserCount = member.getRoom().getMaxUserCount();
        int enterUserCount = member.getRoom().getEnterUserCount();
        int adjustEffectLevel = maxUserCount - enterUserCount; // 보정효과 레벨 타겟: 3인최대, 2인 입장시 => 효과 레벨 1

        // 자신의 적에게 보정 효과 최대치로 적용
        final long memberCountAdjustEffectMoveId = 999L; // 고정됨
        BaseMove memberCountAdjustEffectMove = baseMoveService.findById(memberCountAdjustEffectMoveId).get();
        setStatusLogic.setStatusEffect(SetEffectRequest.builder()
                .baseStatusEffects(memberCountAdjustEffectMove.getBaseStatusEffects())
                .targetLevel(adjustEffectLevel + 1) // 현재 타겟 레벨 + 1로 적용 (아래에서 한꺼번에 빼서 적용, 이전참전자와 동일레벨 필요, 최대레벨은 최대참가자 - 1 로 세팅됨)
                .build());

        // 인원수에 비례해 모든 멤버의 적의 보정효과 레벨 감소
        int adjustLevel = maxUserCount - enterUserCount; // 3인최대, 2인 입장시 => 효과 레벨 1
        member.getRoom().getMembers().stream()
                .flatMap(roomMember -> roomMember.getActors().stream())
                .filter(Actor::isEnemy)
                .forEach(enemy -> {
                    final long memberCountAdjustEffectBaseId = 999L; // 고정됨
                    StatusUtil.getEffectByBaseId(enemy, memberCountAdjustEffectBaseId).ifPresent(memberCountAdjustEffect -> {
                        if (memberCountAdjustEffect.getLevel() > adjustLevel) {
                            double currentHpRateDouble = (double) enemy.getHp() / enemy.getMaxHp();
                            setStatusLogic.subtractStatusEffectLevel(enemy, memberCountAdjustEffect.getLevel() - adjustLevel, memberCountAdjustEffect); // 레벨 0으로 감소시 삭제됨

                            // startBattle 은 커맨드 실행후 동기화 과정이 없으므로, 여기서 체력까지 수정
                            enemy.updateHp((int) (enemy.getMaxHp() * currentHpRateDouble));
                        }
                    });
                });
    }

    /**
     * 타겟에 logicId 로 조회한 트리거 무브를 저장
     */
    protected void saveTriggeredMove(List<Actor> targets, String moveLogicId) {
        BaseMove baseMove = baseMoveService.findByLogicId(moveLogicId);
        List<Move> triggeredMoves = new ArrayList<>();
        targets.forEach(target -> triggeredMoves.add(Move.fromBaseMove(baseMove).mapActor(target)));
        moveService.saveTriggeredMoves(triggeredMoves);
    }


    // 헬퍼
    protected String normalAttackKey(String gid) {
        return "phit_" + gid;
    }

    protected String abilityKey(String gid, int order) {
        return "ab_" + gid + "_" + order;
    }

    protected String supportAbilityKey(String gid, int order) {
        return "sa_" + gid + "_" + order;
    }

    protected String chargeAttackKey(String gid, String type) {
        return "esp_" + gid + "_" + type; // esp_12345_a
    }

}
