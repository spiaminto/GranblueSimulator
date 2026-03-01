package com.gbf.granblue_simulator.user.service;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.domain.actor.MappedMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseCharacterRepository;
import com.gbf.granblue_simulator.metadata.service.BaseCharacterService;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.user.controller.UserRegisterForm;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.domain.UserCharacterMove;
import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByLoginId(String username) {
        return userRepository.findByLoginId(username);
    }

    public boolean existsByLoginId(String loginId) {
        return userRepository.existsByLoginId(loginId);
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
        // 일단 아래는 고정
        List<UserCharacter> firstPartyUserCharacters = Stream.of(60000L, 70500L, 70600L, 70700L).map(userCharacterMap::get).toList();
        List<UserCharacter> secondPartyUserCharacters = Stream.of(60100L, 70800L, 70900L, 71000L).map(userCharacterMap::get).toList();
        List<UserCharacter> thirdPartyUserCharacters = Stream.of(60000L, 71100L, 71200L, 71300L).map(userCharacterMap::get).toList();
        // summon 은 user 커스터마이즈 없이 메타데이터 그대로
        List<Long> summonIds = List.of(40000L, 40100L, 40200L, 40201L);

        List<Party> defaultParties = List.of(
                Party.builder()
                        .user(user)
                        .name("파티 1")
                        .infoText("파티 1번")
                        .characterIds(firstPartyUserCharacters.stream().map(UserCharacter::getId).toList())
                        .summonIds(summonIds)
                        .build(),
                Party.builder()
                        .user(user)
                        .name("파티 2")
                        .infoText("파티 2번")
                        .characterIds(secondPartyUserCharacters.stream().map(UserCharacter::getId).toList())
                        .summonIds(summonIds)
                        .build(),
                Party.builder()
                        .user(user)
                        .name("파티 3")
                        .infoText("파티 3번")
                        .characterIds(thirdPartyUserCharacters.stream().map(UserCharacter::getId).toList())
                        .summonIds(summonIds)
                        .build()
        );
        List<Party> savedDefaultParties = partyRepository.saveAll(defaultParties);

        user.updatePrimaryPartyId(savedDefaultParties.getFirst().getId());
    }

    public void deleteUser(Long userId) {

    }

}
