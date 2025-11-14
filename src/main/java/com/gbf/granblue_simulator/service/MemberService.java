package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Party;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.battle.BattleContext;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.Character;
import com.gbf.granblue_simulator.domain.battle.actor.Enemy;
import com.gbf.granblue_simulator.domain.battle.actor.prop.Status;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.repository.*;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.actor.BaseActorRepository;
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

    private final BattleContext battleContext;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BaseActorRepository baseActorRepository;
    private final PartyRepository partyRepository;
    private final ActorRepository actorRepository;
    private final BattleLogic battleLogic;
    private final MoveRepository moveRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final StatusRepository statusRepository;

    public boolean updateChargeAttackOn(Long roomId, Long userId, boolean chargeAttackOn) {
        Member member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        member.updateChargeAttackOn(chargeAttackOn);
        return member.isChargeAttackOn();
    }

    public Member enterRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("없는 방"));

        Member member = Member.builder()
                .user(user)
                .room(room)
                .currentTurn(1)
                .allPotionCount(2)
                .potionCount(2)
                .partyId(user.getPrimaryPartyId())
                .chargeAttackSkip(true)
                .build();

        memberRepository.save(member);
        room.getMembers().add(member);

        createBattleActors(member); // 배틀 액터 생성 및 시작

        log.info("[MemberService] user = {}, room = {}", user, room);

        return member;
    }

    private List<Actor> createBattleActors(Member member) {
        Party party = partyRepository.findById(member.getUser().getPrimaryPartyId()).orElseThrow(() -> new IllegalArgumentException("없는 파티"));

        Map<Long, BaseActor> actorMap = baseActorRepository.findAllById(party.getActorIds()).stream()
                .collect(Collectors.toMap(BaseActor::getId, Function.identity()));
        List<BaseActor> baseActors = party.getActorIds().stream()
                .map(actorMap::get)
                .filter(Objects::nonNull)
                .toList(); // in 쿼리 순서고정
        baseActors.forEach(actor -> log.info("[createBattleActors] actor = {}", actor) );

        BaseActor enemyBaseActor = baseActorRepository.findById(7L).get(); // CHECK 현재 디아스포라로 고정

        // 파티 생성
        List<Actor> partyMembers = baseActors.stream()
                .map(actor -> {
                            Long fatalChainMoveId = actor.isLeaderCharacter() ?
                                    moveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, actor.getElementType()).getFirst().getId() :
                                    null;
                            List<Move> summons = actor.isLeaderCharacter() ?
                                    moveRepository.findAllById(party.getSummonIds()) :
                                    Collections.emptyList();
                            List<Long> summonMoveIds = summons.stream().map(Move::getId).toList();
                            List<Integer> summonCoolDowns = summons.stream().map(Move::getCoolDown).toList();
                            log.info("[createBattleActors] actor.name = {}, indexOf = {}", actor.getName(), baseActors.indexOf(actor));
                            return Character.builder()
                                    .name(actor.getName())
                                    .member(member)
                                    .currentOrder(baseActors.indexOf(actor) + 1) // 1부터
                                    .baseActor(actor)
                                    // 주인공만
                                    .fatalChainMoveId(fatalChainMoveId)
                                    .summonMoveIds(summonMoveIds)
                                    .summonCoolDowns(summonCoolDowns)
                                    .build();
                        }
                ).collect(Collectors.toList()); // toList 타입추론 불가
        partyMembers.forEach(Actor::init);
        actorRepository.saveAll(partyMembers);
        member.getActors().addAll(partyMembers);

        // 적 생성
        Actor enemy = Enemy.builder()
                .name(enemyBaseActor.getName())
                .member(member)
                .currentOrder(0)
                .baseActor(enemyBaseActor)
                .build();
        enemy.init();
        actorRepository.save(enemy);
        member.getActors().add(enemy);

        Status enemyStatus = Status.builder().build();
        enemyStatus.init(enemy);
        statusRepository.save(enemyStatus);

        partyMembers.forEach(partyMember -> {
            Status status = Status.builder().build();
            status.init(partyMember);
            statusRepository.save(status);
        });

        battleContext.init(member, null);
        battleLogic.startBattle();

        return null;
    }

    public void exitRoom(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Room room = member.getRoom();
        List<Actor> actors = member.getActors();

        // 배틀 엑터, 배틀 스테이터스 삭제
        actors.forEach(actor -> {
            statusEffectRepository.deleteAll(actor.getStatusEffects());
            statusRepository.delete(actor.getStatus());
            actorRepository.delete(actor);
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
