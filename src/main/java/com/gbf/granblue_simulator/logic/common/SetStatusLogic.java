package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
// TODO 힐처리
public class SetStatusLogic {

    private final BattleStatusRepository battleStatusRepository;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final CalcStatusLogic calcStatusLogic;

    /**
     * Move 를 받아 해당 Move 의 Status 리스트를 타겟에 맞춰 BattleStatus 로 set
     * Move 에 '랜덤효과 N개 부여' 인 경우 적용하여 set
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        List<Status> statuses = move.getStatuses();
        if (move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(statuses);
            statuses = statuses.subList(0, move.getRandomStatusCount());
        }
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, null);
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
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, null);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set 하며, StatusTargetType modifyingTargetType 을 추가로 받아 Status.targetType 대신 사용
     * 주로 SELF 스테이터스의 효과를 PARTY_MEMBERS 로 전체화 할때 사용한다.
     *
     * @param mainActor           move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers        mainActor 를 포함한 전체 파티원
     * @param statuses            Move.statuses 또는 임의의 Status
     * @param modifyingTargetType 기존 Status.targetType 대신 사용할 타겟 타입
     */
    public SetStatusResult setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses, StatusTargetType modifyingTargetType) {
        return this.applyStatus(mainActor, enemy, partyMembers, statuses, modifyingTargetType);
    }

    /**
     * 스테이터스를 적용하는 메인로직
     * 여기서 타겟을 정한 뒤 타겟별로 applyStatusToActor 를 실행해 발생한 Status 를 BattleStatus 로 변환하여 리스트에 담는다.
     * 디스펠 처리와 스테이터스 및 스텟 재계산 처리도 함.
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param statuses
     * @param modifiedTargetType
     * @return SetStatusResult 스테이터스 처리 결과 DTO
     */
    protected SetStatusResult applyStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses, StatusTargetType modifiedTargetType) {
        List<List<BattleStatus>> addedBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        List<List<BattleStatus>> removedBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, 0));
        // 처리 순서에 맞게 정렬
        List<Status> sortedStatuses = statuses.stream().sorted(Comparator.comparing(status -> status.getType().getProcessOrder())).toList();

        // 스테이터스를 각각의 타깃별로 적용 후 결과에 순서에 맞게 넣음
        sortedStatuses.forEach(status -> {
            // 타겟 타입 변경여부 처리
            StatusTargetType statusTargetType = modifiedTargetType != null ? modifiedTargetType : status.getTarget();
            // 타겟 타입별 타겟 가져와서
            this.getTargets(mainActor, enemy, partyMembers, statusTargetType).forEach(targetActor -> {
                // 타겟 BattleActor 별로 스테이터스 적용
                ApplyStatusToActorResult result = this.applyStatusToActor(mainActor, targetActor, status);
                // 적용(추가)된 BattleStatus 를 반환 리스트에 추가 (dispel 등에서 null)
                if (result.getAddedBattleStatus() != null) addedBattleStatusesList.get(targetActor.getCurrentOrder()).add(result.getAddedBattleStatus());
                // 지워진(디스펠, 클리어) BattleStatus 를 반환 리스트에 추가
                removedBattleStatusesList.get(targetActor.getCurrentOrder()).addAll(result.getDispelledBattleStatus());
                // 힐 추가
                healValues.set(targetActor.getCurrentOrder(), healValues.get(targetActor.getCurrentOrder()) + result.getHealValue());
            });
        });

        // 스텟 재계산
        partyMembers.forEach(calcStatusLogic::syncStatus);
        calcStatusLogic.syncStatus(enemy);

        // TODO 나중에 힐 처리까지 하고 나서 분리해야 할듯
        // ============================================================================

        // 로깅
