package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.RaidInfo;
import com.gbf.granblue_simulator.battle.controller.dto.room.EnterRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.ExitRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomAddForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomInfo;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import com.gbf.granblue_simulator.metadata.domain.Raid;
import com.gbf.granblue_simulator.metadata.domain.RaidType;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.repository.RaidRepository;
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
import com.gbf.granblue_simulator.web.mail.GmailSender;
import jakarta.servlet.http.HttpSession;
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
    private final BaseMoveRepository baseMoveRepository;
    private final PartyRepository partyRepository;
    private final MemberService memberService;
    private final UserService userService;
    private final BaseEnemyService baseEnemyService;
    private final BaseMoveService baseMoveService;
    private final RaidRepository raidRepository;

    private final GmailSender gmailSender;

    @RequestMapping("/")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm,
                        Model model,
                        HttpSession session,
                        @AuthenticationPrincipal PrincipalDetails principal) {
        final boolean isLoggedIn = principal != null;

        // 에러 핸들 메시지
        String error = (String) session.getAttribute("errorMessage");
        if (error != null) {
            model.addAttribute("errorMessage", error);
            session.removeAttribute("errorMessage"); // 1회성으로만 사용할것
        }

        List<Room> rooms = roomService.findActiveRooms();
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .sorted(Comparator.comparing(Room::getCreatedAt))
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
                    Boolean isMember = isLoggedIn && room.getMembers().stream().anyMatch(member -> principal.getUser().getId().equals(member.getUser().getId()));

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
                            .isMember(isMember)
                            .build();
                })
                .sorted(Comparator.comparingInt(roomInfo -> roomInfo.getIsMember() ? 0 : 1)) // 참전중인 방 우선
                .toList();
        model.addAttribute("roomInfos", roomInfos);

        if (principal != null) {
            User user = userService.findById(principal.getUser().getId()).orElseThrow(() -> new IllegalStateException("없는 유저"));
            Long primaryPartyId = user.getPrimaryPartyId();
            Party party = partyRepository.findById(primaryPartyId).orElseThrow(() -> new IllegalArgumentException("선택된 파티 없음"));
            log.debug("[index] partyIds = {}", party.getUserCharacterIds());
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
                    .summonInfos(
                            baseMoveRepository.findAllById(party.getSummonIds()).stream()
                                    .map(move -> PartySummonInfo.builder()
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

            user.getMembers().stream().filter(member -> member.getRoom().isFinished() && !member.checkedResult()).findAny().ifPresent(member -> {
                model.addAttribute("resultChecked", false);
            });
        }

        // 회원가입 폼 추가
        if (!model.containsAttribute("userRegisterForm")) {
            model.addAttribute("userRegisterForm", new UserRegisterForm());
        }

        // 방만들기 레이드 추가
        List<Raid> raids = raidRepository.findAllByType(RaidType.MULTI);
        List<RaidInfo> raidInfos = raids.stream()
                .map(raid -> RaidInfo.builder()
                        .id(raid.getId())
                        .type(raid.getType())
                        .name(raid.getName())
                        .info(raid.getInfo())
                        .raidImageSrc(raid.getRaidImageSrc())
                        .build())
                .toList();
        model.addAttribute("raidInfos", raidInfos);

        return "index";
    }

    @PostMapping("/room")
    public String addRoom(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm,
                          @AuthenticationPrincipal PrincipalDetails principalDetails,
                          RedirectAttributes redirectAttributes) {
        if (principalDetails == null) {
            redirectAttributes.addFlashAttribute("alertMessage", "유저 오류입니다.");
            return "redirect:/";
        }

        // 방 작성
        Room savedRoom = roomService.addRoom(principalDetails.getId(), roomAddForm.getRaidId(), roomAddForm.getMessage());

        // 멤버 추가
        memberService.enterRoom(savedRoom.getId(), principalDetails.getId());

        return "redirect:/room/" + savedRoom.getId();
    }

    @PostMapping("/room/exit")
    @Transactional
    public String exitRoom(@ModelAttribute ExitRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal) {
        Long memberId = form.getMemberId();
        Member member = memberService.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
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
                           @AuthenticationPrincipal PrincipalDetails principal,
                           RedirectAttributes redirectAttributes) {
        log.info("[joinRoom] enterRoomForm = {}", form);
        if (principal == null || !principal.getId().equals(form.getUserId())) {
            redirectAttributes.addFlashAttribute("alertMessage", "유저 오류입니다.");
            return "redirect:/";
        }
        Long userId = principal.getId();
        Long roomId = form.getRoomId();

        Member member = memberService.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (member == null) {
            User user = userService.findById(userId).orElseThrow(() -> new IllegalStateException("잘못된 유저입니다."));
            long enteringRoomCount = user.getMembers().stream()
                    .filter(userMember -> userMember.getRoom().getRoomStatus() == RoomStatus.ACTIVE)
                    .count();
            if (enteringRoomCount >= 2) {
                redirectAttributes.addFlashAttribute("alertMessage", "참전 가능한 방의 갯수는 최대 2개 입니다.");
                return "redirect:/";
            }
            // 멤버 추가 시작
            memberService.enterRoom(roomId, userId);
        }

        return "redirect:/room/" + roomId;
    }

    @GetMapping("/users/me/battle-history")
    @Transactional
    public String getHistory(@AuthenticationPrincipal PrincipalDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/";
        }
        Long userId = principal.getUser().getId();
        User user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저 입니다. userId = " + userId));

        List<RoomInfo> roomInfos = user.getMembers().stream()
                .filter(member ->
                        (member.getRoom().isFinished() && !member.getRoom().getMembers().isEmpty())
                                && !(member.checkedResult() && member.getRoom().getRaid().getType() == RaidType.TUTORIAL) // 튜토리얼, 결과 체크했다면 제외
                )
                .sorted(Comparator.comparing((Member member) -> member.getRoom().getEndedAt()).reversed())
                .map(member -> {
                    Room room = member.getRoom();
                    String enemyPortraitSrc = room.getRaid().getRaidImageSrc();
                    String enemyName = room.getRaid().getName();
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
                            .resultChecked(member.checkedResult())
                            .build();
                })
                .toList();

        model.addAttribute("roomInfos", roomInfos);
        return "battleHistory";
    }

    @GetMapping("/base-enemies/{enemyId}")
    @Transactional
    public String getBaseEnemy(@PathVariable Long enemyId,
                               Model model,
                               @AuthenticationPrincipal PrincipalDetails principalDetails) {

        BaseEnemy baseEnemy = baseEnemyService.findById(enemyId).orElseThrow(() -> new IllegalArgumentException("없는 id 입니다. enemyId = " + enemyId));
        Integer currentFormOrder = baseEnemy.getFormOrder();
        Map<Integer, BaseEnemy> baseEnemyMap = baseEnemyService.findByRootNameEn(baseEnemy.getRootNameEn()).stream()
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
                    MoveInfo chargeAttackInfo = MoveInfo.fromWithModifier(baseMoveMap.get(baseOmen.getStandbyType().getChargeAttackType()).getFirst());
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
                .supportAbilities(baseMoveMap.get(MoveType.SUPPORT_ABILITY).stream().map(MoveInfo::fromWithModifier).toList())
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

    @GetMapping("/base-summons/{summonId}")
    public String getBaseSummon(@PathVariable Long summonId,
                                Model model) {
        BaseMove summon = baseMoveService.findById(summonId).orElseThrow(() -> new IllegalStateException("없는 소환석입니다."));
        MoveInfo summonInfo = MoveInfo.from(summon);

        model.addAttribute("summonInfo", summonInfo);
        return "summonInfo";
    }

    @PostMapping("/inquiry")
    public String postInquiry(@RequestParam String text,
                              @AuthenticationPrincipal PrincipalDetails principal,
                              RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/";

        log.info("[postInquiry] text = {}, from = {}", text, principal.getUsername());
        gmailSender.sendInquiry("username = " + principal.getUsername() + "\n" + text);

        redirectAttributes.addFlashAttribute("alertMessage", "문의를 보냈습니다.");
        return "redirect:/";
    }


}
