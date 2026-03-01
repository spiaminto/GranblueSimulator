package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.EnemyInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.controller.dto.room.EnterRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.ExitRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomAddForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomInfo;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseOmenRepository;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
import com.gbf.granblue_simulator.metadata.service.BaseEnemyService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import com.gbf.granblue_simulator.party.controller.dto.BaseEnemyInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartyInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartySummonInfo;
import com.gbf.granblue_simulator.party.controller.dto.UserCharacterInfo;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.controller.UserRegisterForm;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.service.UserService;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
@Transactional
public class IndexController {

    private final RoomService roomService;
    private final MemberRepository memberRepository;
    private final BaseMoveRepository baseMoveRepository;
    private final PartyRepository partyRepository;
    private final MemberService memberService;
    private final UserService userService;
    private final BaseActorService baseActorService;
    private final BaseEnemyService baseEnemyService;
    private final BaseMoveService baseMoveService;
    private final BaseOmenRepository baseOmenRepository;

    @RequestMapping("/")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm, Model model,
                        @AuthenticationPrincipal PrincipalDetails principal) {

        List<Room> rooms = roomService.findActiveRooms();
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .sorted(Comparator.comparing(Room::getCreatedAt))
                .map(room -> {
                            int enemyHpRate = -1;
                            String enemyPortraitSrc = "";
                            String enemyName = "";
                            Optional<Actor> enemyOptional = room.getMembers().stream()
                                    .filter(member -> !member.getActors().isEmpty())
                                    .findFirst()
                                    .flatMap(member -> member.getActors().stream()
                                            .filter(Actor::isEnemy)
                                            .findFirst());
                            if (enemyOptional.isPresent()) {
                                Actor enemy = enemyOptional.get();
                                enemyHpRate = enemy.getHpRateInt();
                                enemyPortraitSrc = enemy.getActorVisual().getPortraitImageSrc();
                                enemyName = enemy.getName();
                            }
                            LocalDateTime roomCreatedAt = room.getCreatedAt();
                            long remainingSeconds = 1800L - Duration.between(roomCreatedAt, LocalDateTime.now()).getSeconds();
                            String remainingTimeString = "00:00";
                            if (remainingSeconds > 0) {
                                long remainingMinutes = remainingSeconds / 60;
                                remainingTimeString = remainingMinutes + ":" + remainingSeconds % 60;
                            }
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
                                    .build();
                        }
                ).toList();
        model.addAttribute("roomInfos", roomInfos);

        if (principal != null) {
            Long primaryPartyId = principal.getUser().getPrimaryPartyId();
            Party party = partyRepository.findById(primaryPartyId).orElseThrow(() -> new IllegalArgumentException("선택된 파티 없음"));
            log.info("partyIds = {}", party.getCharacterIds());
            PartyInfo partyInfo = PartyInfo.builder()
                    .id(party.getId())
                    .name(party.getName())
                    .info(party.getInfoText())
                    .characterInfos(
                            party.getCharacterIds().stream()
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
                    .summonInfos(
                            baseMoveRepository.findAllById(party.getSummonIds()).stream()
                                    .map(move ->
                                            PartySummonInfo.builder()
                                                    .id(move.getId())
                                                    .name(move.getName())
                                                    .info(move.getInfo())
                                                    .cooldown(move.getCoolDown())
                                                    .portraitSrc(move.getDefaultVisual().getPortraitImageSrc())
                                                    .build()
                                    ).toList()
                    )
                    .build();
            model.addAttribute("partyInfo", partyInfo);
        }

        // 회원가입 폼 추가
        if (!model.containsAttribute("userRegisterForm")) {
            model.addAttribute("userRegisterForm", new UserRegisterForm());
        }

        // 방만들기 적 추가
        List<EnemyInfo> baseEnemiesInfo = baseEnemyService.findFirstFormEnemies().stream()
                .map(baseEnemy -> EnemyInfo.builder()
                        .baseId(baseEnemy.getId())
                        .name(baseEnemy.getName())
                        .portraitSrc(baseEnemy.getDefaultVisual().getPortraitImageSrc())
                        .build())
                .toList();
        model.addAttribute("enemyInfos", baseEnemiesInfo);

        return "index";
    }

    @PostMapping("/room")
    public String addRoom(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm,
                          @AuthenticationPrincipal PrincipalDetails principalDetails,
                          RedirectAttributes redirectAttributes) {
        if (principalDetails == null) {
            return "redirect:/";
        }

        // 방 작성
        Room savedRoom = roomService.addRoom(principalDetails.getId(), roomAddForm.getMessage());

        // 멤버 추가
        memberService.enterRoom(savedRoom.getId(), principalDetails.getId());

        return "redirect:/room/" + savedRoom.getId();
    }

    @PostMapping("/room/exit")
    @Transactional
    public String exitRoom(@ModelAttribute ExitRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal) {
        Long memberId = form.getMemberId();
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Long userId = principal.getId();
        if (!member.getUser().getId().equals(userId)) {
            return "redirect:/";
        }

        memberService.exitRoom(memberId);

        return "redirect:/";
    }

    @PostMapping("/room/join")
    @Transactional
    public String joinRoom(@ModelAttribute EnterRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal, Model model) {
        log.info("[joinRoom] enterRoomForm = {}", form);
        if (principal == null || !principal.getId().equals(form.getUserId())) {
            return "redirect:/";
        }
        Long userId = principal.getId();
        Long roomId = form.getRoomId();

        Member member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (member == null) {
            // 멤버 추가 시작
            memberService.enterRoom(roomId, userId);
        }

        return "redirect:/room/" + roomId;
    }

    @GetMapping("/users/me/battle-history")
    @Transactional
    public String history(@AuthenticationPrincipal PrincipalDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/";
        }
        Long userId = principal.getUser().getId();
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저 입니다. userId = " + userId));
        List<Room> rooms = user.getMembers().stream()
                .map(Member::getRoom)
                .filter(Room::isFinished)
                .toList();
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .sorted(Comparator.comparing(Room::getCreatedAt))
                .map(room -> {
                            BaseActor baseEnemy = baseActorService.findById(room.getEnemyBaseId()).orElseThrow(() -> new IllegalArgumentException("적이 없습니다. id = " + room.getEnemyBaseId()));
                            String enemyPortraitSrc = baseEnemy.getDefaultVisual().getPortraitImageSrc();
                            String enemyName = baseEnemy.getName();
                            String endedAt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.KOREA).format(room.getEndedAt());

                            return RoomInfo.builder()
                                    .id(room.getId())
                                    .info(room.getInfo())
                                    .roomStatus(room.getRoomStatus())
                                    .ownerUsername(room.getOwnerUsername())
                                    .memberCount(room.getMembers().size())
                                    .maxMemberCount(room.getMaxUserCount())
                                    .enemyName(enemyName)
                                    .enemyPortraitSrc(enemyPortraitSrc)
                                    .endedAt(endedAt)
                                    .build();
                        }
                ).toList();
        model.addAttribute("roomInfos", roomInfos);
        return "battleHistory";
    }

    @GetMapping("/base-enemies/{enemyId}")
    public String getBaseEnemy(@PathVariable Long enemyId,
                               Model model,
                               @AuthenticationPrincipal PrincipalDetails principalDetails) {
        //CHECK 나중에 수정

        BaseEnemy baseEnemy = baseEnemyService.findById(enemyId).orElseThrow(() -> new IllegalArgumentException("없는 id 입니다. enemyId = " + enemyId));
        Integer currentFormOrder = baseEnemy.getFormOrder();
        Map<Integer, BaseEnemy> baseEnemyMap = baseActorService.findByRootNameEn(baseEnemy.getRootNameEn()).stream()
                .collect(Collectors.toMap(
                        BaseEnemy::getFormOrder,
                        Function.identity()
                ));
        BaseEnemy beforeFormEnemy = baseEnemyMap.get(currentFormOrder + 1);
        BaseEnemy nextFormEnemy = baseEnemyMap.get(currentFormOrder - 1);

        Map<MoveType, List<BaseMove>> baseMoveMap = baseMoveService.findAllByIdsToMap(baseEnemy.getDefaultMoveIds());
        baseMoveMap.forEach((key, value) -> log.info("key = {}, value = {}", key, value.stream().map(BaseMove::getName).reduce((a, b) -> a + ", " + b).orElse("")));

        Map<MoveType, BaseOmen> baseOmens = baseEnemy.getOmens();
        List<MoveInfo> chargeAttackInfos = new ArrayList<>();
        List<BaseEnemyInfo.ChargeAttack> chargeAttacks = baseOmens.values().stream()
                .sorted(Comparator.comparing((BaseOmen baseOmen) -> baseOmen.getOmenType().getDisplayOrder()).thenComparing(BaseOmen::getStandbyType))
                .map(baseOmen -> {
                    MoveInfo chargeAttackInfo = MoveInfo.from(baseMoveMap.get(baseOmen.getStandbyType().getChargeAttackType()).getFirst());
                    chargeAttackInfos.add(chargeAttackInfo);
                    return BaseEnemyInfo.ChargeAttack.builder()
                            .move(chargeAttackInfo)
                            .cancelConds(baseOmen.getOmenCancelConds())
                            .omen(baseOmen)
                            .build();
                })
                .toList();

        BaseEnemyInfo baseEnemyInfo = BaseEnemyInfo.builder()
                .id(baseEnemy.getId())
                .nextFormId(beforeFormEnemy != null ? beforeFormEnemy.getId() : null)
                .beforeFormId(nextFormEnemy != null ? nextFormEnemy.getId() : null)
                .name(baseEnemy.getName())
                .portraitSrc(baseEnemy.getDefaultVisual().getPortraitImageSrc())
                .chargeAttacks(chargeAttacks)
                .chargeAttackInfos(chargeAttackInfos)
                .supportAbilities(baseMoveMap.get(MoveType.SUPPORT_ABILITY).stream().map(MoveInfo::from).toList())
                .elementType(baseEnemy.getElementType().getPresentName())
                .atk(baseEnemy.getAtk())
                .hp(baseEnemy.getMaxHp())
                .def(baseEnemy.getDef())
                .doubleAttackRate((int) (baseEnemy.getDoubleAttackRate() * 100))
                .tripleAttackRate((int) (baseEnemy.getTripleAttackRate() * 100))
                .maxChargeGauge(baseEnemy.getMaxChargeGauge())
                .build();

        model.addAttribute("enemyInfo", baseEnemyInfo);
        return "enemyInfo";
    }


//    @GetMapping("/insert")
//    public String insert() {
//        return "insert/character";
//    }
//
//    @GetMapping("/insert-enemy")
//    public String enemyInsert() {
//        return "insert/enemy";
//    }


}
