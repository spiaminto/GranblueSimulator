package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.ArrayList;

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

    /**
     * 첫 init
     * @param battleActor
     */
    public void initStatus(BattleActor battleActor) {
        calcStatusLogic.initStatus(battleActor);
    }

    /**
     * 외부에서 스테이터스값을 동기화 할때 사용
     * TODO 나중에 정리하고 없애기
     * @param battleActor
     */
    public void syncStatus(BattleActor battleActor) {
        calcStatusLogic.syncStatus(battleActor);
    }

    /**
     * BattleActor 들을 받아 행동에 따른 스테이터스를 설정
     * 편의를 위해 파라미터로 mainActor, enemy, partyMembers 각각 분리해서 받기로.
     * Target 에 따른 분류 후 applyStatusEffectToActor 호출하여 처리
     * BattleStatus 등록 후 calcStatusLogic.syncStatus() 호출
     *
     * @param mainActor    move 사용자
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public void setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
        move.getStatuses().forEach(status -> {
            switch (status.getTarget()) {
                case SELF -> applyStatusToActor(mainActor, status);
                case ENEMY -> applyStatusToActor(enemy, status);
                case PARTY_MEMBERS -> partyMembers.forEach(partyMember -> applyStatusToActor(partyMember, status));
                case ALL_PLAYERS -> {
                } // 미구현
                case NEXT_CHARACTER -> {
                } // 미구현
                default -> throw new IllegalArgumentException("Invalid target type: " + status.getTarget());
            }
        });
        
        // 스텟 재계산
        partyMembers.forEach(calcStatusLogic::syncStatus);
        calcStatusLogic.syncStatus(enemy);
    }

    /**
     * 파라미터로 넘긴 status 만 적용
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param statuses
     */
    public void setStatus(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, List<Status> statuses) {
        statuses.forEach(status -> {
            switch (status.getTarget()) {
                case SELF -> applyStatusToActor(mainActor, status);
                case ENEMY -> applyStatusToActor(enemy, status);
                case PARTY_MEMBERS -> partyMembers.forEach(partyMember -> applyStatusToActor(partyMember, status));
                case ALL_PLAYERS -> {
                } // 미구현
                case NEXT_CHARACTER -> {
                } // 미구현
                default -> throw new IllegalArgumentException("Invalid target type: " + status.getTarget());
            }
        });

        // 스텟 재계산
        partyMembers.forEach(calcStatusLogic::syncStatus);
        calcStatusLogic.syncStatus(enemy);

    }

    /**
     * Status 의 target 과 관계없이 Move.Status 를 파라미터로 넘어온 BattleActor 에게 부여
     * 효과 전체화 등에서 사용
     * 등록후 calcStatusLogic.syncStatus() 호출
     *
     * @param battleActors
     * @param move
     */
    public void setStatusToManualTargets(List<BattleActor> battleActors, Move move) {
        move.getStatuses().forEach(status -> {
            battleActors.forEach(battleActor -> applyStatusToActor(battleActor, status));
        });

        // 스텟 재계산
        battleActors.forEach(calcStatusLogic::syncStatus);
    }


    /**
     * BattleActor 에 Status 를 BattleStatus 로 적용
     *
     * @param
     */
    protected Status applyStatusToActor(BattleActor battleActor, Status status) {
        if (statusValidFilter(status, battleActor)) {
            BattleStatus battleStatus = BattleStatus.builder()
                    .battleActor(battleActor)
                    .duration(status.getDuration())
                    .status(status)
                    .level(status.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                    .iconSrc(status.getIconSrcs().isEmpty() ? "" : status.getIconSrcs().getFirst())
                    .build()
                    .setBattleActor(battleActor);
            battleStatusRepository.save(battleStatus);
            return status;
        }  else {
            return null;
        }
    }

    /**
     * 스테이터스 추가 전 확인
     * 스테이터스를 배틀스테이터스로 추가하지 않아도 되면 false 반환
     *
     * @param status
     * @param battleActor
     * @return
     */
    protected boolean statusValidFilter(Status status, BattleActor battleActor) {
        boolean isStatusValid = true;
        StatusEffect firstStatusEffect = status.getStatusEffects().entrySet().iterator().next().getValue();
        
        // 오의 게이지 업 -> 반드시 하나의 StatusEffect 로 구성됨
        if (firstStatusEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP) {
            chargeGaugeLogic.processChargeGaugeFromSetStatus(battleActor, firstStatusEffect);
            return false;
        }

        // 레벨제 스테이터스
        if (status.getMaxLevel() > 0) {
            // 레벨제 스테이터스의 경우
            isStatusValid = statusUtil.getSameIdBattleStatus(battleActor, status).map(battleStatus -> {
                // status.id 가 동일한 battleStatus 가 이미 존재하는 경우
                if (battleStatus.getLevel() < status.getMaxLevel()) {
                    // 최고레벨이 아니면 레벨 증가
                    battleStatus.increaseLevel();
                } else {
                    // 최고레벨과 같거나 크면 효과시간 초기화
                    battleStatus.resetDuration();
                }
                return false; // 들어온 status 버림
            }).orElseGet(() -> true); // 동일한 battleStatus 없음
        }

        // 덮어쓰는 효과 (공존불가 이펙트, 레벨제면 안됨)
        if (status.getMaxLevel() < 1 && firstStatusEffect.getType().isCoveringEffect()) { // -> 반드시 하나의 Status 를 차지하는 버프여야 제대로 적용됨. StatusEffect 두개이상인 경우 로직문제발생
            // 덮어쓰는 효과인 경우
            isStatusValid = statusUtil.getSameEffectTypeStatus(battleActor, status).map(battleStatus -> {
                boolean isValid = true;
                double inputStatusEffectValue = statusUtil.getStatusEffectValue(status);
                double currentStatusEffectValue = statusUtil.getStatusEffectValue(battleStatus.getStatus());
                if (inputStatusEffectValue == currentStatusEffectValue) {
                    // 들어온 status 와 적용된 battleStatus 의 효과량이 같은경우 들어온 status 의 효과시간이 같거나 긴경우 valid true
                    isValid = status.getDuration() >= battleStatus.getDuration();
                } else {
                    // 들어온 status 의 효과량이 큰경우 valid true
                    isValid = inputStatusEffectValue > currentStatusEffectValue;
                }

                if (isValid) {
                    // 들어온 Status 가 valid 인경우 기존 battleStatus 삭제
                    battleStatus.getBattleActor().getBattleStatuses().remove(battleStatus);
                    battleStatusRepository.delete(battleStatus); // TODO 나중에 DELETE 쿼리 날아가는지 여부 확인할것
                }

                return isValid;
            }).orElseGet(() -> true);

        }

//        덮어쓰는 효과로 대체됨. 테스트 후 삭제 예정
        // 베리어 (연산을 줄이기 위해 조건 깊이를 늘림) 
//        if (battleActor.getBarrier() > 0) {
//            // 캐릭터가 베리어를 수치를 가지고 있음
//            Optional<StatusEffect> inputBarrierEffect = status.getStatusEffects().stream().filter(effect -> effect.getType() == StatusEffectType.BARRIER).findFirst();
//            if (inputBarrierEffect.isPresent()) {
//                // 적용할 스테이터스 이펙트가 배리어임
//                if (battleActor.getBarrier() > inputBarrierEffect.get().getValue()) {
//                    // 적용할 베리어 이펙트의 값이 현재 캐릭터의 배리어 수치보다 낮을경우 버림
//                    isStatusValid = false;
//                } else {
//                    // 적용할 베리어 이펙트의 값이 현재 캐릭터의 배리어 수치보다 높을경우 현재 캐릭터의 배리어 삭제 (valid = true)
//                    StatusEffect existingBarrierEffect = battleActor.getBattleStatuses().stream()
//                            .map(BattleStatus::getStatus)
//                            .map(Status::getStatusEffects)
//                            .flatMap(List::stream)
//                            .filter(effect -> effect.getType() == StatusEffectType.BARRIER)
//                            .findFirst().orElse(null);
//
//                    // 배리어는 반드시 Status 한칸을 차지하도록 설계되기 때문에 베리어가 포함된 BattleStatus 전체를 삭제
//                    battleActor.setBarrier(0);
//                    battleStatusRepository.deleteById(existingBarrierEffect.getStatus().getId());
//                }
//            }
//        }


        return isStatusValid;
    }

}
