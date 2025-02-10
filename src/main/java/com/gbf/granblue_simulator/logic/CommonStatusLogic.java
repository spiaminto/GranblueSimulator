package com.gbf.granblue_simulator.logic;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommonStatusLogic {

    private final BattleStatusRepository battleStatusRepository;

    /**
     * BattleActor 들을 받아 행동에 따른 스테이터스를 설정
     * 편의를 위해 파라미터로 mainActor, enemy, partyMembers 각각 분리해서 받기로.
     * Target 에 따른 분류 후 applyStatusEffectToActor 호출하여 처리
     *
     * @param mainActor    move 사용자
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public void setStatusToActors(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move) {
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
    }

    /**
     * Status 의 target 과 관계없이 Move.Status 를 파라미터로 넘어온 BattleActor 에게 부여
     * 효과 전체화 등에서 사용
     * @param battleActors
     * @param move
     */
    public void setStatusToManualTargets(List<BattleActor> battleActors, Move move) {
        move.getStatuses().forEach(status -> {
            battleActors.forEach(battleActor -> applyStatusToActor(battleActor, status));
        });
    }

    /**
     * BattleActor 에 Status 를 BattleStatus 로 적용
     * @param
     */
    protected void applyStatusToActor(BattleActor battleActor, Status status) {
        if (battleActor == null) {
            log.error("BattleActor is null, status = {}", status);
            return;
        }
//        battleActors.forEach(battleActor -> log.info("log = {}", battleActor));
        if (statusValidFilter(status, battleActor)) {
            List<BattleStatus> battleStatuses = new ArrayList<>();
            battleStatuses.add(BattleStatus.builder()
                    .battleActor(battleActor)
                    .status(status)
                    .level(status.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                    .iconSrc(status.getIconSrcs().isEmpty() ? "" : status.getIconSrcs().getFirst())
                    .build()
                    .setBattleActor(battleActor));
            // 만든 배틀스테이터스 모두 저장
//        battleStatuses.forEach(s -> log.info("log = {}", s.getBattleActor().getId()));
            battleStatusRepository.saveAll(battleStatuses);
        }
    }

    /**
     * 스테이터스 추가 전 확인
     * 스테이터스를 배틀스테이터스로 추가하지 않아도 되면 false 반환
     *
     * @param status
     * @param battleCharacter
     * @return
     */
    public boolean statusValidFilter(Status status, BattleActor battleCharacter) {
        boolean isStatusValid = true;

        // 레벨제 스테이터스가 미리 붙어있으면, 해당 스테이터스의 레벨을 올리고 입력된 스테이터스는 버린다.
        if (status.getMaxLevel() > 0) {
            for (BattleStatus battleStatus : battleCharacter.getBattleStatuses()) {
                if (battleStatus.getStatus().getId().equals(status.getId())) {
                    battleStatus.increaseLevel();
                    isStatusValid = false;
                    break;
                }
            }
        }

        // 베리어 (연산을 줄이기 위해 조건 깊이를 늘림)
        // 캐릭터가 베리어를 수치를 가지고 있음
        if (battleCharacter.getBarrier() > 0) {
            Optional<StatusEffect> inputBarrierEffect = status.getStatusEffects().stream().filter(effect -> effect.getType() == StatusEffectType.BARRIER).findFirst();
            // 적용할 스테이터스 이펙트가 배리어임
            if (inputBarrierEffect.isPresent()) {
                // 적용할 베리어 이펙트의 값이 현재 캐릭터의 배리어 수치보다 낮을경우 버림
                if (battleCharacter.getBarrier() > inputBarrierEffect.get().getValue()) {
                    isStatusValid = false;
                } else {
                    // 적용할 베리어 이펙트의 값이 현재 캐릭터의 배리어 수치보다 높을경우 현재 캐릭터의 배리어 삭제 (valid = true)
                    StatusEffect existingBarrierEffect = battleCharacter.getBattleStatuses().stream()
                            .map(BattleStatus::getStatus)
                            .map(Status::getStatusEffects)
                            .flatMap(List::stream)
                            .filter(effect -> effect.getType() == StatusEffectType.BARRIER)
                            .findFirst().orElse(null);

                    // 배리어는 반드시 Status 한칸을 차지하도록 설계되기 때문에 베리어가 포함된 BattleStatus 전체를 삭제
                    battleCharacter.setBarrier(0);
                    battleStatusRepository.deleteById(existingBarrierEffect.getStatus().getId());
                }
            }
        }


        return isStatusValid;
    }

}
