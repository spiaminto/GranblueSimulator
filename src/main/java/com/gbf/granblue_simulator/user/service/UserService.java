package com.gbf.granblue_simulator.user.service;

import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.MappedMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseCharacterRepository;
import com.gbf.granblue_simulator.metadata.service.BaseCharacterService;
import com.gbf.granblue_simulator.party.domain.BaseParty;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.BasePartyRepository;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.controller.UserRegisterForm;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.domain.UserCharacterMove;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.expression.Maps;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserCharacterService userCharacterService;
    private final PartyRepository partyRepository;
    private final BaseCharacterService baseCharacterService;
    private final MemberService memberService;
    private final BasePartyRepository basePartyRepository;

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByLoginId(String username) {
        return userRepository.findByLoginId(username);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public boolean existsByLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public void createUserCharactersFromInsert(BaseCharacter insertedCharacter) {
        List<UserCharacter> userCharacters = userRepository.findAll().stream()
                .map(user -> {
                    Map<Long, UserCharacterMove> userActorMoveMap = new HashMap<>();
                    if (insertedCharacter.isLeaderCharacter()) {
                        // 주인공의 경우 어빌리티 상태필드 추가
                        MappedMove mappedMove = insertedCharacter.getMappedMove();
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
                            .baseCharacter(insertedCharacter)
                            .customVisual(insertedCharacter.getDefaultVisual())
                            .abilities(userActorMoveMap)
                            .build();
                })
                .toList();
        userCharacterService.saveAll(userCharacters);
    }

    public void registerUser(UserRegisterForm form) {
        //유저생성
        User user = User.builder()
                .username(form.getUsername())
                .loginId(form.getLoginId())
                .role("ROLE_USER")
                .clearPoint(0)
                .password(passwordEncoder.encode(form.getPassword()))
                .build();
        userRepository.save(user);

        //모든 BaseCharacter 에 대해 UserCharacter 생성
        List<BaseCharacter> baseCharacters = baseCharacterService.findAvailableCharacters();
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

        //기본 파티 생성
        Map<Long, UserCharacter> userCharacterMap = savedUserCharacters.stream()
                .collect(Collectors.toMap(
                        userCharacter -> userCharacter.getBaseCharacter().getId(),
                        Function.identity()
                ));
        List<Party> defaultParties = basePartyRepository.findAll().stream()
                .sorted(Comparator.comparing(BaseParty::getId))
                .map(baseParty ->
                Party.builder()
                        .user(user)
                        .name(baseParty.getName())
                        .infoText(baseParty.getInfo())
                        .userCharacterIds(baseParty.getBaseCharacterIds().stream().map(baseCharacterId -> userCharacterMap.get(baseCharacterId).getId()).toList())
                        .summonIds(baseParty.getSummonIds())
                        .baseParty(baseParty)
                        .build()
        ).toList();

        List<Party> savedDefaultParties = partyRepository.saveAll(defaultParties);

        user.updatePrimaryPartyId(savedDefaultParties.getFirst().getId());
    }

    @Builder
    @Getter
    private static class DefaultParty {
        private String name;
        private String infoText;
        private List<Long> baseCharacterIds;
        private Long mainSummonId;
    }

    private static final Map<Integer, DefaultParty> defaultPartyMap = Map.of(
            1, DefaultParty.builder()
                    .name("수속성 검호 파티")
                    .infoText("")
                    .baseCharacterIds(List.of(60100L, 71300L, 70900L, 71000L))
                    .mainSummonId(40100L)
                    .build(),
            2, DefaultParty.builder()
                    .name("수속성 파이터 파티")
                    .infoText("")
                    .baseCharacterIds(List.of(60500L, 70500L, 70800L, 70600L))
                    .mainSummonId(40300L)
                    .build(),
            3, DefaultParty.builder()
                    .name("토속성 라이징포스 파티")
                    .infoText("")
                    .baseCharacterIds(List.of(71400L, 71500L, 71600L, 71700L))
                    .mainSummonId(42200L)
                    .build(),
            4, DefaultParty.builder()
                    .name("토속성 버서커 파티")
                    .infoText("")
                    .baseCharacterIds(List.of(60300L, 71900L, 71800L, 72000L))
                    .mainSummonId(40500L)
                    .build()
    );

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("유저가 없습니다."));

        List<UserCharacter> userCharacters = user.getUserCharacters().values().stream().toList();
        userCharacterService.deleteAll(userCharacters);

        partyRepository.deleteAll(user.getAllParty());

        user.getMembers().forEach(member -> memberService.deleteMember(member.getId()));

        userRepository.delete(user);
    }

}
