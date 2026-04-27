package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.room.RoomAddForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomInfo;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.MappedMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.service.BaseCharacterService;
import com.gbf.granblue_simulator.party.controller.dto.PartyInfo;
import com.gbf.granblue_simulator.party.controller.dto.UserCharacterInfo;
import com.gbf.granblue_simulator.party.domain.BaseParty;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.BasePartyRepository;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.domain.UserCharacterMove;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import com.gbf.granblue_simulator.user.service.UserCharacterService;
import com.gbf.granblue_simulator.user.service.UserService;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final RoomService roomService;
    private final MemberService memberService;

    private final UserCharacterService userCharacterService;
    private final UserService userService;
    private final BaseCharacterService baseCharacterService;

    private final BasePartyRepository basePartyRepository;
    private final PartyRepository partyRepository;

    @RequestMapping("/admin")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm, Model model,
                        @AuthenticationPrincipal PrincipalDetails principal) {
        List<Room> rooms = roomService.findAll();
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .map(room -> {
                    int enemyHpRate = -1;
                    String enemyPortraitSrc = room.getRaid().getRaidImageSrc();
                    String enemyName = room.getRaid().getName();
                    Optional<Actor> enemyOptional = room.getMembers().stream()
                            .filter(member -> !member.getActors().isEmpty())
                            .findFirst()
                            .flatMap(member -> member.getActors().stream()
                                    .filter(Actor::isEnemy)
                                    .findFirst());
                    if (enemyOptional.isPresent()) {
                        Actor enemy = enemyOptional.get();
                        enemyHpRate = enemy.getHpRateInt();
                    }
                    LocalDateTime roomCreatedAt = room.getCreatedAt();
                    long remainingSeconds = 2700L - Duration.between(roomCreatedAt, LocalDateTime.now()).getSeconds();
                    String remainingTimeString = "00:00";
                    if (remainingSeconds > 0) {
                        long remainingMinutes = remainingSeconds / 60;
                        remainingTimeString = remainingMinutes + ":" + remainingSeconds % 60;
                    }
                    Boolean isMember = room.getMembers().stream().anyMatch(member -> principal.getUser().getId().equals(member.getUser().getId()));

                    return RoomInfo.builder()
                            .id(room.getId())
                            .info(room.getInfo())
                            .roomStatus(room.getRoomStatus())
                            .ownerUsername(room.getOwnerUsername())
                            .memberCount(room.getMembers().size())
                            .maxMemberCount(room.getMaxUserCount())
                            .enemyHpRate(enemyHpRate)
                            .enemyPortraitSrc(enemyPortraitSrc)
                            .enemyName(enemyName)
                            .remainingTime(remainingTimeString)
                            .roomStatus(room.getRoomStatus())
                            .isMember(isMember)
                            .build();
                })
                .sorted(Comparator.comparingInt(roomInfo -> roomInfo.getIsMember() ? 0 : 1)) // 참전중인 방 우선
                .toList();
        model.addAttribute("roomInfos", roomInfos);

        if (principal != null) {
            Long primaryPartyId = principal.getUser().getPrimaryPartyId();
            Party party = partyRepository.findById(primaryPartyId).orElseThrow(() -> new IllegalArgumentException("선택된 파티 없음"));
            PartyInfo partyInfo = PartyInfo.builder()
                    .id(party.getId())
                    .name(party.getName())
                    .info(party.getInfoText())
                    .characterInfos(
                            party.getUserCharacterIds().stream()
                                    .map(characterId -> {
                                        UserCharacter character = party.getUser().getUserCharacters().get(characterId);
                                        return UserCharacterInfo.builder()
                                                .id(character.getId())
                                                .name(character.getBaseCharacter().getName())
                                                .portraitSrc(character.getCustomVisual().getPortraitImageSrc())
                                                .build();
                                    })
                                    .toList()
                    )
                    .build();
            model.addAttribute("partyInfo", partyInfo);
        }

        return "admin/adminIndex";
    }

    @DeleteMapping("/api/admin/room/{roomId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteRoom(@PathVariable Long roomId) {
        log.info("[deleteRoom] roomId = {}", roomId);
        Room room = roomService.findById(roomId).orElseThrow(() -> new IllegalArgumentException("없는 방"));
        List<Member> members = new ArrayList<>(room.getMembers());
        for (Member member : members) {
            memberService.deleteMember(member.getId());
        }

        roomService.deleteRoom(roomId);

        return ResponseEntity.ok(Map.of("isSuccess", "true"));
    }

    public record InsertPartyForm(String baseCharacterIdsString, Long mainSummonId, String partyName,
                                  String partyInfo) {
    }

    @PostMapping("/admin/insert-party")
    public String postInsertParty(@ModelAttribute InsertPartyForm insertPartyForm) {
        log.info("[postInsertParty] insertPartyForm = {}", insertPartyForm);

        String baseCharacterIdsString = insertPartyForm.baseCharacterIdsString();
        List<Long> baseCharacterIds = Arrays.stream(baseCharacterIdsString.replaceAll("\\s+", "").split(",")).map(Long::valueOf).toList();

        List<User> allUsers = userService.findAll();
        List<BaseCharacter> baseCharacters = baseCharacterService.findAllById(baseCharacterIds).stream().
                sorted(Comparator.comparing(baseCharacter -> baseCharacterIds.indexOf(baseCharacter.getId())))
                .toList();
        if (baseCharacters.size() != baseCharacterIds.size())
            throw new IllegalStateException("파티 삽입 오류, 캐릭터 수 불일치 characters / ids = " + baseCharacters.size() + " / " + baseCharacterIds.size());


        for (User user : allUsers) {
            if (user.getAllParty().stream().anyMatch(party -> party.getName().equals(insertPartyForm.partyName())))
                continue;

            List<UserCharacter> userCharacters = baseCharacters.stream().map(baseCharacter -> {
                Map<Long, UserCharacterMove> userActorMoveMap = new HashMap<>();
                if (baseCharacter.isLeaderCharacter()) {
                    // 주인공의 경우 어빌리티 상태필드 추가
                    MappedMove mappedMove = baseCharacter.getMappedMove();
                    List<Long> abilityIds = mappedMove.getAbilityIds(); // 기본매핑
                    List<Long> allAbilityIds = mappedMove.getAllAbilityIds(); // 전체매핑
                    List<Long> supportAbilityIds = mappedMove.getSupportAbilityIds();
                    List<Long> allSupportAbilityIds = mappedMove.getAllSupportAbilityIds();

                    allAbilityIds.forEach(abilityId -> {
                        UserCharacterMoveStatus userCharacterMoveStatus = UserCharacterMoveStatus.IN_USE;
                        if (!abilityIds.contains(abilityId)) {
                            userCharacterMoveStatus = UserCharacterMoveStatus.UNAVAILABLE;
                        }
                        if (abilityIds.indexOf(abilityId) == 0) {
                            userCharacterMoveStatus = UserCharacterMoveStatus.DEFAULT;
                        }
                        userActorMoveMap.put(abilityId, UserCharacterMove.builder()
                                .moveType(MoveType.ABILITY)
                                .status(userCharacterMoveStatus).build());
                    });

                    allSupportAbilityIds.forEach(abilityId -> {
                        UserCharacterMoveStatus userCharacterMoveStatus = UserCharacterMoveStatus.IN_USE;
                        if (!supportAbilityIds.contains(abilityId)) {
                            userCharacterMoveStatus = UserCharacterMoveStatus.UNAVAILABLE;
                        }
                        userActorMoveMap.put(abilityId, UserCharacterMove.builder()
                                .moveType(MoveType.SUPPORT_ABILITY)
                                .status(userCharacterMoveStatus).build());
                    });
                }

                return UserCharacter.builder()
                        .user(user)
                        .baseCharacter(baseCharacter)
                        .customVisual(baseCharacter.getDefaultVisual())
                        .abilities(userActorMoveMap)
                        .build();
            }).toList();

            List<UserCharacter> savedUserCharacters = userCharacterService.saveAll(userCharacters);
            List<Long> userCharacterIds = savedUserCharacters.stream().map(UserCharacter::getId).toList();

            // 파티생성
            Party party = Party.builder()
                    .user(user)
                    .name(insertPartyForm.partyName())
                    .infoText(insertPartyForm.partyInfo())
                    .userCharacterIds(userCharacterIds)
                    .summonIds(List.of(insertPartyForm.mainSummonId()))
                    .build();
            partyRepository.save(party);
        }

        return "redirect:/admin";
    }

    @GetMapping("/admin/reset-party")
    public String getResetParty() {
        List<User> allUsers = userService.findAll();

        // 파티 초기화
        List<Party> parties = allUsers.stream().flatMap(user -> user.getAllParty().stream()).toList();
        partyRepository.deleteAll(parties);
        List<UserCharacter> beforeUserCharacters = allUsers.stream().flatMap(user -> user.getUserCharacters().values().stream()).toList();
        userCharacterService.deleteAll(beforeUserCharacters);
        allUsers.forEach(user -> {
            user.getAllParty().clear();
            user.getUserCharacters().clear();
        });

        // 재생성
        List<BaseParty> baseParties = basePartyRepository.findAll().stream().sorted(Comparator.comparing(BaseParty::getId)).toList();
        for (User user : allUsers) {
            int index = 0;
            for (BaseParty baseParty : baseParties) {
                // UserCharacter 다시 저장
                List<BaseCharacter> baseCharacters = baseCharacterService.findAllById(baseParty.getBaseCharacterIds()).stream().sorted(Comparator.comparing(baseCharacter -> baseParty.getBaseCharacterIds().indexOf(baseCharacter.getId()))).toList();
                List<UserCharacter> userCharacters = baseCharacters.stream().map(baseCharacter -> {
                    Map<Long, UserCharacterMove> userActorMoveMap = new HashMap<>();
                    if (baseCharacter.isLeaderCharacter()) {
                        // 주인공의 경우 어빌리티 상태필드 추가
                        MappedMove mappedMove = baseCharacter.getMappedMove();
                        List<Long> abilityIds = mappedMove.getAbilityIds(); // 기본매핑
                        List<Long> allAbilityIds = mappedMove.getAllAbilityIds(); // 전체매핑
                        List<Long> supportAbilityIds = mappedMove.getSupportAbilityIds();
                        List<Long> allSupportAbilityIds = mappedMove.getAllSupportAbilityIds();

                        allAbilityIds.forEach(abilityId -> {
                            UserCharacterMoveStatus userCharacterMoveStatus = UserCharacterMoveStatus.IN_USE;
                            if (!abilityIds.contains(abilityId)) {
                                userCharacterMoveStatus = UserCharacterMoveStatus.UNAVAILABLE;
                            }
                            if (abilityIds.indexOf(abilityId) == 0) {
                                userCharacterMoveStatus = UserCharacterMoveStatus.DEFAULT;
                            }
                            userActorMoveMap.put(abilityId, UserCharacterMove.builder()
                                    .moveType(MoveType.ABILITY)
                                    .status(userCharacterMoveStatus).build());
                        });

                        allSupportAbilityIds.forEach(abilityId -> {
                            UserCharacterMoveStatus userCharacterMoveStatus = UserCharacterMoveStatus.IN_USE;
                            if (!supportAbilityIds.contains(abilityId)) {
                                userCharacterMoveStatus = UserCharacterMoveStatus.UNAVAILABLE;
                            }
                            userActorMoveMap.put(abilityId, UserCharacterMove.builder()
                                    .moveType(MoveType.SUPPORT_ABILITY)
                                    .status(userCharacterMoveStatus).build());
                        });
                    }

                    return UserCharacter.builder()
                            .user(user)
                            .baseCharacter(baseCharacter)
                            .customVisual(baseCharacter.getDefaultVisual())
                            .abilities(userActorMoveMap)
                            .build();
                }).toList();

                List<UserCharacter> savedUserCharacters = userCharacterService.saveAll(userCharacters);

                Map<Long, UserCharacter> userCharacterMap = savedUserCharacters.stream()
                        .collect(Collectors.toMap(
                                userCharacter -> userCharacter.getBaseCharacter().getId(),
                                Function.identity()
                        ));
                Party party = Party.builder()
                        .user(user)
                        .name(baseParty.getName())
                        .infoText(baseParty.getInfo())
                        .userCharacterIds(baseParty.getBaseCharacterIds().stream()
                                .map(baseCharacterId -> userCharacterMap.get(baseCharacterId).getId()).toList())
                        .summonIds(List.of(baseParty.getSummonIds().getFirst()))
                        .baseParty(baseParty)
                        .build();
                partyRepository.save(party); // primaryPartyId 필요해서 즉시저장

                if (index == 0) {
                    user.updatePrimaryPartyId(party.getId());
                }
                index++;
            }
        }

        return "redirect:/admin";
    }

    @GetMapping("/admin/insert")
    public String insert() {
        return "insert/character";
    }

    @GetMapping("/admin/insert-enemy")
    public String enemyInsert() {
        return "insert/enemy";
    }


}
