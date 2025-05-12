package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import com.gbf.granblue_simulator.repository.actor.BattleCharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
// TODO 힐처리
public class SetStatusLogic {

    private final BattleStatusRepository battleStatusRepository;

    private final StatusUtil statusUtil;

    private final ChargeGaugeLogic chargeGaugeLogic;
    private final CalcStatusLogic calcStatusLogic;

    /**
     * 첫 init
     *
     * @param battleActor
     */
    public void initStatus(BattleActor battleActor) {
        calcStatusLogic.initStatus(battleActor);
    }

    /**
     * 외부에서 스테이터스값을 동기화 할때 사용
     * TODO 나중에 정리하고 없애기
     *
     * @param battleActor
     */
    public void syncStatus(BattleActor battleActor) {
        calcStatusLogic.syncStatus(battleActor);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set 하는 메인 메서드
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param statuses     Move.statuses 또는 임의의 Status
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, null);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set 하며, StatusTargetType modifyingTargetType 을 추가로 받아 Status.targetType 대신 사용
     * 주로 SELF 스테이터스의 효과를 PARTY_MEMBERS 로 전체화 할때 사용한다.
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param statuses     Move.statuses 또는 임의의 Status
     * @param modifyingTargetType 기존 Status.targetType 대신 사용할 타겟 타입
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses, StatusTargetType modifyingTargetType) {
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, modifyingTargetType);
    }

    /**
     * Move 를 받아 해당 Move 의 Status 리스트를 타겟에 맞춰 BattleStatus 로 set
     * Move 에 '랜덤효과 N개 부여' 인 경우에 사용
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public SetStatusResult setRandomStatusFromMove(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        List<Status> statuses = move.getStatuses();
        if (move.getRandomStatusCount() != null && move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(statuses);
            statuses = statuses.subList(0, move.getRandomStatusCount());
        }
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, null);
    }

    /**
     * 스테이터스를 적용하는 메인로직
     * 여기서 타겟을 정한 뒤 타겟별로 applyStatusToActor 를 실행해 발생한 Status 를 BattleStatus 로 변환하여 리스트에 담는다.
     * 디스펠 처리와 스테이터스 및 스텟 재계산 처리도 함.
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param statuses
     * @param modifiedTargetType
     * @return SetStatusResult 스테이터스 처리 결과 DTO
     */
    protected SetStatusResult applyStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses, StatusTargetType modifiedTargetType) {
        List<BattleStatus> enemyAddedBattleStatus = new ArrayList<>();
        List<List<BattleStatus>> partyMemberAddedBattleStatus = IntStream.range(0, 4).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList()); // SELF 및 NEXT_CHARACTER 대응예정

        // 스테이터스를 각각의 타깃별로 적용 후 결과에 순서에 맞게 넣음
        statuses.forEach(status -> {
            // 타겟 타입 변경여부 처리
            StatusTargetType statusTargetType = modifiedTargetType != null ? modifiedTargetType : status.getTarget();
            // 타겟 타입별 타겟 가져와서
            this.getTargets(mainActor, enemy, partyMembers, statusTargetType).forEach(battleActor -> {
                // 타겟 BattleActor 별로 스테이터스 적용
                BattleStatus addedBattleStatus = this.applyStatusToActor(mainActor, battleActor, status);
                // 적용(추가)된 BattleStatus 를 반환 리스트에 추가
                if (battleActor.isEnemy()) enemyAddedBattleStatus.add(addedBattleStatus);
                else partyMemberAddedBattleStatus.get(battleActor.getCurrentOrder() - 1).add(addedBattleStatus);
            });
        });

        // Actor 별 적용된 Status 에 대한 후처리 ========================================
        // 아군에 대한 적의 디스펠 처리
        List<List<BattleStatus>> partyMemberRemovedBattleStatus = processEnemyDispel(partyMembers);
        boolean isPartyMemberDispelled = !partyMemberRemovedBattleStatus.isEmpty();

        // 적에 대한 아군 메인캐릭터의 디스펠 처리 (원본도 디스펠 처리가 늦음)
        List<BattleStatus> enemyRemovedBattleStatus = processMainActorDispel(enemy);
        boolean enemyDispelled = !enemyRemovedBattleStatus.isEmpty();

        // 스텟 재계산
        partyMembers.forEach(calcStatusLogic::syncStatus);
        calcStatusLogic.syncStatus(enemy);

        // TODO 나중에 힐 처리까지 하고 나서 분리해야 할듯
        // ============================================================================

        // 로깅
        partyMemberAddedBattleStatus.forEach(addedStatuses -> log.info("addedStatuses = {}", addedStatuses));

        return SetStatusResult.builder()
                .enemyAddedStatuses(enemyAddedBattleStatus)
                .enemyRemovedStatuses(enemyRemovedBattleStatus)
                .partyMemberAddedStatuses(partyMemberAddedBattleStatus)
                .partyMemberRemovedStatuses(partyMemberRemovedBattleStatus)
                .partyMemberDispelled(isPartyMemberDispelled)
                .enemyDispelled(enemyDispelled)
                .build();
    }

    private List<BattleActor> getTargets(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, StatusTargetType targetType) {
        List<BattleActor> resultTargets = new ArrayList<>();
        switch (targetType) {
            case SELF -> resultTargets.add(mainActor);
            case ENEMY -> resultTargets.add(enemy);
            case PARTY_MEMBERS -> resultTargets.addAll(partyMembers);
            case ALL_PLAYERS -> {}
            case NEXT_CHARACTER -> {}
            default -> throw new IllegalArgumentException("getTargets() Invalid target type: " + targetType);
        }
        return resultTargets;
    }

    /**
     * 발생한 Status 를 BattleStatus 로 변환하는 메인 로직
     * 처리 결과에 따라 기존 BattleStatus, Miss BattleStatus, No Effect BattleStatus, 새로운 BattleStatus 를 반환하며
     * 새로 발생한 BattleStatus 의 경우 DB 에 저장한다.
     *
     * @param mainActor 스테이터스를 발생시킨 대상
     * @param targetActor 스테이터스를 받은 대상
     * @param status 발생한 스테이터스
     * @return BattleStatus
     */
    protected BattleStatus applyStatusToActor(BattleActor mainActor, BattleActor targetActor, Status status) {
        // 디버프 명중처리
        if (status.getType() == StatusType.DEBUFF || status.getType() == StatusType.DEBUFF_FOR_ALL) {
            BattleStatus missBattleStatus = this.processDebuffResistance(mainActor, targetActor, status);
            if (missBattleStatus != null) return missBattleStatus; // 명중에 실패하면 Miss BattleStatus 바로 반환
        }

        // 중복 id 스테이터스 처리
        BattleStatus updatedBattleStatus = statusUtil.getSameIdBattleStatus(targetActor, status)
                .map(battleStatus -> this.processDuplicatedIdStatus(status, battleStatus)).orElseGet(() -> null);
        if (updatedBattleStatus != null) return updatedBattleStatus; // 갱신된 기존 효과 반환

        // 이펙트 타입 겹침 처리
        log.info("setStatusEffectType status = {} size = {}", status, status.getStatusEffects().size());
        if (status.getStatusEffects().size() == 1) {
            BattleStatus coveringBattleStatus = statusUtil.getSameEffectTypeStatus(targetActor, status)
                    .map(battleStatus -> this.processDuplicatedEffectStatus(status, battleStatus)).orElseGet(() -> null);
            if (coveringBattleStatus != null) return coveringBattleStatus; // 레벨제의 경우 갱신된 기존 효과, 기존효과가 상위인 경우 No Effect BattleStatus 바로 반환
        }

        // 오의 게이지 업(반드시 하나의 StatusEffect 로 구성됨) 별도처리
        StatusEffect firstStatusEffect = status.getStatusEffects().entrySet().iterator().next().getValue();
        if (firstStatusEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP) {
            chargeGaugeLogic.processChargeGaugeFromSetStatus(targetActor, firstStatusEffect);
        }

        // 새로 추가되는 스테이터스
        BattleStatus addedBattleStatus = BattleStatus.builder()
                .battleActor(targetActor)
                .duration(status.getDuration())
                .status(status)
                .level(status.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                .iconSrc(status.getIconSrcs().isEmpty() ? "" : status.getIconSrcs().getFirst())
                .build()
                .setBattleActor(targetActor);
        battleStatusRepository.save(addedBattleStatus);
        return addedBattleStatus;
    }

    /**
     * 디버프 명중 처리
     *
     * @param mainActor   디버프 명중률을 사용할 메인액터
     * @param targetActor 디버프 내성을 사용할 타겟
     * @return 디버프 명중에 실패할 경우 MISS BattleStatus 반환, 명중하면 null (다음단계진행)
     */
    private BattleStatus processDebuffResistance(BattleActor mainActor, BattleActor targetActor, Status status) {
        // 약체 명중 처리
        Double deBuffSuccessRate = mainActor.getDeBuffSuccessRate(); // 기본 약체명중 + 약체명중UP - 약체명중 DOWN (하한 0 상한 X)
        Double deBuffResistRate = targetActor.getDeBuffResistRate(); // 타겟 기본 약체내성 + 약체내성UP - 약체내성 DOWN (하한 -0.99 상한 x)
        double debuffAccuracy = deBuffSuccessRate * (1 - deBuffResistRate);
        if (deBuffSuccessRate < 100 && debuffAccuracy < Math.random()) // 디버프 성공률 100.0 이상은 '필중'상태. 등록은 999이상으로 할것임
            // 디버프 명중에 실패할 경우 MISS 반환. 이 MISS 는 DB 에 저장되지 않음. (표시전용)
            return BattleStatus.builder()
                    .duration(0)
                    .status(Status.builder().type(status.getType()).name("MISS").effectText("MISS").build())
                    .level(0)
                    .iconSrc("")
                    .build()
                    .setBattleActor(targetActor);
        return null;
    }

    /**
     * 발생한 Status 가 기존 BattleStatus 와 id 가 같은경우 기존 BattleStatus 를 갱신 한 후 반환
     *
     * @param status       발생한 Status
     * @param battleStatus 발생한 Status 와 Status.id 가 같은 기존 BattleStatus
     * @return 갱신된 기존 BattleStatus
     */
    private BattleStatus processDuplicatedIdStatus(Status status, BattleStatus battleStatus) {
        if (battleStatus == null) return null;
        if (status.getMaxLevel() > 0) {
            // 레벨제 스테이터스
            if (battleStatus.getLevel() < status.getMaxLevel()) {
                // 최고레벨이 아니면 레벨 증가
                battleStatus.increaseLevel();
            } else {
                // 최고레벨과 같거나 크면 효과시간 초기화
                battleStatus.resetDuration();
            }
        } else {
            // 레벨제 아니면 효과시간 초기화 -> Status.id 같기때문에 효과량이 동일
            battleStatus.resetDuration();
        }
        return battleStatus;
    }

    /**
     * 발생한 Status 가 기존 BattleStatus 와 타입(Status.StatusEffect.type)이 같은경우 발생한 Status 와 기존 BattleStatus 중
     * 효과량 또는 지속시간이 더 우위인 효과를 사용.
     *
     * @param status       발생한 Status
     * @param battleStatus 발생한 Status 와 첫번째 Status.StatusEffect.type 이 같은 BattleStatus (이펙트 1개로 구성)
     * @return 레벨제일 경우 레벨이 갱신된 기존 BattleStatus,
     * 기존 BattleStatus 가 효과량 우위로 발생한 Status 가 버려졌을 경우 NO EFFECT BattleStatus,
     * 발생한 Status 효과량이 우위이면 null 반환 (다음 단계 진행)
     */
    private BattleStatus processDuplicatedEffectStatus(Status status, BattleStatus battleStatus) {
        log.info("processDuplicatedEffectStatus() status = {}, battleStatus = {}", status, battleStatus);
        if (battleStatus == null || battleStatus.getStatus().getStatusEffects().size() > 1) return null;
        if (status.getMaxLevel() > 0 && battleStatus.getLevel() > 0) {
            // 레벨제 스테이터스, 1렙 이상
            if (battleStatus.getLevel() < status.getMaxLevel()) {
                // 최고레벨이 아니면 레벨 증가
                battleStatus.increaseLevel();
            } else {
                // 최고레벨과 같거나 크면 효과시간 초기화
                battleStatus.resetDuration();
            }
            return battleStatus; // 레벨제의 경우, 기존 batteStatus 갱신후 반환
        } else {
            // 레벨제 스테이터스가 아님
            boolean inputStatusCovering = false; // 입력 스테이터스가 덮어쓰는지 여부
            double inputStatusEffectValue = statusUtil.getFirstStatusEffectValue(status);
            double currentStatusEffectValue = statusUtil.getFirstStatusEffectValue(battleStatus.getStatus());
            log.info("inputStatusEffectValue = {}, currentStatusEffectValue = {}", inputStatusEffectValue, currentStatusEffectValue);
            inputStatusCovering = inputStatusEffectValue == currentStatusEffectValue ?
                    // 효과량이 같으면 효과시간이 같거나 긴경우 true : 효과량이 다르면 효과량이 큰 경우 true
                    status.getDuration() >= battleStatus.getDuration() : inputStatusEffectValue > currentStatusEffectValue;
            if (inputStatusCovering) {
                // 입력 스테이터스가 덮어쓴다면, 기존의 battleStatus 삭제
                battleStatus.getBattleActor().getBattleStatuses().remove(battleStatus);
                battleStatusRepository.delete(battleStatus); // TODO 나중에 DELETE 쿼리 날아가는지 여부 확인할것
            }
            return inputStatusCovering ? null :
                    BattleStatus.builder() // 발생효과가 덮어 씌워졌으면 NO EFFECT 반환 (DB 저장x, 표시용)
                            .duration(0)
                            .status(Status.builder().type(status.getType()).name("NO EFFECT").effectText("NO EFFECT").build())
                            .level(0)
                            .iconSrc("")
                            .build();
        }
    }

    /**
     * enemy 에게 부여된 BattleStatus 중 dispel 이있는지 확인후 있으면 처리
     *
     * @param enemy
     * @return enemy 에게서 지워진 BattleStatus List (기본값 빈 리스트)
     */
    private List<BattleStatus> processMainActorDispel(BattleActor enemy) {
        List<BattleStatus> enemyRemovedBattleStatus = new ArrayList<>();
        List<BattleStatus> enemyDispelBattleStatuses = statusUtil.getBattleStatusByStatusType(enemy, StatusType.DISPEL);
        if (!enemyDispelBattleStatuses.isEmpty()) {
            // 디스펠 부여됨
            BattleStatus enemyDispelBattleStatus = enemyDispelBattleStatuses.getFirst(); // 디스펠 중첩안됨.
            // 디스펠 효과적용
            List<BattleStatus> dispelledBattleStatus = enemy.getBattleStatuses().stream()
                    .filter(status -> status.getStatus().getType() == StatusType.BUFF && status.getStatus().removable())
                    .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                    .limit(enemyDispelBattleStatus.getStatus().getStatusEffects().get(StatusEffectType.ACT_DISPEL).getValue().longValue())
                    .toList(); // 소거될 버프 (적은 디스펠의 value 값 갯수만큼만 최근에 추가된 버프부터 소거함)
            enemyRemovedBattleStatus.addAll(dispelledBattleStatus);
            enemy.getBattleStatuses().removeAll(dispelledBattleStatus);
            battleStatusRepository.deleteAll(dispelledBattleStatus);
            battleStatusRepository.delete(enemyDispelBattleStatus);
        }
        return enemyRemovedBattleStatus;
    }

    /**
     * partyMembers 각각에 부여된 BattleStatus 중 dispel 이있는지 확인후 있으면 처리
     *
     * @param partyMembers
     * @return partyMembers 에서 지워진 BattleStatus 를 order 에 맞게 넣은 리스트 (기본값 빈 리스트 4개 를 묶는 2차원 리스트)
     */
    private List<List<BattleStatus>> processEnemyDispel(List<BattleActor> partyMembers) {
        List<List<BattleStatus>> partyMemberRemovedBattleStatus = IntStream.range(0, 4).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        partyMembers.forEach(partyMember -> {
            List<BattleStatus> dispelBattleStatuses = statusUtil.getBattleStatusByStatusType(partyMember, StatusType.DISPEL);
            if (!dispelBattleStatuses.isEmpty()) {
                // 디스펠 부여됨
                List<BattleStatus> dispelGuardBattleStatues = statusUtil.getBattleStatusByStatusType(partyMember, StatusType.DISPEL_GUARD);
                if (!dispelGuardBattleStatues.isEmpty()) {
                    // 디스펠 가드가 있으면 디스펠 무효
                    partyMemberRemovedBattleStatus.get(partyMember.getCurrentOrder() - 1).addAll(dispelGuardBattleStatues); // 결과에 추가
                    battleStatusRepository.deleteAll(dispelBattleStatuses); // 디스펠 삭제
                    battleStatusRepository.deleteAll(dispelGuardBattleStatues); // 디스펠가드 삭제
                } else {
                    // 디스펠 효과 적용
                    List<BattleStatus> dispelledBattleStatus = partyMember.getBattleStatuses().stream()
                            .filter(status -> status.getStatus().getType() == StatusType.BUFF && status.getStatus().removable())
                            .toList(); // 소거될 버프 (소거 가능한 버프 전체 소거)
                    partyMemberRemovedBattleStatus.get(partyMember.getCurrentOrder() - 1).addAll(dispelledBattleStatus); // 순서에 맞게 결과에 추가
                    battleStatusRepository.deleteAll(dispelledBattleStatus); // 디스펠 된 배틀 스테이터스 삭제
                    battleStatusRepository.deleteAll(dispelBattleStatuses); // 디스펠 자체 삭제
                }
            }
        });
        return partyMemberRemovedBattleStatus;
    }

    /**
     * 배틀 스테이터스의 턴을 진행시킴. 남은시간이 감소하고 남은시간이 0턴인 배틀스테이터스는 삭제됨
     *
     * @param enemy
     * @param partyMembers
     */
    public void progressBattleStatus(BattleActor enemy, List<BattleActor> partyMembers) {
        List<BattleActor> allActors = new ArrayList<>();
        allActors.add(enemy);
        allActors.addAll(partyMembers);

        // BattleStatus 남은시간 1턴 감소
        allActors.forEach(battleActor -> battleActor.getBattleStatuses().forEach(BattleStatus::decreaseDuration));

        // 남은시간 0턴인 BattleStatus
        List<List<BattleStatus>> expiredBattleStatusesList = allActors.stream()
                .map(battleActor -> battleActor.getBattleStatuses()
                        .stream().filter(battleStatus -> battleStatus.getDuration() == 0).toList())
                .toList();
        expiredBattleStatusesList.forEach(list -> list.forEach(battleStatus -> log.info("[progressBattleStatus] listIndex = {} statusName = {}, expiredBattleStatus = {}", expiredBattleStatusesList.indexOf(list), battleStatus.getStatus().getName(), battleStatus)));

        // 남은 시간 0턴인 BAttleStatus 삭제
        expiredBattleStatusesList.forEach(battleStatuses -> {
            if (!battleStatuses.isEmpty()) {
                battleStatuses.getFirst().getBattleActor().getBattleStatuses().removeAll(battleStatuses);
                battleStatusRepository.deleteAll(battleStatuses);
            }
        });

    }


}
