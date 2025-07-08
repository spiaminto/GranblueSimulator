package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.Party;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleCharacter;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.logic.common.CalcStatusLogic;
import com.gbf.granblue_simulator.repository.*;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ActorRepository actorRepository;
    private final PartyRepository partyRepository;
    private final BattleActorRepository battleActorRepository;
    private final BattleLogic battleLogic;
    private final MoveRepository moveRepository;
    private final CalcStatusLogic calcStatusLogic;
    private final BattleStatusRepository battleStatusRepository;

    public boolean updateChargeAttackOn(Long roomId, Long userId, boolean chargeAttackOn) {
        Member member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        member.setChargeAttackOn(chargeAttackOn);
        return member.isChargeAttackOn();
    }

    public Member enterRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("없는 방"));

        Member member = Member.builder()
                .user(user)
                .room(room)
                .currentTurn(1)
                .build();

        memberRepository.save(member);

        createBattleActors(member); // 배틀 액터 생성 및 시작

        log.info("[MemberService] user = {}, room = {}", user, room);

        return member;
    }

    private List<BattleActor> createBattleActors(Member member) {
        Party party = partyRepository.findById(member.getUser().getPrimaryPartyId()).orElseThrow(() -> new IllegalArgumentException("없는 파티"));

        Map<Long, Actor> actorMap = actorRepository.findAllById(party.getActorIds()).stream()
                .collect(Collectors.toMap(Actor::getId, Function.identity()));
        List<Actor> actors = party.getActorIds().stream()
                .map(actorMap::get)
                .filter(Objects::nonNull)
                .toList(); // in 쿼리 순서고정
        actors.forEach(actor -> log.info("[createBattleActors] actor = {}", actor) );

        Actor enemyActor = actorRepository.findById(7L).get(); // CHECK 현재 디아스포라로 고정

        // 파티 생성
        List<BattleActor> partyMembers = actors.stream()
                .map(actor -> {
                            Long fatalChainMoveId = actor.isMainCharacter() ?
                                    moveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, actor.getElementType()).getFirst().getId() :
                                    null;
                            List<Move> summons = actor.isMainCharacter() ?
                                    moveRepository.findAllById(party.getSummonMoveIds()) :
                                    Collections.emptyList();
                            List<Long> summonMoveIds = summons.stream().map(Move::getId).toList();
                            List<Integer> summonCoolDowns = summons.stream().map(Move::getCoolDown).toList();
                            log.info("[createBattleActors] actor.name = {}, indexOf = {}", actor.getName(), actors.indexOf(actor));
                            return BattleCharacter.builder()
                                    .name(actor.getName())
                                    .member(member)
                                    .currentOrder(actors.indexOf(actor) + 1) // 1부터
                                    .actor(actor)
                                    // 주인공만
                                    .fatalChainMoveId(fatalChainMoveId)
                                    .summonMoveIds(summonMoveIds)
                                    .summonCoolDowns(summonCoolDowns)
                                    .build();
                        }
                ).collect(Collectors.toList()); // toList 타입추론 불가
        battleActorRepository.saveAll(partyMembers);

        // 적 생성
        BattleActor enemy = BattleEnemy.builder()
                .name(enemyActor.getName())
                .member(member)
                .currentOrder(0)
                .actor(enemyActor)
                .build();
        battleActorRepository.save(enemy);

        // 전투 시작
        partyMembers.forEach(calcStatusLogic::initStatus);
        calcStatusLogic.initStatus(enemy);

        battleLogic.startBattle(partyMembers, enemy);

        return null;
    }

    public void exitRoom(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Room room = member.getRoom();
        List<BattleActor> battleActors = member.getBattleActors();

        // 배틀 엑터, 배틀 스테이터스 삭제
        battleActors.forEach(battleActor -> {
            battleStatusRepository.deleteAll(battleActor.getBattleStatuses());
            battleActorRepository.delete(battleActor);
        });
        // 멤버 삭제
        memberRepository.delete(member);

        // 방 삭제
        if (room != null) {
            room.getMembers().remove(member);
            if (room.getMembers().isEmpty()) {
                roomRepository.delete(room);
            }
        }
    }

}
