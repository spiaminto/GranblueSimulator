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
import com.gbf.granblue_simulator.metadata.domain.Raid;
import com.gbf.granblue_simulator.metadata.domain.RaidType;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.repository.RaidRepository;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
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

import java.util.*;

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
    private final StatusService statusService;
    private final BaseActorService baseActorService;
    private final RaidRepository raidRepository;

    public Optional<Member> findById(Long memberId) {
        return memberRepository.findById(memberId);
    }

    public Optional<Member> findByRoomIdAndUserId(Long roomId, Long userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId);
    }

    public Optional<Member> findFreshWithActorsById(Long memberId) {
        return memberRepository.findWithActorsById(memberId);
    }

    @Transactional(timeout = 2)
    public Member enterRoom(Long roomId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("없는 유저"));
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalStateException("없는 방"));
        if (room.getEnterUserCount() >= room.getMaxUserCount()) throw new IllegalStateException("방 최대 입장제한 초과");
        if (room.getRoomStatus() == RoomStatus.TUTORIAL && !room.getOwnerId().equals(userId))
            throw new IllegalArgumentException("유효하지 않은 방 입장요청 입니다.");

        final Long hexachromaticRaidId = 10300L;
        final Long diasporaRaidId = 10000L;
        if (hexachromaticRaidId.equals(room.getRaid().getId())
                && user.getMembers().stream().noneMatch(member ->
                diasporaRaidId.equals(member.getRoom().getRaid().getId()) && member.getRoom().getRoomStatus() == RoomStatus.CLEARED)) {
            throw new IllegalStateException("디아스포라HL 레이드를 1번이상 클리어 해야 도전가능합니다.");
        }

        Member member = Member.builder()
                .user(user)
                .room(room)
                .currentTurn(0)
                .allPotionCount(2)
                .potionCount(2)
                .elixirCount(2)
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

        List<UserCharacter> userCharacters = party.getUserCharacterIds().stream()
                .map(characterId -> user.getUserCharacters().get(characterId))
                .toList();
        userCharacters.forEach(userCharacter -> log.debug("[createBattleActors] userCharacter = {}", userCharacter));

        Long currentEnemyBaseId = member.getRoom().getCurrentEnemyBaseId();
        BaseActor enemyBaseActor = baseActorService.findById(currentEnemyBaseId).orElseThrow(() -> new IllegalArgumentException("적 정보가 없음 baseEnemy.id = " + currentEnemyBaseId));

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

            log.debug("[createBattleActors] userCharacter.name = {}, indexOf = {}", userCharacter.getBaseCharacter().getName(), userCharacters.indexOf(userCharacter));
            if (characterActor.getBaseActor().isLeaderCharacter()) {
                leaderUserCharacter = userCharacter;
                leaderActor = characterActor;
                member.updateLeaderCharacterBaseId(leaderUserCharacter.getBaseCharacter().getId());
            }
        }
        if (leaderActor == null)
            throw new IllegalStateException("전투 캐릭터 생성 오류, 주인공 없음 캐릭터: " + StringUtils.join(partyMembers.stream().map(Actor::getName).toList(), ' '));
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
        // 소환석 결정
        List<BaseMove> allBaseSummons = baseMoveService.findAllByMoveType(MoveType.SUMMON);
        BaseMove partyMainSummon = null;
        List<BaseMove> candidateSummons = new ArrayList<>();

        List<Long> partyMainSummonIds = user.getAvailableParty().stream().flatMap(userParty -> userParty.getSummonIds().stream()).toList(); // 각 파티의 기본 소환석 제외
        for (BaseMove baseSummon : allBaseSummons) {
            // 메인 소환석
            if (Objects.equals(baseSummon.getId(), party.getSummonIds().getFirst())) partyMainSummon = baseSummon;
            // 랜덤 소환석
            if (!partyMainSummonIds.contains(baseSummon.getId())) candidateSummons.add(baseSummon);
        }
        Collections.shuffle(candidateSummons);
        if (partyMainSummon == null)
            throw new IllegalStateException("소환석 생성 오류: 파티의 메인 소환석을 불러올 수 없습니다. id = " + party.getSummonIds());
        candidateSummons.addFirst(partyMainSummon);
        List<BaseMove> selectedSummons = candidateSummons.stream().limit(6).toList();

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
                    for (MoveType summonType : MoveType.SUMMONS) {
                        int index = summonType.getOrder() - 1;
                        if (selectedSummons.size() <= index) continue;
                        moveTypeById.put(selectedSummons.get(index).getId(), summonType); // moveType 맵에 FIRST_SUMMON, ... 추가
                    }
                    toSaveBaseMoves.addAll(selectedSummons); // 소환석
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
        List<Status> statuses = allActors.stream().map(statusService::init).toList();
        statusRepository.saveAll(statuses);

        return allActors;
    }

    public void exitRoom(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        if (member.checkedResult()) return; // 결과 확인했을시, 처리없음
        Room room = member.getRoom();
        if (room.getRoomStatus() == RoomStatus.TUTORIAL) {
            member.updateCheckedResult(true); // 튜토리얼은 나간 즉시 확인처리
        }

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
        room.getMembers().remove(member);

        if (room.getMembers().stream().allMatch(roomMember -> roomMember.getActors().isEmpty())) {
            room.changeStatus(RoomStatus.FAILED_EMPTY);
        }

    }

    /**
     * 아직 확인 안 한 결과를 확인. 클리어 포인트 추가
     */
    public record RoomResultCheckResult(boolean isClearPointIncreased, String message) {
    }

    public RoomResultCheckResult checkRoomResult(Member member) {
        User user = member.getUser();
        Room room = member.getRoom();
        int myHonor = member.getHonor();
        boolean isClearPointIncreased = false;
        String message = "없음";

        if (room.getRaid().getId().equals(10300L)) {
            isClearPointIncreased = myHonor >= 200000 && room.getRoomStatus() == RoomStatus.CLEARED;
            message = isClearPointIncreased ? "클리어 포인트를 획득했습니다. 현재포인트: " + user.getClearPoint()
                    : "실패 또는 공헌도 20만 미만으로 클리어 포인트를 획득하지 못했습니다. 현재포인트: " + user.getClearPoint();
        }

        if (member.checkedResult()) return new RoomResultCheckResult(isClearPointIncreased, message); // 이미 확인했으면 먼저 리턴

        member.updateCheckedResult(true);

        if (isClearPointIncreased) {
            user.updateClearPoint(user.getClearPoint() + 1);
            message = "클리어 포인트를 획득했습니다. 현재포인트: " + user.getClearPoint();
        }

        return new RoomResultCheckResult(isClearPointIncreased, message);
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
                .allPotionCount(2)
                .potionCount(2)
                .elixirCount(2)
                .partyId(user.getPrimaryPartyId())
                .chargeAttackSkip(true)
                .build();
        memberRepository.save(member);
        room.getMembers().add(member);
        room.updateEnterUserCount(room.getEnterUserCount() + 1);

        //캐릭터 생성
        Party party = partyRepository.findById(member.getUser().getPrimaryPartyId()).orElseThrow(() -> new IllegalStateException("없는 파티"));
        List<Long> baseCharacterIds = List.of(60100L, 71300L, 70900L, 71000L); // 검호, 와무, 하제, 실비아
        List<BaseCharacter> baseCharacters = baseCharacterService.findAllById(baseCharacterIds);
        baseCharacters.sort(Comparator.comparing(baseCharacter -> baseCharacterIds.indexOf(baseCharacter.getId())));
        List<Long> summonIds = List.of(40100L, 40200L); // 바루나, 루시

        // 메타데이터
        List<UserCharacter> userCharacters = party.getUserCharacterIds().stream()
                .map(characterId -> user.getUserCharacters().get(characterId))
                .toList();
        userCharacters.forEach(actor -> log.debug("[createBattleActors] actor = {}", actor));
        Raid tutorialRaid = raidRepository.findAllByType(RaidType.TUTORIAL).stream()
                .findFirst().orElseThrow(() -> new IllegalStateException("튜토리얼 레이드 생성 오류 (레이드 없음)"));
        BaseActor enemyBaseActor = baseActorRepository.findById(tutorialRaid.getFirstBaseEnemyId()).orElseThrow(() -> new IllegalStateException("튜토리얼 레이드 생성 오류 (적 없음)"));

        // 1. baseActor -> Actor
        List<Actor> allActors = new ArrayList<>();
        // 파티 생성
        List<Actor> partyMembers = new ArrayList<>();
        BaseActor leaderActor = null;
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

            log.debug("[enterTutorialRoom] baseCharacters = {}", baseCharacters);
            if (baseCharacter.isLeaderCharacter()) {
                leaderActor = baseCharacter;
                member.updateLeaderCharacterBaseId(leaderActor.getId());
            }
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
        List<Status> statuses = allActors.stream().map(statusService::init).toList();
        statusRepository.saveAll(statuses);

        // 튜토리얼용 합체소환 지정
        Move secondSummon = toSaveAllMoves.stream().filter(move -> move.getType() == MoveType.SECOND_SUMMON).findFirst().orElseThrow(() -> new IllegalStateException("튜토리얼 합체 소환석 지정 오류"));
        room.updateUnionSummonId(secondSummon.getId());

        log.info("[MemberService] user = {}, room = {}", user, room);

        return member;
    }

}
