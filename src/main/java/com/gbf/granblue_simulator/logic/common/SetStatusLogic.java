package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
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
//TODO 디스펠처리, 오의게이지처리, 힐처리, 버프/디버프 명중률 처리
public class SetStatusLogic {

    private final BattleStatusRepository battleStatusRepository;
    private final StatusUtil statusUtil;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final CalcStatusLogic calcStatusLogic;
    private final BattleCharacterRepository battleCharacterRepository;

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
     * Status 의 target 과 관계없이 Move.Status 를 파라미터로 넘어온 BattleActor 에게 부여
     * 효과 전체화 등에서 사용
     * 등록후 calcStatusLogic.syncStatus() 호출
     * 편의를 위해 enemy, partyMembers 의 정보도 받음
     *
     * @param targets
     * @param move
     */
    public SetStatusResult setStatusToManualTargets(List<BattleActor> targets, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        return applyStatusToManualTargets(targets, enemy, partyMembers, move.getStatuses());
    }

    /**
     * Status 의 target 과 관계없이 넘어온 statuses 부여
     * 효과 전체화 등에서 사용
     * 등록후 calcStatusLogic.syncStatus() 호출
     * 편의를 위해 enemy, partyMembers 의 정보도 받음
     *
     * @param targets
     * @param statuses
     */
    public SetStatusResult setStatusToManualTargets(List<BattleActor> targets, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        return applyStatusToManualTargets(targets, enemy, partyMembers, statuses);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param statuses     Move.statuses 또는 임의의 Status
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        return applyStatus(mainActor, enemy, partyMembers, statuses);
    }

    /**
     * Move 를 받아 해당 Move 의 Status 리스트를 타겟에 맞춰 BattleStatus 로 set
     * Move 에 '랜덤효과 N개 부여' 효과 적용
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        List<Status> statuses = move.getStatuses();
        if (move.getRandomStatusCount() != null && move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(statuses);
            statuses = statuses.subList(0, move.getRandomStatusCount());
        }
        return applyStatus(mainActor, enemy, partyMembers, statuses);
    }

    /**
     * Status 의 target 과 관계없이 넘어온 statuses 부여
     * 효과 전체화 등에서 사용
     * 등록후 calcStatusLogic.syncStatus() 호출
     * 편의를 위해 enemy, partyMembers 의 정보도 받음
     *
     * @param targets
     * @param statuses
     */
    protected SetStatusResult applyStatusToManualTargets(List<BattleActor> targets, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        List<BattleStatus> enemyAddedBattleStatus = new ArrayList<>();
        List<List<BattleStatus>> partyMemberAddedBattleStatus = IntStream.range(0, 4).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());

        statuses.forEach(status -> {
            targets.forEach(battleActor -> {
                BattleStatus addedBattleStatus = applyStatusToActor(battleActor, status);
                if (battleActor.isEnemy()) enemyAddedBattleStatus.add(addedBattleStatus);
                else partyMemberAddedBattleStatus.get(battleActor.getCurrentOrder() - 1).add(addedBattleStatus);
            });
        });

        // 스텟 재계산
        targets.forEach(calcStatusLogic::syncStatus);

