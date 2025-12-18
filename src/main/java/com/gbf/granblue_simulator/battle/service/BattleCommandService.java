package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.BattleLogic;
import com.gbf.granblue_simulator.battle.logic.SyncLogic;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
/**
 * 커맨드 (사용자 입력) 을 처리부로 넘기는 서비스 <br>
 * 임의로 아래와 같이 커맨드를 정의 <br>
 * 커맨드: 공격(턴 진행), 어빌리티 사용, 페이탈 체인, 소환석 <br>
 * 서브커맨드: 가드, 포션 <br>
 */
public class BattleCommandService {

    private final BattleContext battleContext;
    private final BattleLogic battleLogic;
    private final SyncLogic syncLogic;
    private final MoveRepository moveRepository;

    /**
     * 방 생성 또는 입장시 실행
     */
    public void startBattle() {
        Member currentMember = battleContext.getMember();
        // 기존 적이 있을 경우 동기화
        currentMember.getRoom().getMembers().stream()
                .filter(roomMember -> !roomMember.equals(currentMember))
                .findFirst().ifPresent(syncLogic::syncEnemy); // referenceMember.enemy 로 내 enemy 를 즉시 동기화

        battleLogic.startBattle();
    }

    /**
     * 커맨드 "공격" 진입점 (턴 진행)
     *
     * @return
     */
    public List<ActorLogicResult> progressTurn() {
        Member currentMember = battleContext.getMember();
        List<ActorLogicResult> progressTurnResults = new ArrayList<>();
        // 1. 동기화
        progressTurnResults.add(syncLogic.processSync());
        // 2. 아군 전체가 공격행동
        progressTurnResults.addAll(battleLogic.processStrike());
        // 3. 적이 공격행동
        progressTurnResults.addAll(battleLogic.processEnemyStrike());
        // 4. 턴 종료 처리
        if (!battleContext.getFrontCharacters().isEmpty()) {
            progressTurnResults.addAll(battleLogic.processTurnEnd());
            // CHECK 나중에 부활기능이 추가될경우, 쿨다운을 위시한 부분을 전처리 해줘야함
        }
        // 빈 결과 제거
        progressTurnResults = progressTurnResults.stream().filter(ActorLogicResult::notEmpty).toList();

        // 턴 증가
        currentMember.increaseTurn();
        // 커맨드 후처리
        postProcessCommand(progressTurnResults);

        progressTurnResults.forEach(result -> log.info("[progressTurn] Result: {}", result));
        return progressTurnResults;
    }

