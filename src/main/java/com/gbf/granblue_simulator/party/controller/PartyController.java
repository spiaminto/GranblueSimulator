package com.gbf.granblue_simulator.party.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import com.gbf.granblue_simulator.party.controller.dto.*;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.domain.UserCharacterMove;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import com.gbf.granblue_simulator.user.service.UserCharacterService;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PartyController {

    private final UserRepository userRepository;
    private final BaseMoveRepository baseMoveRepository;
    private final BaseMoveService baseMoveService;
    private final UserCharacterService userCharacterService;
    private final PartyRepository partyRepository;

    @GetMapping("/users/{userId}/parties")
    public String party(@PathVariable Long userId, Model model) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        List<Party> parties = user.getParties();
        List<PartyInfo> partyInfos = parties.stream()
                .sorted(Comparator.comparing(Party::getId))
                .map(party -> PartyInfo.builder()
                        .id(party.getId())
                        .name(party.getName())
                        .info(party.getInfoText())
                        .characterInfos(
                                party.getCharacterIds().stream()
                                        .map(characterId -> {
                                            UserCharacter userCharacter = user.getUserCharacters().get(characterId);
                                            BaseCharacter baseCharacter = userCharacter.getBaseCharacter();
                                            return UserCharacterInfo.builder()
                                                    .id(userCharacter.getId())
                                                    .name(baseCharacter.getName())
                                                    .portraitSrc(userCharacter.getCustomVisual().getPortraitImageSrc())
                                                    .build();
                                        }).toList()
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
                        .isPrimary(party.getId().equals(user.getPrimaryPartyId()))
                        .build()
                ).toList();

        model.addAttribute("partyInfos", partyInfos);
        return "party";
    }

    @GetMapping("/users/{userId}/characters/{characterId}")
    public String getUserCharacter(@PathVariable Long userId,
                                   @PathVariable Long characterId,
                                   @RequestParam(required = false) Long partyId,
                                   @RequestParam(required = false) Long fromCharacterId,
                                   Model model,
                                   @AuthenticationPrincipal PrincipalDetails principalDetails) {
        //CHECK 나중에 수정
        userId = principalDetails != null ? principalDetails.getId() : 1L;

        UserCharacter userCharacter = userCharacterService.findById(characterId);
        BaseCharacter baseCharacter = userCharacter.getBaseCharacter();
        Map<MoveType, List<BaseMove>> baseMoveMap = baseMoveService.findAllByIdsToMap(baseCharacter.getAllMoveIds());

        List<BaseMove> baseAbilities = baseMoveMap.get(MoveType.ABILITY);
        List<BaseMove> baseSupportAbilities = baseMoveMap.get(MoveType.SUPPORT_ABILITY);
        List<UserCharacterMoveStatus> abilityStatuses = new ArrayList<>();
        List<UserCharacterMoveStatus> supportAbilityStatuses = new ArrayList<>();
        if (userCharacter.isLeaderCharacter()) {
            baseAbilities.forEach(baseMove -> {
                UserCharacterMove userCharacterMove = userCharacter.getAbilities().get(baseMove.getId());
                if (userCharacterMove == null) abilityStatuses.add(UserCharacterMoveStatus.UNAVAILABLE);
                else abilityStatuses.add(userCharacterMove.getStatus());
            });
            baseSupportAbilities.forEach(baseMove -> {
                UserCharacterMove userCharacterMove = userCharacter.getAbilities().get(baseMove.getId());
                if (userCharacterMove == null) supportAbilityStatuses.add(UserCharacterMoveStatus.UNAVAILABLE);
                else supportAbilityStatuses.add(userCharacterMove.getStatus());
            });
        }

        UserCharacterInfo characterInfo = UserCharacterInfo.builder()
                .id(characterId)
                .name(baseCharacter.getName())
                .isLeaderCharacter(userCharacter.isLeaderCharacter())
                .portraitSrc(baseCharacter.getDefaultVisual().getPortraitImageSrc())
                .chargeAttack(MoveInfo.from(baseMoveMap.get(MoveType.CHARGE_ATTACK).getFirst()))
                .abilities(baseAbilities.stream().map(MoveInfo::from).toList())
                .abilityStatuses(abilityStatuses)
                .supportAbilities(baseMoveMap.get(MoveType.SUPPORT_ABILITY).stream().map(MoveInfo::from).toList())
                .supportAbilityStatuses(supportAbilityStatuses)
                .elementType(baseCharacter.getElementType().getPresentName())
                .atk(baseCharacter.getAtk())
                .hp(baseCharacter.getMaxHp())
                .def(baseCharacter.getDef())
                .doubleAttackRate((int) (baseCharacter.getDoubleAttackRate() * 100))
                .tripleAttackRate((int) (baseCharacter.getTripleAttackRate() * 100))
                .build();

        model.addAttribute("characterInfo", characterInfo);
        model.addAttribute("userId", userId);
        model.addAttribute("partyId", partyId);
        if (fromCharacterId == null) {
            model.addAttribute("fromCharacterId", characterId);
        } else {
            model.addAttribute("fromCharacterId", fromCharacterId);
            model.addAttribute("toCharacterId", characterId);
        }
        return "characterInfo";
    }

    @GetMapping("/users/{userId}/characters")
    public String getUserCharacters(@PathVariable Long userId,
                                    @RequestParam Long partyId,
                                    @RequestParam(required = false) Long fromCharacterId,
                                    @RequestParam(required = false) Boolean isLeaderCharacter,
                                    @AuthenticationPrincipal PrincipalDetails principalDetails,
                                    Model model) {
        log.info("[getUserCharacters] userId = {}, partyId = {}, fromCharacterId = {}, isLeaderCharacter = {}", userId, partyId, fromCharacterId, isLeaderCharacter);
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        boolean isLeader = isLeaderCharacter != null && isLeaderCharacter;

        List<UserCharacterInfo> userCharacterInfos = user.getUserCharacters().values().stream()
                .filter(userCharacter -> userCharacter.isLeaderCharacter() == isLeader)
                .map(userCharacter -> UserCharacterInfo.builder()
                        .id(userCharacter.getId())
                        .name(userCharacter.getBaseCharacter().getName())
                        .iconSrc(userCharacter.getCustomVisual().getCharacterIconImageSrc())
                        .build()
                ).toList();

        model.addAttribute("characterInfos", userCharacterInfos);
        model.addAttribute("partyId", partyId);
        model.addAttribute("fromCharacterId", fromCharacterId);

        String viewName = isLeader ? "leaderCharacterList" : "characterList";
        return viewName;
    }

    @PatchMapping("/users/{userId}/parties/{partyId}")
    public String postUserParties(@PathVariable Long userId,
                                  @PathVariable Long partyId,
                                  @RequestParam Long fromCharacterId,
                                  @RequestParam Long toCharacterId,
                                  @AuthenticationPrincipal PrincipalDetails principalDetails) {
        if (fromCharacterId.equals(toCharacterId)) return "redirect:/users/" + userId + "/parties"; // 같은캐릭 일단 무시
        Party party = partyRepository.findById(partyId).orElseThrow(() -> new IllegalArgumentException("없는 파티"));

        User user = party.getUser();
        if (!user.getId().equals(userId)) throw new IllegalArgumentException("잘못된 접근");

        List<Long> characterIds = party.getCharacterIds();
        int fromIndex = characterIds.indexOf(fromCharacterId);
        int toIndex = characterIds.indexOf(toCharacterId);

        if (fromIndex == -1) throw new IllegalArgumentException("파티에 없는 캐릭터");

        if (toIndex >= 0) {
            // 스왑
            characterIds.set(fromIndex, toCharacterId);
            characterIds.set(toIndex, fromCharacterId);
        } else {
            // 교체
            characterIds.set(fromIndex, toCharacterId);
        }

        log.info("[postUserParties] swapped, from={} (index={}), to={} (index={})", fromCharacterId, fromIndex, toCharacterId, toIndex);

        partyRepository.save(party);
        return "redirect:/users/" + userId + "/parties";
    }

    @PatchMapping("/users/{userId}")
    public String patchUser(@PathVariable Long userId,
                            @ModelAttribute UserEditRequest request,
                            @AuthenticationPrincipal PrincipalDetails principal) {
        log.info("[patchUser] request = {}", request);
        // ...userId 검증
        Long primaryPartyId = request.getPartyId();
        User findUser = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저"));
        findUser.updatePrimaryPartyId(primaryPartyId);

        return "redirect:/users/" + userId + "/parties";
    }

    @PatchMapping("/users/{userId}/characters/{characterId}")
    @Transactional
    public String updateAbilityStatus(
            @PathVariable Long userId,
            @PathVariable Long characterId,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @ModelAttribute UpdateUserCharacterForm form,
            RedirectAttributes redirectAttributes) {

        if (principalDetails != null && !principalDetails.getUser().getId().equals(userId))
            throw new IllegalArgumentException("잘못된 접근");
        UserCharacter userCharacter = userCharacterService.findById(characterId);
        if (!userCharacter.getUser().getId().equals(userId)) throw new IllegalArgumentException("잘못된 캐릭터 접근");

        Map<Long, UserCharacterMove> abilities = userCharacter.getAbilities();
        UserCharacterMoveStatus targetStatus = form.getTargetStatus();
        if (abilities == null) throw new IllegalStateException("매핑된 어빌리티 없음");

        UserCharacterMove userCharacterMove = abilities.get(form.getMoveId());
        if (userCharacterMove == null) throw new IllegalStateException("어빌리티가 매핑된 어빌리티에 없음");
        if (userCharacterMove.getStatus() == UserCharacterMoveStatus.DEFAULT) throw new IllegalArgumentException("기본 어빌리티는 변경할수 없습니다.");

        // 어빌리티를 IN_USE로 변경 시 개수 체크
        if (targetStatus == UserCharacterMoveStatus.IN_USE && userCharacterMove.getMoveType() != MoveType.SUPPORT_ABILITY) {
            long inUseCount = abilities.values().stream()
                    .filter(a -> a.getMoveType() == MoveType.ABILITY && a.getStatus() == UserCharacterMoveStatus.IN_USE)
                    .count();
            if (inUseCount >= 2) {
                throw new IllegalStateException("어빌리티는 최대 3개까지 사용 가능합니다.");
            }
        }

        if (userCharacterMove.getStatus() == UserCharacterMoveStatus.UNAVAILABLE && targetStatus == UserCharacterMoveStatus.AVAILABLE) {
            // 구매하기
            log.info("구매하기 시작");

        } else {
            // 상태변경
            userCharacterMove.setStatus(targetStatus);
        }

        return "redirect:/users/" + userId + "/characters/" + characterId;
    }

}