//        partyMemberAddedBattleStatus.forEach(addedStatuses -> log.info("partyMemberIndex = {}, addedStatuses = {}", partyMemberAddedBattleStatus.indexOf(addedStatuses), addedStatuses));

        return SetStatusResult.builder()
                .addedStatusesList(addedBattleStatusesList)
                .removedStatuesList(removedBattleStatusesList)
                .healValues(healValues)
                .build();
    }

    private List<BattleActor> getTargets(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, StatusTargetType targetType) {
        List<BattleActor> resultTargets = new ArrayList<>();
        switch (targetType) {
            case SELF -> resultTargets.add(mainActor);
            case SELF_AND_NEXT_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(battleActor -> battleActor.getCurrentOrder() == mainActor.getCurrentOrder() + 1).findFirst().ifPresent(resultTargets::add);
            }
            case SELF_AND_MAIN_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().ifPresent(resultTargets::add);
            }
            case MAIN_CHARACTER ->
                    partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().ifPresent(resultTargets::add);
            case ENEMY -> resultTargets.add(enemy);
            case PARTY_MEMBERS -> resultTargets.addAll(partyMembers);
            case PARTY_MEMBERS_NOT_SELF -> resultTargets.addAll(partyMembers.stream().filter(battleActor -> !battleActor.getId().equals(mainActor.getId())).toList());
            case ALL_PLAYERS -> {
            }
            default -> throw new IllegalArgumentException("getTargets() Invalid target type: " + targetType);
        }
        return resultTargets;
    }

    /**
     * 발생한 Status 를 BattleStatus 로 변환하는 메인 로직
     * 처리 결과에 따라 기존 BattleStatus, Miss BattleStatus, No Effect BattleStatus, 새로운 BattleStatus 를 반환하며
     * 새로 발생한 BattleStatus 의 경우 DB 에 저장한다.
     *
     * @param mainActor   스테이터스를 발생시킨 대상
     * @param targetActor 스테이터스를 받은 대상
     * @param status      발생한 스테이터스
     * @return BattleStatus
     */
    protected ApplyStatusToActorResult applyStatusToActor(BattleActor mainActor, BattleActor targetActor, Status status) {
        log.info("[applyStatusToActor] mainActorName = {}, targetActorName = {} , \n status = {} \n effects = {}", mainActor.getName(), targetActor.getName(), status, status.getStatusEffects());

        // 디버프 명중처리
        if (status.getType() == StatusType.DEBUFF || status.getType() == StatusType.DEBUFF_FOR_ALL) {
            BattleStatus missBattleStatus = this.processDebuffResistance(mainActor, targetActor, status);
            if (missBattleStatus != null)
                return ApplyStatusToActorResult.builder().addedBattleStatus(missBattleStatus).build(); // 명중에 실패하면 Miss BattleStatus 바로 반환
        }

        // 중복 id 스테이터스 처리
        BattleStatus updatedBattleStatus = getSameIdBattleStatus(targetActor, status)
                .map(battleStatus -> this.processDuplicatedIdStatus(status, battleStatus)).orElseGet(() -> null);
        if (updatedBattleStatus != null)
            return ApplyStatusToActorResult.builder().addedBattleStatus(updatedBattleStatus).build(); // 갱신된 기존 효과 반환

        // 이펙트 타입 겹침 처리
        if (status.getStatusEffects().size() == 1 && status.getStatusEffects().get(StatusEffectType.NONE) == null) {
            BattleStatus coveringBattleStatus = getSameEffectTypeStatus(targetActor, status)
                    .map(battleStatus -> this.processDuplicatedEffectStatus(status, battleStatus)).orElseGet(() -> null);
            if (coveringBattleStatus != null)
                return ApplyStatusToActorResult.builder().addedBattleStatus(coveringBattleStatus).build(); // 레벨제의 경우 갱신된 기존 효과, 기존효과가 상위인 경우 No Effect BattleStatus 바로 반환
        }

        StatusEffect firstStatusEffect = status.getStatusEffects().entrySet().iterator().next().getValue();
        // 오의 게이지 업(반드시 하나의 StatusEffect 로 구성됨) 별도처리
        if (firstStatusEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP || firstStatusEffect.getType() == StatusEffectType.ACT_FATAL_CHAIN_GAUGE_UP) {
            BattleStatus gaugeUpBattleStatus = processGaugeUpEffectStatus(targetActor, firstStatusEffect);
            return ApplyStatusToActorResult.builder().addedBattleStatus(gaugeUpBattleStatus).build();
        }

        // 디스펠 처리
        if (status.getType() == StatusType.DISPEL) {
            List<BattleStatus> dispelledBattleStatuses = processDispel(targetActor, status);
            return ApplyStatusToActorResult.builder().dispelledBattleStatus(dispelledBattleStatuses).build();
        }

        // 클리어 처리
        if (status.getType() == StatusType.CLEAR || status.getType() == StatusType.CLEAR_FOR_ALL) {
            List<BattleStatus> clearedBattleStatuses = processClear(targetActor, status);
            return ApplyStatusToActorResult.builder().clearedBattleStatus(clearedBattleStatuses).build();
        }

        // 힐 처리
        if (status.getType() == StatusType.HEAL || status.getType() == StatusType.HEAL_FOR_ALL) {
            int healValue = processHeal(targetActor, status);
            return ApplyStatusToActorResult.builder().healValue(healValue).build();
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
        return ApplyStatusToActorResult.builder().addedBattleStatus(addedBattleStatus).build();
    }

    @Data
    @Builder
    static class ApplyStatusToActorResult {
        private BattleStatus addedBattleStatus;
        @Builder.Default
        private List<BattleStatus> dispelledBattleStatus = new ArrayList<>();
        @Builder.Default
        private List<BattleStatus> clearedBattleStatus = new ArrayList<>();
        private int healValue;
    }

    /**
     * 디버프 명중 처리
     *
     * @param mainActor   디버프 명중률을 사용할 메인액터
     * @param targetActor 디버프 내성을 사용할 타겟
     * @return 디버프 명중에 실패할 경우 MISS BattleStatus 반환, 명중하면 null (다음단계진행)
     */
    private BattleStatus processDebuffResistance(BattleActor mainActor, BattleActor targetActor, Status status) {
        if (!status.isResistible()) return null; // 필중 : 마운트와 약체명중을 관통한다
        // 마운트 처리 - RESIST
        BattleStatus mountBattleStatus = getBattleStatusByEffectType(targetActor, StatusEffectType.MOUNT);
        if (mountBattleStatus != null) {
            mountBattleStatus.expireAtTurnEnd(); // 마운트는 발동시 해당 턴 종료시 삭제됨
            return BattleStatus.builder()
                    .duration(0)
                    .status(Status.builder().type(status.getType()).name("RESIST").effectText("RESIST").build())
                    .level(0)
                    .iconSrc("")
                    .build()
                    .setBattleActor(targetActor);
        }
        // 약체 명중 처리 - MISS
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
        log.info("[processDuplicatedEffectStatus] \n status = {}, \n battleStatus = {}", status, battleStatus);
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
            double inputStatusEffectValue = getFirstStatusEffectValue(status);
            double currentStatusEffectValue = getFirstStatusEffectValue(battleStatus.getStatus());
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
                            .build()
                            .setBattleActor(battleStatus.getBattleActor());
        }

    }

    private BattleStatus processGaugeUpEffectStatus(BattleActor targetActor, StatusEffect gaugeUpEffect) {
        if (gaugeUpEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP) {
            chargeGaugeLogic.processChargeGaugeFromSetStatus(targetActor, gaugeUpEffect);
        } else if (gaugeUpEffect.getType() == StatusEffectType.ACT_FATAL_CHAIN_GAUGE_UP) {
            chargeGaugeLogic.processFatalChainGaugeFromSetStatus(targetActor, gaugeUpEffect);
        } else {
            throw new IllegalArgumentException("Not supported gauge up effect type : " + gaugeUpEffect.getType());
        }

        return BattleStatus.builder()
                .duration(0)
                .status(Status.builder().type(StatusType.BUFF).name("오의게이지 상승").effectText("오의게이지 상승").build())
                .level(0)
                .iconSrc("")
                .build()
                .setBattleActor(targetActor);
    }

    private List<BattleStatus> processDispel(BattleActor target, Status dispelStatus) {
        List<BattleStatus> dispelGuardStatuses = getBattleStatuesByStatusType(target, StatusType.DISPEL_GUARD);
        if (!dispelGuardStatuses.isEmpty()) {
            // 디스펠 가드 성공
            BattleStatus dispelGuardStatus = dispelGuardStatuses.getFirst(); // 중복 불가긴 함
            target.getBattleStatuses().remove(dispelGuardStatus);
            battleStatusRepository.delete(dispelGuardStatus);
            return List.of(dispelGuardStatus);
        }
        List<BattleStatus> dispelledBattleStatuses = target.getBattleStatuses().stream()
                .filter(status -> status.getStatus().getType().isBuff() && status.getStatus().removable())
                .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                .limit((int) dispelStatus.getStatusEffects().get(StatusEffectType.ACT_DISPEL).getValue()) // 적의 dispel 은 99정도로 들어옴
                .toList();
        target.getBattleStatuses().removeAll(dispelledBattleStatuses);
        battleStatusRepository.deleteAll(dispelledBattleStatuses);
        // CHECK BUFF_FOR_ALL 에 대한 DISPEL 처리 동기화 미구현
        return dispelledBattleStatuses;
    }

    private List<BattleStatus> processClear(BattleActor target, Status clearStatus) {
        List<BattleStatus> clearedBattleStatuses = target.getBattleStatuses().stream()
                .filter(status -> status.getStatus().getType().isDebuff() && status.getStatus().removable())
                .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                .limit((int) clearStatus.getStatusEffects().get(StatusEffectType.ACT_CLEAR).getValue())
                .toList(); // 해제될 디버프 (클리어의 value 값 갯수만큼만 최근에 추가된 디버프부터 해제함)
        target.getBattleStatuses().removeAll(clearedBattleStatuses);
        battleStatusRepository.deleteAll(clearedBattleStatuses);
        // CHECK CLEAR_FOR_ALL 처리 미구현
        return clearedBattleStatuses;
    }

    private int processHeal(BattleActor target, Status healStatus) {
        Integer currentHp = target.getHp();
        int healInitValue = (int) healStatus.getStatusEffects().get(StatusEffectType.ACT_HEAL).getValue();
        double healUpRate = getEffectValueSum(target, StatusEffectType.HEAL_UP);
        double healDownRate = getEffectValueSum(target, StatusEffectType.HEAL_DOWN);
        double resultHealRate = Math.clamp(0, 1 + healUpRate + healDownRate, 2.0); // 하한 0 상한 2 (100%증가)
        Integer healResultValue = (int) (healInitValue * resultHealRate);
        Integer healedHp = currentHp + healResultValue;
        target.setHp(healedHp);
        log.info("[processPartyHeal] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        // CHECK HEAL_FOR_ALL 미구현
        return healResultValue;
    }

    /**
     * battleActor 의 battleStatus 중 statusIds 에 해당하는 모든 스테이터스의 레벨을 level 만큼 증가
     * 사용자에게 결과 반환 없이 임의로 레벨 조작시 사용
     *
     * @param battleActor 증가시킬 대상
     * @param level       증가량 (목표 값이 아님)
     * @param statusIds   증가시킬 battleStatus 의 statusId
     */
    public void addBattleStatusesLevel(BattleActor battleActor, int level, Long... statusIds) {
        for (Long statusId : statusIds)
            this.addBattleStatusLevel(battleActor, level, statusId);
    }

    /**
     * battleActor 의 battleStatus 중 statusId 에 해당하는 스테이터스의 레벨을 level 만큼 증가
     * 사용자에게 결과 반환 없이 임의로 레벨 조작시 사용
     *
     * @param battleActor
     * @param level       증가량 (목표 값이 아님)
     * @param statusId    증가할 battleStatus 의 원본 status
     */
    public void addBattleStatusLevel(BattleActor battleActor, int level, Long statusId) {
        battleActor.getBattleStatuses().stream()
                .filter(battleStatus -> battleStatus.getStatus().getId().equals(statusId))
                .findFirst()
                .ifPresent(battleStatus -> battleStatus.addLevel(level));
        calcStatusLogic.syncStatus(battleActor); // 갱신
    }

    /**
     * battleActor 의 battleStatus 중 statusId 에 해당하는 스테이터스의 레벨을 level 만큼 감소
     * 레벨 감소의 경우 사용자에게 감소정보를 노출해야하기 때문에 SetStatusResult 를 반환
     *
     * @param battleActor
     * @param level        감소량 (목표 값이 아님)
     * @param battleStatus 레벨이 감소할 battleStatus
     * @return setStatusResult
     */
    public SetStatusResult subtractBattleStatusLevel(BattleActor battleActor, int level, BattleStatus battleStatus) {
        List<List<BattleStatus>> addedBattleStatuses = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        List<List<BattleStatus>> removedBattleStatuses = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());

        battleStatus.subtractLevel(level);

        if (battleStatus.getLevel() <= 0) {
            battleStatusRepository.delete(battleStatus);
            battleActor.getBattleStatuses().remove(battleStatus);
            removedBattleStatuses.get(battleActor.getCurrentOrder()).add(battleStatus); // 삭제되면 이쪽
        } else addedBattleStatuses.get(battleActor.getCurrentOrder()).add(battleStatus); // 레벨 내려가면 추가와 동일하게 표시

        calcStatusLogic.syncStatus(battleActor); // 갱신
        return SetStatusResult.builder()
                .addedStatusesList(addedBattleStatuses)
                .removedStatuesList(removedBattleStatuses)
                .build();
    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 연장
     *
     * @param battleStatuses
     * @param duration
     */
    public void extendBattleStatuses(List<BattleStatus> battleStatuses, Integer duration) {
        battleStatuses.forEach(battleStatus -> this.extendBattleStatus(battleStatus, duration));
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 연장
     *
     * @param battleStatus
     * @param duration
     */
    public void extendBattleStatus(BattleStatus battleStatus, Integer duration) {
        if (battleStatus.getDuration() > 0) battleStatus.addDuration(duration); // 남은 시간이 1턴 이상이여야 연장가능 (0턴은 의사적으로 감소시킨 수치)
    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 단축
     *
     * @param battleStatuses
     * @param duration
     */
    public void shortenBattleStatuses(List<BattleStatus> battleStatuses, Integer duration) {
        battleStatuses.forEach(battleStatus -> this.shortenBattleStatus(battleStatus, duration));
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 단축
     *
     * @param battleStatus
     * @param duration
     */
    public void shortenBattleStatus(BattleStatus battleStatus, Integer duration) {
        battleStatus.subtractDuration(duration);
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param battleActor
     * @param battleStatuses
     */
    public void removeBattleStatuses(BattleActor battleActor, List<BattleStatus> battleStatuses) {
        battleStatuses.forEach(battleStatus -> removeBattleStatus(battleActor, battleStatus));
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param battleActor
     * @param battleStatus
     */
    public void removeBattleStatus(BattleActor battleActor, BattleStatus battleStatus) {
        if (battleStatus == null) return;
        battleActor.getBattleStatuses().remove(battleStatus);
        battleStatusRepository.delete(battleStatus);
    }

    /**
     * 배틀 스테이터스의 턴을 진행시킴. 남은시간이 감소하고 남은시간이 0턴인 배틀스테이터스는 삭제됨
     * 캐릭터의 턴종효과 보다 나중에 발동해야함 (강화효과 연장 등 적용 후 발동해야 함)
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
