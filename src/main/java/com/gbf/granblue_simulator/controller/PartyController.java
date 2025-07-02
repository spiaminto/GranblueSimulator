package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.auth.PrincipalDetails;
import com.gbf.granblue_simulator.controller.request.UserEditRequest;
import com.gbf.granblue_simulator.controller.response.info.party.PartyCharacterInfo;
import com.gbf.granblue_simulator.controller.response.info.party.PartyInfo;
import com.gbf.granblue_simulator.controller.response.info.party.PartySummonInfo;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.Party;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.repository.PartyRepository;
import com.gbf.granblue_simulator.repository.UserRepository;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PartyController {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final ActorRepository actorRepository;
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
                                actorRepository.findAllById(party.getActorIds()).stream()
                                        .map(actor ->
                                                PartyCharacterInfo.builder()
                                                        .id(actor.getId())
                                                        .name(actor.getName())
                                                        .portraitSrc(actor.getBattlePortraitSrc())
                                                        .chargeAttack(actor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                                                        .abilities(actor.getMoves().values().stream().filter(move ->
                                                                move.getType().getParentType() == MoveType.ABILITY).toList())
                                                        .supportAbilities(actor.getMoves().values().stream().filter(move ->
                                                                move.getType().getParentType() == MoveType.SUPPORT_ABILITY).toList())
                                                        .build()
                                        ).toList()
                        )
                        .summonInfos(
                                moveRepository.findAllById(party.getSummonMoveIds()).stream()
                                        .map(move ->
                                                PartySummonInfo.builder()
                                                        .id(move.getId())
                                                        .name(move.getName())
                                                        .info(move.getInfo())
                                                        .cooldown(move.getCoolDown())
                                                        .portraitSrc(move.getAsset().getIconImageSrc())
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
        Actor actor = actorRepository.findById(characterId).orElseThrow(() -> new IllegalArgumentException("없는 캐릭터"));
        PartyCharacterInfo characterInfo = PartyCharacterInfo.builder()
                .id(characterId)
                .name(actor.getName())
                .isMainCharacter(actor.isMainCharacter())
                .portraitSrc(actor.getBattlePortraitSrc())
                .chargeAttack(actor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilities(actor.getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .toList())
                .supportAbilities(actor.getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.SUPPORT_ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .toList())
                .portraitSrc(actor.getBattlePortraitSrc())
                .elementType(actor.getElementType().getPresentName())
                .atk(actor.getBaseAttackPoint())
                .hp(actor.getBaseHitPoint())
                .def(actor.getBaseDefencePoint())
                .doubleAttackRate((int) (actor.getBaseDoubleAttackRate() * 100))
                .tripleAttackRate((int) (actor.getBaseTripleAttackRate() * 100))
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
