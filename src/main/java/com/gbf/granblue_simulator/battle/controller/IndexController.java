package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import com.gbf.granblue_simulator.battle.controller.dto.room.EnterRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.ExitRoomForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomAddForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartyCharacterInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartyInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartySummonInfo;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IndexController {

    private final RoomService roomService;
    private final MemberRepository memberRepository;
    private final MoveRepository moveRepository;
    private final PartyRepository partyRepository;
    private final BaseActorRepository baseActorRepository;
    private final MemberService memberService;

    @RequestMapping("/")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm, Model model,
                        @AuthenticationPrincipal PrincipalDetails principal) {
        List<Room> rooms = roomService.findAll();
        rooms.forEach(room -> {
            log.info("[index] room = {}", room);
            room.getMembers().forEach(member -> {
                log.info("[index] member = {}", member);
                member.getActors().forEach(actor -> {
                    log.info("[index] actor = {}", actor);
                });
            });
        });
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .map(room -> RoomInfo.builder()
                        .id(room.getId())
                        .info(room.getInfo())
                        .ownerUsername(room.getOwnerUsername())
                        .memberCount(room.getMembers().size())
                        .enemyHpRate(room.getMembers().getFirst().getActors().stream()
                                .filter(Actor::isEnemy)
                                .findFirst().orElseThrow(() -> new IllegalArgumentException("적이 존재하지 않음"))
                                .getHpRate())
                        .build()
                ).toList();
        model.addAttribute("roomInfos", roomInfos);

        if (principal != null) {
            Long primaryPartyId = principal.getUser().getPrimaryPartyId();
            Party party = partyRepository.findById(primaryPartyId).orElseThrow(() -> new IllegalArgumentException("선택된 파티 없음"));
            PartyInfo partyInfo = PartyInfo.builder()
                    .id(party.getId())
                    .name(party.getName())
                    .info(party.getInfoText())
                    .characterInfos(
                            baseActorRepository.findAllById(party.getActorIds()).stream()
                                    .map(actor ->
                                            PartyCharacterInfo.builder()
                                                    .id(actor.getId())
                                                    .name(actor.getName())
                                                    .portraitSrc(actor.getDefaultVisual().getPortraitImageSrc())
                                                    .chargeAttack(actor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                                                    .abilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.ABILITY).toList())
                                                    .supportAbilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.SUPPORT_ABILITY).toList())
                                                    .build()
                                    ).toList()
                    )
                    .summonInfos(
                            moveRepository.findAllById(party.getSummonIds()).stream()
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

        return "index";
    }

    @PostMapping("/room")
    public String addRoom(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm,
                          @AuthenticationPrincipal PrincipalDetails principalDetails) {
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
