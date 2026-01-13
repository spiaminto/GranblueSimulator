package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Character;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.battle.repository.ActorRepository;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.battle.repository.StatusRepository;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.User;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final BattleContext battleContext;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BaseActorRepository baseActorRepository;
    private final PartyRepository partyRepository;
    private final ActorRepository actorRepository;
    private final MoveRepository moveRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final StatusRepository statusRepository;
    private final BattleCommandService battleCommandService;

    public boolean updateChargeAttackOn(Long roomId, Long userId, boolean chargeAttackOn) {
        Member member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        member.updateChargeAttackOn(chargeAttackOn);
        return member.isChargeAttackOn();
    }

    @Transactional(timeout = 1)
    public Member enterRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalStateException("없는 방"));
        if (room.getEnterUserCount() >= room.getMaxUserCount()) throw new IllegalStateException("방 최대 입장제한 초과");

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
        room.updateEnterUserCount(room.getEnterUserCount() + 1);

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
        baseActors.forEach(actor -> log.info("[createBattleActors] actor = {}", actor));

        BaseActor enemyBaseActor = baseActorRepository.findById(7L).get(); // CHECK 현재 디아스포라로 고정

        // 파티 생성
        List<Actor> partyMembers = baseActors.stream()
                .map(actor -> {
                            Long fatalChainMoveId = actor.isLeaderCharacter() ?
                                    moveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, actor.getElementType()).getFirst().getId() :
                                    null;
                            member.updateFatalChainMoveId(fatalChainMoveId);
                            List<Move> summons = actor.isLeaderCharacter() ?
                                    moveRepository.findAllById(party.getSummonIds()) :
                                    Collections.emptyList();
                            List<Long> summonMoveIds = summons.stream().map(Move::getId).toList();
                            List<Integer> summonCoolDowns = summons.stream().map(Move::getCoolDown).toList();
                            ActorVisual actorVisual = actor.getDefaultVisual();
                            log.info("[createBattleActors] actor.name = {}, indexOf = {}", actor.getName(), baseActors.indexOf(actor));
                            return Character.builder()
                                    .name(actor.getName())
                                    .member(member)
                                    .currentOrder(baseActors.indexOf(actor) + 1) // 1부터
                                    .baseActor(actor)
                                    .summonMoveIds(summonMoveIds)
                                    .summonCoolDowns(summonCoolDowns)
                                    .actorVisual(actorVisual)
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
                .actorVisual(enemyBaseActor.getDefaultVisual())
                .build();
        enemy.init();
        actorRepository.save(enemy);
        member.getActors().add(enemy);

        // 페이탈 체인 설정
        Actor leaderActor = partyMembers.stream()
                .filter(actor -> actor.getBaseActor().isLeaderCharacter())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("[createBAttleActors] leader 없음, memberId = " + member.getId()));
        List<Move> fatalChainMoves = moveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, leaderActor.getElementType());
        Move fatalChainMove = fatalChainMoves.getFirst();
        member.updateFatalChainMoveId(fatalChainMove.getId());

        Status enemyStatus = Status.builder().build();
        enemyStatus.init(enemy);
        statusRepository.save(enemyStatus);

        partyMembers.forEach(partyMember -> {
            Status status = Status.builder().build();
            status.init(partyMember);
            statusRepository.save(status);
        });

        battleContext.init(member, null);
        battleCommandService.startBattle();

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
