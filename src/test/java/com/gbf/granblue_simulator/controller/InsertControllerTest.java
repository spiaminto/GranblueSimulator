//package com.gbf.granblue_simulator.controller;
//
//import com.gbf.granblue_simulator.controller.request.insert.character.AbilityRequest;
//import com.gbf.granblue_simulator.controller.request.insert.character.CharacterInsertRequest;
//import com.gbf.granblue_simulator.controller.request.insert.character.ChargeAttackRequest;
//import com.gbf.granblue_simulator.controller.request.insert.character.IdleAndAttackRequest;
//import com.gbf.granblue_simulator.controller.response.insert.InsertResponse;
//import com.gbf.granblue_simulator.domain.entity.Character;
//import com.gbf.granblue_simulator.domain.move.MoveType;
//import com.gbf.granblue_simulator.repository.move.AssetRepository;
//import com.gbf.granblue_simulator.repository.actor.CharacterRepository;
//import com.gbf.granblue_simulator.repository.ImageRepository;
//import jakarta.persistence.EntityManager;
//import jakarta.transaction.Transactional;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.ResponseEntity;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@SpringBootTest
//@ActiveProfiles(value = "local")
//@Slf4j
//class InsertControllerTest {
//
//    @Autowired
//    InsertController insertController;
//
//    @Autowired
//    CharacterRepository characterRepository;
//
//    @Autowired
//    AssetRepository assetRepository;
//
//    @Autowired
//    ImageRepository imageRepository;
//
//    @Autowired
//    EntityManager entityManager;
//
//    @Test
//    @Transactional
//    void insertCharacter() {
//        CharacterInsertRequest request = new CharacterInsertRequest();
//        request.setName("test");
//        request.setImageSrc("/static/...");
//        ResponseEntity<InsertResponse> response = insertController.insertCharacter(request);
//
//        entityManager.clear();
//
//        Character character = characterRepository.findById(response.getBody().getCharacterId()).get();
//        log.info("character = {}", character);
//        log.info("asset = {}", character.getAsset());
//        log.info("image = {}", character.getAsset().getImage());
//
//    }
//
//    @Transactional
//    @Test
//    void insertIdleAndAttack() {
//        // character
//        CharacterInsertRequest characterRequest = new CharacterInsertRequest();
//        characterRequest.setName("test");
//        characterRequest.setImageSrc("/static/...");
//        ResponseEntity<InsertResponse> response = insertController.insertCharacter(characterRequest);
//        Long characterId = response.getBody().getCharacterId();
//
//        log.info("\n====================================");
//
//        // start
//        IdleAndAttackRequest request = new IdleAndAttackRequest();
//        request.setCharacterId(characterId);
//        request.setIdleVideoSrc("idleVideoSrc");
//        request.setSingleAttackVideoSrc("singleAttackVideoSrc");
//        request.setSingleAttackAudioSrc("singleAttackAudioSrc");
//        request.setDoubleAttackVideoSrc("doubleAttackVideoSrc");
//        request.setDoubleAttackAudioSrc("doubleAttackAudioSrc");
//        request.setTripleAttackVideoSrc("tripleAttackVideoSrc");
//        request.setTripleAttackAudioSrc("tripleAttackAudioSrc");
//
//        insertController.insertIdleAndAttack(request);
//
//        entityManager.clear();
//        Character character = characterRepository.findById(characterId).get();
//        character.getMoves().forEach((id, move) -> {
//            log.info("move = {}", move);
//            log.info("asset = {}", move.getAsset());
//            log.info("video = {}", move.getAsset().getEffectVideo());
//            log.info("audio = {}", move.getAsset().getAudios());
//        });
//
//    }
//
//    @Transactional
//    @Test
//    void insertChargeAttack() {
//        // character
//        CharacterInsertRequest characterRequest = new CharacterInsertRequest();
//        characterRequest.setName("test");
//        characterRequest.setImageSrc("/static/...");
//        ResponseEntity<InsertResponse> response = insertController.insertCharacter(characterRequest);
//        Long characterId = response.getBody().getCharacterId();
//
//        log.info("\n====================================");
//
//        ChargeAttackRequest request = new ChargeAttackRequest();
//        request.setCharacterId(characterId);
//        request.setName("오의이름");
//        request.setInfo("오의정보");
//        request.setChanged(false);
//        request.setVideoSrc("videoSrc");
//        request.setDamageRate(4.5);
//        request.setAudioSrcAndDelays("/audio1, 30 \n /audio2, 100");
//
//        // status 1
//        ChargeAttackRequest.ChargeAttackStatus status1 = new ChargeAttackRequest.ChargeAttackStatus();
//        status1.setImageSrc("image1");
//        status1.setEffectText("이펙트텍스트1");
//        status1.setStatusText("스테이터스텍스트1");
//        status1.setType("BUFF");
//        status1.setDuration(3);
//        status1.setStatusEffect("ATK_UP,30 \n ATK_UP,20");
//
//        // status2
//        ChargeAttackRequest.ChargeAttackStatus status2 = new ChargeAttackRequest.ChargeAttackStatus();
//        status2.setImageSrc("image2");
//        status2.setEffectText("이펙트텍스트2");
//        status2.setStatusText("스테이터스텍스트2");
//        status2.setType("DEBUFF");
//        status2.setDuration(5);
//        status2.setStatusEffect("ATK_DOWN, 30 \n ATK_DOWN, 20");
//
//        // status 3
//        ChargeAttackRequest.ChargeAttackStatus status3 = new ChargeAttackRequest.ChargeAttackStatus();
//        status3.setImageSrc("image3");
//        status3.setEffectText("이펙트텍스트3");
//        status3.setStatusText("스테이터스텍스트3");
//        status3.setType("BUFF");
//        status3.setDuration(10);
//        status3.setStatusEffect("ATK_UP, 30 \n ATK_UP, 20");
//        request.setStatuses(List.of(status1, status2, status3));
//
//        response = insertController.insertChargeAttack(request);
//
//        entityManager.clear();
//
//        characterRepository.findById(characterId).get().getMoves().forEach((id, move) -> {
//            log.info("move = {}", move);
//            log.info("asset = {}", move.getAsset());
//            log.info("video = {}", move.getAsset().getEffectVideo());
//            log.info("audio = {}", move.getAsset().getAudios());
//            move.getStatuses().forEach(status -> {
//                log.info("status = {}", status);
//                log.info("status asset = {}", status.getImage());
//                status.getStatusEffect().forEach(effect -> {
//                    log.info("effect = {}", effect);
//                });
//            });
//        });
//    }
//
//    @Transactional
//    @Test
//    void insertAbility() {
//        // character
//        CharacterInsertRequest characterRequest = new CharacterInsertRequest();
//        characterRequest.setName("test");
//        characterRequest.setImageSrc("/static/...");
//        ResponseEntity<InsertResponse> response = insertController.insertCharacter(characterRequest);
//        Long characterId = response.getBody().getCharacterId();
//
//        log.info("\n====================================");
//
//        AbilityRequest request = new AbilityRequest();
//        request = request;
//        request.setCharacterId(characterId);
//        request.setName("어빌리티 이름");
//        request.setType("FIRST_ABILITY");
//        request.setInfo("어빌리티 정보");
//        request.setCoolDown(4);
//        request.setDuration(3);
//        request.setAbilityEffectCount(2);
//        request.setAbilityHitCount(1);
//        request.setEffectVideoSrc("어빌리티 비디오 src");
//        request.setMotionVideoSrc("어빌리티 모션 비디오 src");
//        request.setDamageRate("4.0");
//        request.setAudioSrcAndDelays("/audio1, 30 \n /audio2, 100");
//
//        // status 1
//        AbilityRequest.AbilityStatus status1 = new AbilityRequest.AbilityStatus();
//        status1.setImageSrc("image1");
//        status1.setEffectText("이펙트텍스트1");
//        status1.setStatusText("스테이터스텍스트1");
//        status1.setType("BUFF");
//        status1.setDuration(3);
//        status1.setStatusEffect("ATK_UP,30 \n ATK_UP,20");
//
//        // status2
//        AbilityRequest.AbilityStatus status2 = new AbilityRequest.AbilityStatus();
//        status2.setImageSrc("image2");
//        status2.setEffectText("이펙트텍스트2");
//        status2.setStatusText("스테이터스텍스트2");
//        status2.setType("DEBUFF");
//        status2.setDuration(5);
//        status2.setStatusEffect("ATK_DOWN, 30 \n ATK_DOWN, 20");
//
//        // status 3
//        AbilityRequest.AbilityStatus status3 = new AbilityRequest.AbilityStatus();
//        status3.setImageSrc("image3");
//        status3.setEffectText("이펙트텍스트3");
//        status3.setStatusText("스테이터스텍스트3");
//        status3.setType("BUFF");
//        status3.setDuration(10);
//        status3.setStatusEffect("ATK_UP, 30 \n ATK_UP, 20");
//        request.setStatuses(List.of(status1, status2, status3));
//
//        response = insertController.insertAbility(request);
//        entityManager.clear();
//        characterRepository.findById(characterId).get().getMoves().forEach((id, move) -> {
//            log.info("move = {}", move);
//            log.info("asset = {}", move.getAsset());
//            log.info("video = {}", move.getAsset().getEffectVideo());
//            log.info("motion = {}", move.getAsset().getMotionVideo());
//            log.info("audio = {}", move.getAsset().getAudios());
//            move.getStatuses().forEach(status -> {
//                log.info("status = {}", status);
//                log.info("status asset = {}", status.getImage());
//                status.getStatusEffect().forEach(effect -> {
//                    log.info("effect = {}", effect);
//                });
//            });
//        });
//    }
//}