package com.gbf.granblue_simulator.party.controller;

import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import com.gbf.granblue_simulator.party.controller.dto.UserEditRequest;
import com.gbf.granblue_simulator.party.controller.dto.PartyCharacterInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartyInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartySummonInfo;
import com.gbf.granblue_simulator.user.User;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import com.gbf.granblue_simulator.battle.service.ActorService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@Controller
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PartyController {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final ActorService actorService;
    private final MoveRepository moveRepository;

    @GetMapping("/party")
    public String party(@RequestParam Long userId, Model model) {
        List<Party> parties = partyRepository.findAll();
        User findUser = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        List<PartyInfo> partyInfos = parties.stream()
                .map(party -> PartyInfo.builder()
                        .id(party.getId())
                        .name(party.getName())
                        .info(party.getInfoText())
                        .characterInfos(
                                actorService.findAllByIdsOrdered(party.getActorIds()).stream()
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
                        .isPrimary(party.getId().equals(findUser.getPrimaryPartyId()))
                        .build()
                ).toList();

        model.addAttribute("partyInfos", partyInfos);
        return "party";
    }

    @GetMapping("/character/{characterId}")
    public String character(@PathVariable Long characterId, Model model) {
        BaseActor baseActor = actorService.findById(characterId).orElseThrow(() -> new IllegalArgumentException("없는 캐릭터"));
        PartyCharacterInfo characterInfo = PartyCharacterInfo.builder()
                .id(characterId)
                .name(baseActor.getName())
                .isMainCharacter(baseActor.isLeaderCharacter())
                .portraitSrc(baseActor.getDefaultVisual().getPortraitImageSrc())
                .chargeAttack(baseActor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilities(baseActor.getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .toList())
                .supportAbilities(baseActor.getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.SUPPORT_ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .toList())
                .elementType(baseActor.getElementType().getPresentName())
                .atk(baseActor.getAtk())
                .hp(baseActor.getMaxHp())
                .def(baseActor.getDef())
                .doubleAttackRate((int) (baseActor.getDoubleAttackRate() * 100))
                .tripleAttackRate((int) (baseActor.getTripleAttackRate() * 100))
                .build();
        model.addAttribute("characterInfo", characterInfo);
        return "characterInfo";
    }

    @PostMapping("/user/edit")
    public String editUser(@ModelAttribute UserEditRequest request,
                           @AuthenticationPrincipal PrincipalDetails principal) {
        Long requestUserId = request.getUserId();
        Long userId = principal.getId();
        // ...userId 검증

        Long primaryPartyId = request.getPrimaryPartyId();
        User findUser = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        findUser.setPrimaryParty(primaryPartyId);

        return "redirect:/party?userId=" + requestUserId;
    }

}