        return SetStatusResult.builder()
                .partyMemberAddedStatuses(partyMemberAddedBattleStatus)
                .enemyAddedStatuses(enemyAddedBattleStatus)
                .build();
    }

    protected SetStatusResult applyStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        boolean isPartyMemberDispelled = false;
        boolean enemyDispelled = false;
        List<BattleStatus> enemyAddedBattleStatus = new ArrayList<>();
        List<List<BattleStatus>> partyMemberAddedBattleStatus = IntStream.range(0, 4).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());

        // 스테이터스를 각각의 타깃별로 적용 후 결과에 순서에 맞게 넣음
        statuses.forEach(status -> {
            switch (status.getTarget()) {
                case SELF -> {
                    if (mainActor.isEnemy()) enemyAddedBattleStatus.add(applyStatusToActor(enemy, status));
                    else partyMemberAddedBattleStatus.get(mainActor.getCurrentOrder() - 1).add(applyStatusToActor(mainActor, status));
                }
                case ENEMY -> enemyAddedBattleStatus.add(applyStatusToActor(enemy, status));
                case PARTY_MEMBERS ->
                        partyMembers.forEach(partyMember -> partyMemberAddedBattleStatus.get(partyMember.getCurrentOrder() - 1).add(applyStatusToActor(partyMember, status)));
                case ALL_PLAYERS -> {
                } // 미구현
                case NEXT_CHARACTER -> {
                } // 미구현
                default -> throw new IllegalArgumentException("Invalid target type: " + status.getTarget());
            }
        });

        // 아군 디스펠 피격 처리 -> 아군은 모든 소거 가능한 버프가 전부 사라짐
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
        isPartyMemberDispelled = !partyMemberRemovedBattleStatus.isEmpty();

        // 적군 디스펠 피격 처리
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
        enemyDispelled = !enemyRemovedBattleStatus.isEmpty();

        // 스텟 재계산
        partyMembers.forEach(calcStatusLogic::syncStatus);
        calcStatusLogic.syncStatus(enemy);
        // 적인경우 partyMembers 로 target 이 들어옴
        // TODO 나중에 전체적으로 리팩토링해야됨

        partyMemberAddedBattleStatus.forEach(
                addedStatuses -> log.info("addedStatuses = {}", addedStatuses)
        );

        return SetStatusResult.builder()
                .enemyAddedStatuses(enemyAddedBattleStatus)
                .enemyRemovedStatuses(enemyRemovedBattleStatus)
                .partyMemberAddedStatuses(partyMemberAddedBattleStatus)
                .partyMemberRemovedStatuses(partyMemberRemovedBattleStatus)
                .partyMemberDispelled(isPartyMemberDispelled)
                .enemyDispelled(enemyDispelled)
                .build();
    }


    /**
     * BattleActor 에 Status 를 BattleStatus 로 적용
     * 파라미터로 들어온 Status 를 기반으로 생성된 BattleStatus 를 반환함.
     * Status 가 기존의 BattleStatus 와 중첩되어 기존의 BattleStatus 가 업데이트 될 경우 해당 BattleStatus 를 반환하고 파라미터로 들어온 STatus 는 무시함
     *
     * @param battleActor
     * @param status
     * @return BattleStatus
     */
    protected BattleStatus applyStatusToActor(BattleActor battleActor, Status status) {
        BattleStatus resultBattleStatus = null; // 결과로 반환할 스테이터스
        StatusEffect firstStatusEffect = status.getStatusEffects().entrySet().iterator().next().getValue();

        // 같은 Status.id 를 가진 스테이터스 -> 주로 고유스테이터스, 자신의 일반스테이터스 중복부여를 처리
        resultBattleStatus = statusUtil.getSameIdBattleStatus(battleActor, status).map(battleStatus -> {
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
            return battleStatus; // 들어온 status 버림
        }).orElseGet(() -> null);// 동일한 battleStatus 없음
        if (resultBattleStatus != null) return resultBattleStatus;

        // 단일 StatusEffect 로 구성되며, 같은 StatusEffect 가진 스테이터스 -> 주로 일반스테이터스를 처리
        if (status.getStatusEffects().size() == 1) {
            resultBattleStatus = statusUtil.getSameEffectTypeStatus(battleActor, status).map(battleStatus -> {
                boolean inputStatusCovering = false; // 입력 스테이터스가 덮어쓰는지 여부
                if (status.getMaxLevel() > 0 && battleStatus.getLevel() > 1) {
                    // 레벨제 스테이터스, 1렙 이상
                    if (battleStatus.getLevel() < status.getMaxLevel()) {
                        // 최고레벨이 아니면 레벨 증가
                        battleStatus.increaseLevel();
                    } else {
                        // 최고레벨과 같거나 크면 효과시간 초기화
                        battleStatus.resetDuration();
                    }
                    return battleStatus; // 레벨제의 경우 기존 레벨변화하고 들어온건 버림
                } else {
                    // 레벨제 스테이터스가 아님
                    double inputStatusEffectValue = statusUtil.getFirstStatusEffectValue(status);
                    double currentStatusEffectValue = statusUtil.getFirstStatusEffectValue(battleStatus.getStatus());
                    inputStatusCovering = inputStatusEffectValue == currentStatusEffectValue ?
                            // 효과량이 같으면 효과시간이 같거나 긴경우 true : 효과량이 다르면 효과량이 큰 경우 true
                            status.getDuration() >= battleStatus.getDuration() : inputStatusEffectValue > currentStatusEffectValue;
                    if (inputStatusCovering) {
                        // 입력 스테이터스가 덮어쓴다면, 기존의 battleStatus 삭제
                        battleStatus.getBattleActor().getBattleStatuses().remove(battleStatus);
                        battleStatusRepository.delete(battleStatus); // TODO 나중에 DELETE 쿼리 날아가는지 여부 확인할것
                    }
                }
                return inputStatusCovering ? null : battleStatus; // 덮어쓸경우 null, 기존 업데이트하면 기존거 반환
            }).orElseGet(() -> null);
            if (resultBattleStatus != null) return resultBattleStatus;
        }

        // 오의 게이지 업 -> 반드시 하나의 StatusEffect 로 구성됨
        if (firstStatusEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP) {
            chargeGaugeLogic.processChargeGaugeFromSetStatus(battleActor, firstStatusEffect);
        }
        
        // 새로 추가되는 버프
        BattleStatus battleStatus = BattleStatus.builder()
                .battleActor(battleActor)
                .duration(status.getDuration())
                .status(status)
                .level(status.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                .iconSrc(status.getIconSrcs().isEmpty() ? "" : status.getIconSrcs().getFirst())
                .build()
                .setBattleActor(battleActor);
        battleStatusRepository.save(battleStatus);
        return battleStatus;
    }

    /**
     * 배틀 스테이터스의 턴을 진행시킴. 남은시간이 감소하고 남은시간이 0턴인 배틀스테이터스는 삭제됨
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
        expiredBattleStatusesList.forEach(list -> list.forEach(battleStatus -> log.info("[progressBattleStatus] listIndex = {} statusName = {}, expiredBattleStatus = {}",expiredBattleStatusesList.indexOf(list), battleStatus.getStatus().getName(), battleStatus)));

        // 남은 시간 0턴인 BAttleStatus 삭제
        expiredBattleStatusesList.forEach(battleStatuses -> {
            if (!battleStatuses.isEmpty()) {
                battleStatuses.getFirst().getBattleActor().getBattleStatuses().removeAll(battleStatuses);
                battleStatusRepository.deleteAll(battleStatuses);
            }
        });
        
    }


}