    /**
     * 커맨드 "어빌리티 사용"
     *
     * @param moveId
     * @return
     */
    public List<ActorLogicResult> ability(Long moveId) {
        Move ability = moveRepository.findById(moveId).orElseThrow(() -> new MoveValidationException("해당 행동이 존재하지 않음 moveId = " + moveId));
        Actor mainCharacter = battleContext.getMainActor();
        // 검증
        if (mainCharacter.getAbilityCooldown(ability.getType()) > 0)
            throw new MoveValidationException("어빌리티 쿨다운 중, actor = " + mainCharacter.getName() + ", ability = " + ability.getName());
        if (mainCharacter.getAbilitySealed(ability.getType()))
            throw new MoveValidationException("어빌리티 봉인 중,  actor = " + mainCharacter.getName() + ", ability = " + ability.getName());

        List<ActorLogicResult> results = new ArrayList<>();

        // 동기화
        ActorLogicResult syncResult = syncLogic.processSync();
        results.add(syncResult);

        // 실행
        List<ActorLogicResult> moveResults = battleLogic.processAbility(ability);
        results.addAll(moveResults);

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.info("[ability] Result: {}", result));
        return results;
    }

    /**
     * 커맨드 '페이탈 체인'
     *
     * @return
     */
    public List<ActorLogicResult> fatalChain() {
        Member member = battleContext.getMember();
        Long fatalChainMoveId = member.getFatalChainMoveId();
        if (fatalChainMoveId == null) throw new MoveValidationException("페이탈 체인 id 없음, member = " + member);
        Move fatalChain = moveRepository.findById(fatalChainMoveId).orElseThrow(() -> new MoveValidationException("페이탈 체인 없음, fatalChainMoveId = " + fatalChainMoveId));

        List<ActorLogicResult> results = new ArrayList<>();

        // 동기화
        ActorLogicResult syncResult = syncLogic.processSync();
        results.add(syncResult);

        // 실행
        List<ActorLogicResult> moveResults = battleLogic.processFatalChain(fatalChain);
        results.addAll(moveResults);

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.info("[fatalChain] Result: {}", result));
        return results;
    }

    /**
     * 커맨드 "소환석 사용" 진입점
     *
     * @param summonId
     * @param doUnionSummon
     * @return
     */
    public List<ActorLogicResult> summon(Long summonId, boolean doUnionSummon) {
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        if (leaderCharacter.isAlreadyDead()) throw new MoveValidationException("주인공이 사망하면 소환석을 사용할수 없음");
        Move summonMove = moveRepository.findById(summonId).orElseThrow(() -> new IllegalArgumentException("없는 소환석"));

        List<ActorLogicResult> results = new ArrayList<>();

        // 동기화
        results.add(syncLogic.processSync());

        // 실행
        results.addAll(battleLogic.processSummon(summonMove, doUnionSummon));

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.info("[summon] Result: {}", result));
        return results;
    }

    /**
     * 서브 커맨드 "가드" 진입점 및 처리
     *
     * @param targetType
     * @return List boolean guardStates
     */
    public List<Boolean> guard(StatusEffectTargetType targetType) {

        List<Boolean> guardStates = battleLogic.processGuard(targetType);
        return guardStates;
    }

    /**
     * 서브 커맨드 "포션사용" 진입점 및 처리
     * 현재 포션사용은 언데드, 강압 효과 등 스테이터스 효과 관계없이 무조건 회복하도록 설정됨.
     *
     * @param targetType SELF, PARTY_MEMBERS
     * @return
     */
    public PotionResult potion(StatusEffectTargetType targetType) {

        PotionResult potionResult = battleLogic.processPotion(targetType);
        return potionResult;
    }

    public ActorLogicResult sync() {
        return syncLogic.processSync();
    }

    /**
     * @param results 커맨드 수행 결과: 공격, 어빌리티사용, 페이탈체인, 소환석 ( 서브커맨드 제외 )
     */
    protected void postProcessCommand(List<ActorLogicResult> results) {
        Member member = battleContext.getMember();

        // 커맨드 종료후 결과 동기화
        syncLogic.syncEnemy(member);

        // 행동 쿨다운 설정
        int resultMoveCooldown = calcMemberMoveCooldown(results);
        member.updateLastMovedTimeNow();
        member.updateMoveCooldown(resultMoveCooldown);

        // 공헌도 계산
        int honor = calcHonor(results);
        results.getFirst().updateHonor(honor); // 첫번째 결과인 SYNC 에 총 공헌도 세팅
        member.addHonor(honor);
    }

    protected int calcMemberMoveCooldown(List<ActorLogicResult> results) {
        int resultMoveCooldown = 1;
        for (ActorLogicResult result : results) {
            if (result.getMainActor().isEnemy()) {
                resultMoveCooldown += 5; // 적은 행동당 5초로 고정
            }
            int moveCoolDown = switch (result.getMove().getType().getParentType()) {
                case ATTACK -> 3;
                case ABILITY -> 2;
                case SUPPORT_ABILITY -> 1;
                case CHARGE_ATTACK -> 4;
                case SUMMON -> 4;
                case FATAL_CHAIN -> 4;
                default -> {
                    log.warn("[calcMemberMoveCooldown] default case, moveType = {}, result = {}", result.getMove().getType(), result);
                    yield 1;
                }
            };
//            log.info("[calcMemberMoveCooldown] moveType = {}, moveCoolDown = {}", result.getMoveType(), moveCoolDown);
            resultMoveCooldown += moveCoolDown;
        }
        log.info("[calcMemberMoveCooldown] resultMoveCooldown = {}", resultMoveCooldown);
        return resultMoveCooldown;
    }

    /**
     * 이름, 비율 (%)
     */
    private final Map<String, Integer> additionalHonorMovenameMap = Map.of(
            "팔랑크스", 1,
            "미제라블 미스트", 1
    );

    protected int calcHonor(List<ActorLogicResult> results) {
        int totalHonor = 0;
        Actor enemy = battleContext.getEnemy();

        int basicMaxHonor = enemy.getMaxHp() / 100; // 기본 총 공헌도는 적 체력의 1%로 함 (적 체력 1억 시, 기본 총 공헌도 100만)
        // 추가 공헌도는 조건을 통해 얻으며, 기본 총 공헌도를 기준으로 획득 (따라서, 최종 공헌도는 기본 총 공헌도를 넘어감)
        // 원본 게임이 적 최대체력 기준 비율로 계산하므로 그와 비슷하게 계산. 단위만 줄임

        for (int index = 0; index < results.size(); index++) {
            ActorLogicResult result = results.get(index);
            Move move = result.getMove();
            if (move.getType() == MoveType.SYNC) continue; // SYNC 는 무시

            int resultHonor = 0;

            //1. 특정 주인공의 어빌리티 사용시 기본 총 공헌도의 1% 분의 공헌도 획득
            if (move.getName() != null) {
                Integer value = additionalHonorMovenameMap.get(move.getName());
                if (value != null) resultHonor += basicMaxHonor / 100;
                log.info("[calcHonor] ABILITY moveName = {}, honor = {}", move.getName(), basicMaxHonor / 100);
            }

            //2. 적의 전조를 해제시 기본 총 공헌도의 1% 분의 공헌도를 획득
            if (move.getType().getParentType() == MoveType.BREAK) {
                resultHonor += basicMaxHonor / 100;
                log.info("[calcHonor] BREAK moveType = {}, honor = {}", move.getType(), basicMaxHonor / 100);
            }

            // 3. 줄어든 적의 체력의 1% 만큼 공헌도 획득 ( = 기본 총 공헌도 분배)
            if (index > 0) { // 첫번째 제외
                ActorLogicResult beforeResult = results.get(index - 1);
                Integer beforeEnemyHp = beforeResult.getSnapshots().get(enemy.getId()).getHp();
                Integer currentEnemyHp = result.getSnapshots().get(enemy.getId()).getHp();
                 currentEnemyHp = currentEnemyHp > 0 ? currentEnemyHp : 0; // 오버된 데미지는 적용 x
                int hpDiff = beforeEnemyHp - currentEnemyHp;
                int honor = hpDiff > 0 ? hpDiff / 100 : 0;
                resultHonor += honor;
                log.info("[calcHonor] DAMAGE beforeEnemyHp = {}, currentEnemyHp = {}, honor = {}", beforeEnemyHp, currentEnemyHp, honor);
            }

            result.updateHonor(resultHonor);
            totalHonor += resultHonor;
            log.info("[calcHonor] moveName = {}, resultHonor = {}, totalHonor = {}", move.getName(), resultHonor, totalHonor);
        }

        return totalHonor;
    }


}
