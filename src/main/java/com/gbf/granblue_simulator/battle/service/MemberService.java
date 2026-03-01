package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Character;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Status;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.repository.*;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import com.gbf.granblue_simulator.metadata.service.BaseCharacterService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    private final BaseMoveRepository baseMoveRepository;
    private final StatusEffectRepository statusEffectRepository;
    private final StatusRepository statusRepository;
    private final MoveRepository moveRepository;
    private final OmenLogic omenLogic;
    private final BaseMoveService baseMoveService;
    private final BaseCharacterService baseCharacterService;

    @Transactional(timeout = 1)
    public Member enterRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalStateException("없는 방"));
        if (room.getEnterUserCount() >= room.getMaxUserCount()) throw new IllegalStateException("방 최대 입장제한 초과");
        if (room.getRoomStatus() == RoomStatus.TUTORIAL && !room.getOwnerId().equals(userId)) throw new IllegalArgumentException("유효하지 않은 방 입장요청 입니다.");

        Member member = Member.builder()
                .user(user)
                .room(room)
                .currentTurn(0)
                .allPotionCount(1)
                .potionCount(2)
                .partyId(user.getPrimaryPartyId())
                .chargeAttackSkip(true)
                .build();
        memberRepository.save(member);
        room.getMembers().add(member);
        room.updateEnterUserCount(room.getEnterUserCount() + 1);

        createActors(member); // 배틀 액터 생성 및 시작

        log.info("[MemberService] user = {}, room = {}", user, room);

        return member;
    }

    private List<Actor> createActors(Member member) {
        Party party = partyRepository.findById(member.getUser().getPrimaryPartyId()).orElseThrow(() -> new IllegalArgumentException("없는 파티"));

        User user = party.getUser();

        List<UserCharacter> userCharacters = party.getCharacterIds().stream()
                .map(characterId -> user.getUserCharacters().get(characterId))
                .toList();

        userCharacters.forEach(actor -> log.info("[createBattleActors] actor = {}", actor));

        BaseActor enemyBaseActor = baseActorRepository.findById(10000L).get(); // CHECK 현재 디아스포라로 고정

        // 1. baseActor -> Actor
        List<Actor> allActors = new ArrayList<>();
        // 파티 생성
        List<Actor> partyMembers = new ArrayList<>();
        Actor leaderActor = null;
        UserCharacter leaderUserCharacter = null;
        for (UserCharacter userCharacter : userCharacters) {
            // 비주얼
            ActorVisual actorVisual = userCharacter.getCustomVisual();
            // 액터
            Actor characterActor = Character.builder()
                    .name(userCharacter.getBaseCharacter().getName())
                    .currentOrder(userCharacters.indexOf(userCharacter) + 1) // 1부터
                    .baseActor(userCharacter.getBaseCharacter())
                    .actorVisual(actorVisual)
                    .build()
                    .mapMember(member);
            characterActor.init();
            partyMembers.add(characterActor);

            log.info("[createBattleActors] userCharacter.name = {}, indexOf = {}", userCharacter.getBaseCharacter().getName(), userCharacters.indexOf(userCharacter));
            if (characterActor.getBaseActor().isLeaderCharacter()) {
                leaderUserCharacter = userCharacter;
                leaderActor = characterActor;
            }
        }
        if (leaderActor == null)
            throw new IllegalArgumentException("[createActors] actor 생성중 문제발생, leaderActor 없음 " + StringUtils.join(partyMembers.stream().map(Actor::getName).toList(), ' '));
        allActors.addAll(partyMembers);
        // 적 생성
        Actor enemy = Enemy.builder()
                .name(enemyBaseActor.getName())
                .currentOrder(0)
                .baseActor(enemyBaseActor)
                .actorVisual(enemyBaseActor.getDefaultVisual())
                .build()
                .mapMember(member);
        enemy.init();
        allActors.add(enemy);
        actorRepository.saveAll(allActors);

        // 2. BaseMove -> Move
        List<Move> toSaveAllMoves = new ArrayList<>();
        // Member 페이탈 체인 설정
        BaseMove fatalChainBaseMove = baseMoveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, leaderActor.getElementType()).getFirst();
        member.updateFatalChainMoveId(fatalChainBaseMove.getId()); // 페이탈 체인 base id 저장
        // Move 매핑
        for (Actor actor : allActors) {

            List<Long> moveIds = actor.getBaseActor().getDefaultMoveIds();
            Map<Long, MoveType> moveTypeById = actor.getBaseActor().getMappedMove().getMoveTypeById();
            if (actor.getBaseActor().isLeaderCharacter()) {
                moveIds = leaderUserCharacter.getBattleMoveIds();
                moveTypeById = leaderUserCharacter.getMoveTypeById(); // ABILITY -> FIRST_ABILITY 등으로 타입 매핑시, 주인공은 custom 활성화된 어빌리티 기준으로 해야함
            }

            List<BaseMove> toSaveBaseMoves = baseMoveService.findAllByIds(moveIds);
            if (!actor.isEnemy()) {
                // 캐릭터: 페이탈 체인 추가
                toSaveBaseMoves.add(fatalChainBaseMove);
                if (actor.getBaseActor().isLeaderCharacter()) {
                    // 주인공: 소환석 추가
                    List<BaseMove> summonBaseMoves = baseMoveService.findAllByIds(party.getSummonIds()).stream()
                            .sorted(Comparator.comparing(baseMove -> party.getSummonIds().indexOf(baseMove.getId())))
                            .toList();
                    for (MoveType summonType : MoveType.SUMMONS) {
                        int index = summonType.getOrder() - 1;
                        if (summonBaseMoves.size() <= index) continue;
                        moveTypeById.put(summonBaseMoves.get(index).getId(), summonType); // moveType 맵에 FIRST_SUMMON, ... 추가
                    }
                    toSaveBaseMoves.addAll(summonBaseMoves); // 소환석
                }
            }

            // BaseMove -> Move
            List<Move> toSaveMoves = new ArrayList<>();
            for (BaseMove toSaveBaseMove : toSaveBaseMoves) {
                toSaveMoves.add(Move.fromBaseMove(toSaveBaseMove)
                        .setActor(actor)
                        .mapType(moveTypeById.get(toSaveBaseMove.getId())));
            }

            actor.addMoves(toSaveMoves); // actor.moves 에 매핑 (성능을 고려해 한번에 매핑)
            toSaveAllMoves.addAll(toSaveMoves);
        }
        moveRepository.saveAll(toSaveAllMoves);

        // 3. Status, StatusDetails, DamageStatusDetails 초기화
        List<Status> statuses = allActors.stream().map(actor -> Status.builder().build().init(actor)).toList();
        statusRepository.saveAll(statuses);

        // 4. battleContext 초기화 및 시작
        battleContext.init(member, null);

        return allActors;
    }

    public void exitRoom(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        if (member.checkedResult()) return; // 결과 확인했을시, 처리없음

        Room room = member.getRoom();
        List<Actor> actors = member.getActors();

        // 배틀 엑터, 배틀 스테이터스 삭제
        actors.forEach(actor -> {
            if (actor.isEnemy()) {
                Enemy enemy = (Enemy) actor;
                if (enemy.getOmen() != null)
                    omenLogic.removeCurrentOmen(enemy);
            }
            statusEffectRepository.deleteAll(actor.getStatusEffects());
            statusRepository.delete(actor.getStatus());
            moveRepository.deleteAll(actor.getMoves());
            actorRepository.delete(actor);
        });

        // memberRepository.delete(member); // 멤버 보존

        // 방 상태 변경
        if (room != null) {
            room.getMembers().remove(member);

            if (room.getMembers().stream().allMatch(roomMember -> roomMember.getActors().isEmpty())) {
                room.changeStatus(RoomStatus.FAILED_EMPTY);
            }
        }
    }

    /**
     * 직접 삭제시 호출
     */
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버 id - " + memberId));

        Room room = member.getRoom();
        List<Actor> actors = member.getActors();

        // 배틀 엑터, 배틀 스테이터스 삭제
        actors.forEach(actor -> {
            if (actor.isEnemy()) {
                Enemy enemy = (Enemy) actor;
                if (enemy.getOmen() != null)
                    omenLogic.removeCurrentOmen(enemy);
            }
            statusEffectRepository.deleteAll(actor.getStatusEffects());
            statusRepository.delete(actor.getStatus());
            moveRepository.deleteAll(actor.getMoves());
            actorRepository.delete(actor);
        });

        // 멤버삭제
         memberRepository.delete(member);
    }

    public Member enterTutorialRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalStateException("없는 방"));
        if (room.getEnterUserCount() >= room.getMaxUserCount()) throw new IllegalStateException("방 최대 입장제한 초과");

        //방 생성
        Member member = Member.builder()
                .user(user)
                .room(room)
                .currentTurn(0)
                .allPotionCount(1)
                .potionCount(2)
                .partyId(user.getPrimaryPartyId())
                .chargeAttackSkip(true)
                .build();
        memberRepository.save(member);
        room.getMembers().add(member);
        room.updateEnterUserCount(room.getEnterUserCount() + 1);

        //캐릭터 생성
        Party party = partyRepository.findById(member.getUser().getPrimaryPartyId()).orElseThrow(() -> new IllegalArgumentException("없는 파티"));
        List<Long> baseCharacterIds = List.of(60000L, 70500L, 70900L, 71300L); // 팔라딘, 야치마, 하제리라, 와무
        List<BaseCharacter> baseCharacters = baseCharacterService.findAllById(baseCharacterIds);
        baseCharacters.sort(Comparator.comparing(BaseCharacter::getId));
        List<Long> summonIds = List.of(40000L); // 제우스

        // 메타데이터
        List<UserCharacter> userCharacters = party.getCharacterIds().stream()
                .map(characterId -> user.getUserCharacters().get(characterId))
                .toList();
        userCharacters.forEach(actor -> log.info("[createBattleActors] actor = {}", actor));
        BaseActor enemyBaseActor = baseActorRepository.findById(10200L).get(); // CHECK 현재 디아스포라로 고정

        // 1. baseActor -> Actor
        List<Actor> allActors = new ArrayList<>();
        // 파티 생성
        List<Actor> partyMembers = new ArrayList<>();
        BaseActor leaderActor = null;
        UserCharacter leaderUserCharacter = null;
        for (BaseCharacter baseCharacter : baseCharacters) {
            // 비주얼
            ActorVisual actorVisual = baseCharacter.getDefaultVisual();
            // 액터
            Actor characterActor = Character.builder()
                    .name(baseCharacter.getName())
                    .currentOrder(baseCharacters.indexOf(baseCharacter) + 1) // 1부터
                    .baseActor(baseCharacter)
                    .actorVisual(actorVisual)
                    .build()
                    .mapMember(member);
            characterActor.init();
            partyMembers.add(characterActor);

            log.info("[enterTutorialRoom] baseCharacters = {}", baseCharacters);
            if (baseCharacter.isLeaderCharacter()) leaderActor = baseCharacter;
        }
        allActors.addAll(partyMembers);

        // 적 생성
        Actor enemy = Enemy.builder()
                .name(enemyBaseActor.getName())
                .currentOrder(0)
                .baseActor(enemyBaseActor)
                .actorVisual(enemyBaseActor.getDefaultVisual())
                .build()
                .mapMember(member);
        enemy.init();
        allActors.add(enemy);
        actorRepository.saveAll(allActors);

        // 2. BaseMove -> Move
        List<Move> toSaveAllMoves = new ArrayList<>();
        // Member 페이탈 체인 설정
        BaseMove fatalChainBaseMove = baseMoveRepository.findByTypeAndElementType(MoveType.FATAL_CHAIN_DEFAULT, leaderActor.getElementType()).getFirst();
        member.updateFatalChainMoveId(fatalChainBaseMove.getId()); // 페이탈 체인 base id 저장
        // Move 매핑
        for (Actor actor : allActors) {

            List<Long> moveIds = actor.getBaseActor().getDefaultMoveIds(); // 주인공도 defaultMoveIds 로 고정
            Map<Long, MoveType> moveTypeById = actor.getBaseActor().getMappedMove().getMoveTypeById();

            List<BaseMove> toSaveBaseMoves = baseMoveService.findAllByIds(moveIds);
            if (!actor.isEnemy()) {
                // 캐릭터: 페이탈 체인 추가
                toSaveBaseMoves.add(fatalChainBaseMove);
                if (actor.getBaseActor().isLeaderCharacter()) {
                    // 주인공: 소환석 추가
                    List<BaseMove> summonBaseMoves = baseMoveService.findAllByIds(summonIds).stream()
                            .sorted(Comparator.comparing(baseMove -> summonIds.indexOf(baseMove.getId())))
                            .toList();
                    for (MoveType summonType : MoveType.SUMMONS) {
                        int index = summonType.getOrder() - 1;
                        if (summonBaseMoves.size() <= index) continue;
                        moveTypeById.put(summonBaseMoves.get(index).getId(), summonType); // moveType 맵에 FIRST_SUMMON, ... 추가
                    }
                    toSaveBaseMoves.addAll(summonBaseMoves); // 소환석
                }
            }

            // BaseMove -> Move
            List<Move> toSaveMoves = new ArrayList<>();
            for (BaseMove toSaveBaseMove : toSaveBaseMoves) {
                toSaveMoves.add(Move.fromBaseMove(toSaveBaseMove)
                        .setActor(actor)
                        .mapType(moveTypeById.get(toSaveBaseMove.getId())));
            }

            actor.addMoves(toSaveMoves); // actor.moves 에 매핑 (성능을 고려해 한번에 매핑)
            toSaveAllMoves.addAll(toSaveMoves);
        }
        moveRepository.saveAll(toSaveAllMoves);

        // 3. Status, StatusDetails, DamageStatusDetails 초기화
         List<Status> statuses = allActors.stream().map(actor -> Status.builder().build().init(actor)).toList();
         statusRepository.saveAll(statuses);

        // 4. battleContext 초기화 및 시작
        // battleContext.init(member, null);

        log.info("[MemberService] user = {}, room = {}", user, room);

        return member;
    }

}
