package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.PotionType;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.BattleLogic;
import com.gbf.granblue_simulator.battle.logic.SyncLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.repository.ActorRepository;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 커맨드 (사용자 입력) 을 처리부로 넘기는 서비스 <br>
 * 임의로 아래와 같이 커맨드를 정의 <br>
 * 커맨드: 공격(턴 진행), 어빌리티 사용, 페이탈 체인, 소환석 <br>
 * 서브커맨드: 가드, 포션 <br>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BattleCommandService {

    private final BattleContext battleContext;
    private final BattleLogic battleLogic;
    private final SyncLogic syncLogic;

    private final RoomService roomService;
    private final StatusService statusService;
    private final MoveService moveService;

    private final ActorRepository actorRepository; // 락 용
    private final MemberService memberService;

    private final EntityManager entityManager;

    /**
     * 방 생성 또는 입장시 실행
     */
    @Retryable(
            retryFor = {JpaSystemException.class, org.hibernate.TransactionException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 0)
    )
    @Transactional(timeout = 2)
    public List<MoveLogicResult> startBattle(BattleCommandRequest request) {
        initializeContext(request);
        Member currentMember = battleContext.getMember();

        RoomStatus roomStatus = currentMember.getRoom().getRoomStatus();
        if (!roomStatus.isHidden() && (roomStatus != RoomStatus.ACTIVE && roomStatus != RoomStatus.CLEARED)) {
            throw new MoveProcessingException("더이상 전투중인 방이 아닙니다. 결과화면으로 이동합니다.", "BATTLE_FINISHED"); // 보험
        }

        // 기존 적이 있을 경우 동기화
        currentMember.getRoom().getMembers().stream()
                .filter(roomMember -> !roomMember.getActors().isEmpty() && !roomMember.equals(currentMember))
                .findFirst().ifPresent(syncLogic::syncEnemy); // referenceMember.enemy 로 내 enemy 를 즉시 동기화

        List<MoveLogicResult> startBattleResults = battleLogic.processBattleStart();
        currentMember.increaseTurn();

        if (currentMember.getRoom().getRoomStatus() == RoomStatus.TUTORIAL) {
            this.startTutorial();
        }

        return startBattleResults;
    }

    /**
     * 튜토리얼 시작시, 일부 캐릭터와 상황을 튜토리얼에 맞게 조정
     */
    private void startTutorial() {
        battleContext.getMember().updateFatalChainGauge(85); // FC
        battleContext.getFrontCharacters().forEach(character -> {
            // 오의 게이지
            character.updateChargeGauge(50);

            // 쿨타임
            if (character.getBaseActor().getId().equals(60100L)) {
                // 검호
                character.updateAbilityCooldowns(5, MoveType.FIRST_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY);
            } else if (character.getBaseActor().getId().equals(70900L)) {
                // 하제리라
                character.updateAbilityCooldowns(5, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY);
            } else if (character.getBaseActor().getId().equals(71300L)) {
                // 와무듀스
                character.updateAbilityCooldowns(5, MoveType.FIRST_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY);
                character.updateAbilityCooldowns(2, MoveType.SECOND_ABILITY);
            } else if (character.getBaseActor().getId().equals(71000L)) {
                // 실비아
                character.updateAbilityCooldowns(5, MoveType.FIRST_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY, MoveType.FOURTH_ABILITY);
            }
        });
    }

    /**
     * 튜토리얼 진행시, 새로고침을 통해 진행도가 뒤로 돌아가는경우 필요한 조정 진행
     */
    public void adjustTutorial(BattleCommandRequest request) {
        initializeContext(request);

        Actor leaderCharacter = battleContext.getLeaderCharacter();
        if (leaderCharacter == null) throw new MoveProcessingException("튜토리얼 초기화가 필요합니다.");

        Member member = battleContext.getMember();
        int currentTurn = battleContext.getCurrentTurn();

        Actor enemy = battleContext.getEnemy();
        if (enemy.getHpRateInt() < 50 && currentTurn < 6) {
            enemy.updateHp((int) (enemy.getMaxHp() * 0.5));
        }

        switch (currentTurn) {
            case 1:
                leaderCharacter.updateChargeGauge(70);
                leaderCharacter.getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
                break;
            case 2:
                break;
            case 3:
                Actor wamdus = battleContext.getFrontCharacters().stream().filter(character -> character.getBaseActor().getId().equals(71300L)).findFirst().orElseThrow(() -> new MoveProcessingException("튜토리얼 초기화가 필요합니다."));
                wamdus.getFirstMove(MoveType.SECOND_ABILITY).updateCooldown(0);
                break;
            case 4:
                leaderCharacter.getFirstMove(MoveType.FIRST_SUMMON).updateCooldown(0);
                member.updateUsedSummon(false);
                member.getRoom().updateUnionSummonId(leaderCharacter.getFirstMove(MoveType.SECOND_SUMMON).getId());
                break;
            case 5:
                battleContext.getFrontCharacters().forEach(character -> character.changeGuard(false));
                break;
            case 6:
                member.updateFatalChainGauge(100);
                member.updateAllPotionCount(2);
                break;
            default:
                // nothing
        }
    }

    /**
     * 커맨드 "공격" 진입점 (턴 진행)
     *
     * @return
     */
    @Retryable(
            retryFor = {JpaSystemException.class, org.hibernate.TransactionException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 0)
    )
    @Transactional(timeout = 2)
    public List<MoveLogicResult> progressTurn(BattleCommandRequest request) {
        initializeContext(request);

        if (battleContext.getFrontCharacters().isEmpty()) throw new MoveValidationException("캐릭터가 전원 사망하였습니다.", true);
        if (battleContext.getEnemy().isAlreadyDead()) throw new MoveValidationException("적이 이미 사망하였습니다.", true);
        preProcessCommand();

        List<MoveLogicResult> progressTurnResults = new ArrayList<>();
        // 동기화
        progressTurnResults.addAll(syncLogic.processSync());
        // 아군 전체가 공격행동
        progressTurnResults.addAll(battleLogic.processStrike());
        // 적이 공격행동
        progressTurnResults.addAll(battleLogic.processEnemyStrike());
        // 턴 종료 처리
        progressTurnResults.addAll(battleLogic.processTurnEnd());
        // 턴 증가
        battleContext.getMember().increaseTurn();
        // 커맨드 후처리
        postProcessCommand(progressTurnResults);

//        progressTurnResults.forEach(result -> log.info("[progressTurn] Result: {}", result));
        return progressTurnResults;
    }

    /**
     * 커맨드 "어빌리티 사용" 진입점
     *
     */
    @Retryable(
            retryFor = {JpaSystemException.class, org.hibernate.TransactionException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 0)
    )
    @Transactional(timeout = 2)
    public List<MoveLogicResult> ability(BattleCommandRequest request) {
        initializeContext(request);

        if (battleContext.getFrontCharacters().isEmpty()) throw new MoveValidationException("캐릭터가 전원 사망하였습니다.", true);
        if (battleContext.getEnemy().isAlreadyDead()) throw new MoveValidationException("적이 이미 사망하였습니다.", true);
        preProcessCommand();

        Long moveId = request.getCommandMoveId();

        Move ability = moveService.findById(moveId).orElseThrow(() -> new MoveValidationException("해당 행동이 존재하지 않음 moveId = " + moveId));
        Actor mainCharacter = battleContext.getMainActor();
        // 검증
        if (ability.getCooldown() > 0)
            throw new MoveValidationException("쿨타임이 진행중입니다.", true);
        if (mainCharacter.getAbilitySealed(ability.getType()))
            throw new MoveValidationException("어빌리티가 봉인되어 사용할 수 없습니다.", true);

        List<MoveLogicResult> results = new ArrayList<>();

        // 동기화
        List<MoveLogicResult> syncResults = syncLogic.processSync();
        results.addAll(syncResults);

        // 실행
        List<MoveLogicResult> moveResults = battleLogic.processAbility(ability);
        results.addAll(moveResults);

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.debug("[ability] Result: {}", result));
        return results;
    }

    /**
     * 커맨드 '페이탈 체인' 진입점
     *
     * @return
     */
    @Retryable(
            retryFor = {JpaSystemException.class, org.hibernate.TransactionException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 0)
    )
    @Transactional(timeout = 2)
    public List<MoveLogicResult> fatalChain(BattleCommandRequest request) {
        initializeContext(request);

        if (battleContext.getFrontCharacters().isEmpty()) throw new MoveValidationException("캐릭터가 전원 사망하였습니다.", true);
        if (battleContext.getEnemy().isAlreadyDead()) throw new MoveValidationException("적이 이미 사망하였습니다.", true);
        preProcessCommand();

        Member member = battleContext.getMember();
        if (member.getFatalChainGauge() < 100) throw new MoveValidationException("페이탈 체인 게이지가 부족하여 사용할 수 없습니다.");

        List<MoveLogicResult> results = new ArrayList<>();

        // 동기화
        List<MoveLogicResult> syncResults = syncLogic.processSync();
        results.addAll(syncResults);

        // 실행
        List<MoveLogicResult> moveResults = battleLogic.processFatalChain();
        results.addAll(moveResults);

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.debug("[fatalChain] Result: {}", result));
        return results;
    }

    /**
     * 커맨드 "소환석 사용" 진입점
     *
     */
    @Retryable(
            retryFor = {JpaSystemException.class, org.hibernate.TransactionException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 0)
    )
    @Transactional(timeout = 2)
    public List<MoveLogicResult> summon(BattleCommandRequest request) {
        initializeContext(request);

        if (battleContext.getFrontCharacters().isEmpty()) throw new MoveValidationException("캐릭터가 전원 사망하였습니다.", true);
        if (battleContext.getEnemy().isAlreadyDead()) throw new MoveValidationException("적이 이미 사망하였습니다.", true);
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        if (leaderCharacter.isAlreadyDead()) throw new MoveValidationException("주인공이 사망하면 소환석을 사용할수 없습니다.", true);
        if (leaderCharacter.getMember().usedSummon()) throw new MoveValidationException("이미 이번 턴에 소환석을 사용했습니다.", true);

        preProcessCommand();

        Move summonMove = moveService.findById(request.getSummonId()).orElseThrow(() -> new IllegalArgumentException("없는 소환석"));
        List<MoveLogicResult> results = new ArrayList<>();

        // 동기화
        results.addAll(syncLogic.processSync());

        // 실행
        results.addAll(battleLogic.processSummon(summonMove, request.isUnionSummon()));

        // 후처리
        postProcessCommand(results);

        results.forEach(result -> log.debug("[summon] Result: {}", result));
        return results;
    }

    /**
     * 서브 커맨드 "가드" 진입점<br>
     * 현재 상태효과 관련 필드를 건드리지 않아 1차캐시 초기화 없이 컨텍스트 초기화 후 사용.
     *
     * @param targetType 가드 타겟타입
     * @return List boolean guardStates
     */
    @Transactional(timeout = 2)
    public List<Boolean> guard(Member member, Long mainActorId, StatusEffectTargetType targetType) {
        battleContext.init(member, mainActorId);

        if (member.getRoom().isFinished()) throw new MoveProcessingException("이미 종료된 전투입니다.", "BATTLE_FINISHED");

        List<Boolean> guardStates = battleLogic.processGuard(targetType);
        return guardStates;
    }

    /**
     * 서브 커맨드 "포션사용" 진입점
     *
     */
    @Transactional(timeout = 2)
    public PotionResult potion(Long memberId, PotionType potionType, Long targetActorId) {
        initializeContext(BattleCommandRequest.builder().memberId(memberId).build());

        Member member = battleContext.getMember();
        if (member.getRoom().isFinished()) throw new MoveProcessingException("이미 종료된 전투입니다.", "BATTLE_FINISHED");
        if (potionType != PotionType.ALL_POTION) {
            if (targetActorId == null || targetActorId <= 0) throw new MoveValidationException("포션 대상이 잘못되었습니다.", true);
            battleContext.getAllCharacters().stream().filter(character -> character.getId().equals(targetActorId)).findFirst().orElseThrow(() -> new MoveValidationException("포션 대상이 없습니다.", true));
        }

        int potionCount = potionType == PotionType.POTION ? member.getPotionCount()
                : potionType == PotionType.ALL_POTION ? member.getAllPotionCount()
                  : potionType == PotionType.ELIXIR ? member.getElixirCount()
                    : -1;
        if (potionCount <= 0) throw new MoveValidationException("포션 갯수가 부족합니다.", true);

        PotionResult potionResult = battleLogic.processPotion(potionType, targetActorId);
        return potionResult;
    }

    /**
     * 동기화 요청 진입점
     */
    @Transactional(timeout = 2)
    public List<MoveLogicResult> sync(BattleCommandRequest request) {

        initializeContext(request);

        RoomStatus roomStatus = battleContext.getMember().getRoom().getRoomStatus();
        if (!roomStatus.isHidden()
                && (roomStatus != RoomStatus.ACTIVE && roomStatus != RoomStatus.CLEARED)) {
            throw new MoveProcessingException("더이상 전투중인 방이 아닙니다. 결과화면으로 이동합니다.", "BATTLE_FINISHED");
        }

        return syncLogic.processSync();
    }

    /**
     * 오의 사용 ON / OFF 진입점
     *
     * @param isChargeAttackOn 요청 상태 (true: ON, false: OFF)
     */
    public List<Boolean> toggleChargeAttack(Member member, boolean isChargeAttackOn) {
        battleContext.init(member, null);
        statusService.initStatusForRead(battleContext.getAllActors());
        member.updateChargeAttackOn(isChargeAttackOn);
        return member.getActors().stream().sorted(Comparator.comparing(Actor::getCurrentOrder)).map(Actor::canCharacterChargeAttack).toList();
    }

    /**
     * 각 커맨드 진입시 전처리
     */
    protected void preProcessCommand() {
        Member member = battleContext.getMember();

        // 방 상태 검증
        boolean isRoomTimeout = member.getRoom().getCreatedAt().plusMinutes(45).isBefore(LocalDateTime.now());
        if (member.getRoom().isFinished() || isRoomTimeout) {
            if (member.getRoom().getEndedAt() == null) {
                roomService.timeoutRoom(member.getRoom().getId()); // 방어용
            }
            throw new MoveProcessingException("이미 종료된 전투입니다.", "BATTLE_FINISHED");
        }

        // 행동 쿨다운 검증
        LocalDateTime lastMoveTime = member.getLastMoveTime();
        double moveCooldown = member.getMoveCooldown();
        if (lastMoveTime == null) return;

        long cooldownMs = (long) (moveCooldown * 1000);
        long elapsed = Duration.between(lastMoveTime, LocalDateTime.now()).toMillis();
        long remaining = cooldownMs - elapsed;
        remaining = remaining <= 2000 ? 0 : remaining / 2; // 2초 내외는 허용, 초과시 보수적으로 잡힌 값 보정
        if (remaining > 0) {
            throw new MoveValidationException("이전 처리 대기중입니다.", true);
        }

    }

    public void initializeContext(BattleCommandRequest request) {
        Member memberBeforeLock = request.getMember();
        if (memberBeforeLock == null) {
            if (request.getMemberId() == null)
                throw new IllegalArgumentException("락용 멤버 조회 에러, 식별자 없음 request = " + request);
            memberBeforeLock = memberService.findById(request.getMemberId()).orElseThrow(() -> new IllegalArgumentException("락용 멤버조회 에러 memberId = " + request.getMemberId()));
        }

        // 해당 방의 모든 Actor 에 대해 락 획득
        List<Long> actorIds = actorRepository.findActorIdsByRoomId(memberBeforeLock.getRoom().getId());
        actorRepository.lockActors(actorIds);

        // entityManager.flush() // CHECK flush 여부는 추이를 지켜보며 결정
        entityManager.clear(); // 정합성을 위해 1차캐시 전부 초기화

        // 정보 재조회 후 컨텍스트 초기화
        Member member = memberService.findFreshWithActorsById(memberBeforeLock.getId()).orElseThrow(() -> new IllegalArgumentException("컨텍스트용 멤버조회 에러 memberId = " + request.getMemberId()));
        battleContext.init(member, request.getMainActorId(), request.getCommandMoveId());

        battleContext.getAllActors().

                forEach(statusService::syncStatus);
    }

    /**
     * 각 커맨드 처리 후 후처리
     *
     * @param results 커맨드 수행 결과: 공격, 어빌리티사용, 페이탈체인, 소환석 ( 서브커맨드 제외 )
     */
    protected void postProcessCommand(List<MoveLogicResult> results) {
        Member member = battleContext.getMember();

        // 커맨드 종료후 결과 동기화
        syncLogic.syncEnemy(member);

        // 행동 쿨다운 설정
        double resultMoveCooldown = calcMemberMoveCooldown(results);
        member.updateLastMovedTimeNow();
        member.updateMoveCooldown(resultMoveCooldown);

        // 공헌도 계산
        int honor = calcHonor(results);
        results.getFirst().updateHonor(honor); // 첫번째 결과인 SYNC 에 총 공헌도 세팅
        member.addHonor(honor);
    }

    protected double calcMemberMoveCooldown(List<MoveLogicResult> results) {
        double resultMoveCooldown = 0;
        for (MoveLogicResult result : results) {
            switch (result.getMove().getType().getParentType()) {
                case ATTACK:
                    resultMoveCooldown += result.getNormalAttackCount() * 0.3;
                    break;
                case ABILITY:
                    resultMoveCooldown += 1.5;
                    break;
                case SUPPORT_ABILITY:
                    double modifier = result.getMove().getBaseMove().getMotionType() == MotionType.NONE ? 1.0 : 2.0;
                    resultMoveCooldown += modifier;
                    break;
                case CHARGE_ATTACK:
                    resultMoveCooldown += 2.0;
                    break;
                case SUMMON:
                    resultMoveCooldown += 5.0;
                    break;
                case FATAL_CHAIN:
                    resultMoveCooldown += 2.0;
                    break;
                case STANDBY:
                    resultMoveCooldown += 1.0;
                    break;
                default:
//                    log.warn("[calcMemberMoveCooldown] default case, moveType = {}, result = {}", result.getMove().getType(), result);
            }
//            log.info("[calcMemberMoveCooldown] moveType = {}, moveCoolDown = {}", result.getMoveType(), moveCoolDown);
        }
        log.info("[calcMemberMoveCooldown] resultMoveCooldown = {}", resultMoveCooldown);

        // TEST ======================
        // resultMoveCooldown = 0;
        return resultMoveCooldown;
    }

    /**
     * 이름, 비율 (%)
     */
    private final Map<String, Integer> additionalHonorMovenameMap = Map.of(
            "팔랑크스", 1,
            "미제라블 미스트", 1,
            "젯 투 젯", 1,
            "사기향상", 1
    );


    /**
     * 커맨드 실행에 대한 공헌도 계산 후 반환
     *
     * @return 공헌도
     */
    protected int calcHonor(List<MoveLogicResult> results) {
        int totalHonor = 0;
        Actor enemy = battleContext.getEnemy();

        int basicMaxHonor = enemy.getMaxHp() / 100; // 기본 총 공헌도는 적 체력의 1%로 함 (적 체력 1억 시, 기본 총 공헌도 100만)
        // 추가 공헌도는 조건을 통해 얻으며, 기본 총 공헌도를 기준으로 획득 (따라서, 최종 공헌도는 기본 총 공헌도를 넘어감)
        // 원본 게임이 적 최대체력 기준 비율로 계산하므로 그와 비슷하게 계산. 단위만 줄임

        for (int index = 0; index < results.size(); index++) {
            MoveLogicResult result = results.get(index);
            Move move = result.getMove();
            if (move.getType() == MoveType.SYNC) continue; // SYNC 는 무시
            BaseMove baseMove = move.getBaseMove();

            int resultHonor = 0;

            //1. 특정 주인공의 어빌리티 사용시 기본 총 공헌도의 1% 분의 공헌도 획득
            if (baseMove.getName() != null) {
                Integer value = additionalHonorMovenameMap.get(baseMove.getName());
                if (value != null) resultHonor += basicMaxHonor / 100;
//                log.info("[calcHonor] ABILITY moveName = {}, honor = {}", baseMove.getName(), basicMaxHonor / 100);
            }

            //2. 적의 전조를 해제시 기본 총 공헌도의 1% 분의 공헌도를 획득
            if (result.getOmenResult() != null && result.getOmenResult().isOmenBreak()) {
                resultHonor += basicMaxHonor / 100;
//                log.info("[calcHonor] BREAK moveType = {}, honor = {}", baseMove.getType(), basicMaxHonor / 100);
            }

            // 3. 줄어든 적의 체력의 1% 만큼 공헌도 획득 ( = 기본 총 공헌도 분배)
            if (index > 0) { // 첫번째 제외
                MoveLogicResult beforeResult = results.get(index - 1);
                Integer beforeEnemyHp = beforeResult.getSnapshots().get(enemy.getId()).getHp();
                Integer currentEnemyHp = result.getSnapshots().get(enemy.getId()).getHp();
                currentEnemyHp = currentEnemyHp > 0 ? currentEnemyHp : 0; // 오버된 데미지는 적용 x
                int hpDiff = beforeEnemyHp - currentEnemyHp;
                int honor = hpDiff > 0 ? hpDiff / 100 : 0;
                resultHonor += honor;
//                log.info("[calcHonor] DAMAGE beforeEnemyHp = {}, currentEnemyHp = {}, honor = {}", beforeEnemyHp, currentEnemyHp, honor);
            }

            result.updateHonor(resultHonor);
            totalHonor += resultHonor;
            log.debug("[calcHonor] moveName = {}, resultHonor = {}, totalHonor = {}", baseMove.getName(), resultHonor, totalHonor);
        }

        return totalHonor;
    }

// TEST ========================================================================================================================

    public void resetCooldowns(BattleCommandRequest request) {

        initializeContext(request);

        battleContext.getFrontCharacters().forEach(partyMember -> partyMember.updateAbilityCooldowns(0, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY));
        battleContext.getFrontCharacters().forEach(Actor::resetAbilityUseCount);

        Actor leaderCharacter = battleContext.getLeaderCharacter();
        leaderCharacter.getSummons().forEach(summonMove -> summonMove.updateCooldown(0));
    }

}
