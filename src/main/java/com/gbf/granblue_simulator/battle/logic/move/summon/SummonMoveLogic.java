package com.gbf.granblue_simulator.battle.logic.move.summon;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Transactional
@Component
@Slf4j
public class SummonMoveLogic extends SummonDefaultLogic {

    DefaultCharacterMoveLogic characterMoveLogic;

    public SummonMoveLogic(CharacterMoveLogicDependencies dependencies, DefaultCharacterMoveLogic characterMoveLogic) {
        super(dependencies);
        this.characterMoveLogic = characterMoveLogic;
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register("summon_2040425000", this::zeus);
        moveLogicRegistry.register("summon_2040100000", this::varuna);
        moveLogicRegistry.register("summon_2040056000", this::lucifel);
        moveLogicRegistry.register("summon_2040413000", this::wamdus);
        moveLogicRegistry.register("summon_2040003000", this::bahamut);
        moveLogicRegistry.register("summon_2040084000", this::titan);
        moveLogicRegistry.register("summon_2040090000", this::hades);
        moveLogicRegistry.register("summon_2040094000", this::agnis);
        moveLogicRegistry.register("summon_2040205000", this::brodia);
        moveLogicRegistry.register("tr_2040205000", this::brodiaTriggerAbility);
        moveLogicRegistry.register("summon_2040255000", this::shingeki);
        moveLogicRegistry.register("summon_2040332000", this::shark);
        moveLogicRegistry.register("summon_2040361000", this::sekitoba);
        moveLogicRegistry.register("summon_2040389000", this::giyu);
        moveLogicRegistry.register("summon_2040408000", this::belzebub);
        moveLogicRegistry.register("summon_2040417000", this::yachima);
        moveLogicRegistry.register("summon_2040421000", this::gozyo);
        moveLogicRegistry.register("summon_2040425000_a", this::yakusokuTsubasa);
        moveLogicRegistry.register("summon_2040425000_b", this::shumatsuTsubasa);
        moveLogicRegistry.register("summon_2040426000", this::allmight);
        moveLogicRegistry.register("summon_2040433000", this::ororo);
        moveLogicRegistry.register("summon_2040448000", this::versusia);
        moveLogicRegistry.register("tr_2040448000", this::versusiaTriggerAbility);
        moveLogicRegistry.register("summon_2040450000", this::kirin);
        moveLogicRegistry.register("summon_2040027000", this::yggdrasil);
    }

    // 제우스: 적에게 8배 데미지 2회, 장악 효과. 아군 전체에 오의 게이지 상승률 증가 효과 [13]
    protected MoveLogicResult zeus(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 바루나: 적에게 12배 데미지, 약화효과 내성 25% 감소(필중), 3턴간 아군 전체에 트리플 어택 확률 50% 증가 [11]
    protected MoveLogicResult varuna(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 루시펠: 적에게 20배 데미지, 아군전체의 체력 7000 회복, 공격력 50% 상승 [7]
    protected MoveLogicResult lucifel(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 와무듀스: 적에게 2배 데미지 x 9회 약화효과 내성 25% 감소(필중), 독효과, 극독레벨상승 [10]
    protected MoveLogicResult wamdus(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 바하무트: 적에게 20배 데미지, 아군 공격력 상승, 소환석 쿨타임 1턴 단축 [9] <6>
    protected MoveLogicResult bahamut(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 티탄: 적에게 12배 데미지, 2턴간 데미지컷 50%, 공격데미지 50,000 상승 [8] - 고정
    protected MoveLogicResult titan(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 하데스: 적에게 12배 데미지, 3턴간 공격력, 방어력, 명중률, 특수기 데미지 다운 [9]
    protected MoveLogicResult hades(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 아그니스: 적에게 7배 * 3회 데미지, 아군전체의 오의게이지 30% 상승 [9]
    protected MoveLogicResult agnis(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 갓 가드 브로디아: 데미지없음, 2턴간 아군 전체의 방어력 1000% 상승, 주인공에게 2턴간 진경나선 효과 부여 [재사용불가]
    // 진경나선효과 중 공격행동후 적에게 6배 데미지 1회, 강화효과 1개 무효화
    protected MoveLogicResult brodia(MoveLogicRequest request) {
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        saveTriggeredMove(List.of(leaderCharacter), "tr_2040205000");

        return defaultSummon(request.getMove());
    }

    // [SELF_STRIKE_END] 진경 나선효과: 공격행동 후 6배 데미지 1회, 강화효과 1개 무효화
    protected MoveLogicResult brodiaTriggerAbility(MoveLogicRequest request) {
        return checkCondition.hasEffect(request.getMove().getActor(), "진경나선")
                .map(effect -> resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(request.getMove()))))
                .orElseGet(() -> {
                    moveService.delete(request.getMove());
                    return resultMapper.emptyResult();
                });
    }

    // 진격의 거인: 적에게 14배 데미지, 아군 전체 오의게이지 30% 상승 [8]
    protected MoveLogicResult shingeki(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 샤---크: 적에게 3.5배 데미지 * 5회, 2턴간 아군 전체 트리플 어택 확률 50% 상승 [8]
    protected MoveLogicResult shark(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 적토마: 적에게 1.5배 X 8회 데미지, 아군 전체 트리플 어택 확률 30% 상승, 추격 효과 [8]
    protected MoveLogicResult sekitoba(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 기유: 데미지 없음, 1턴간 아군전체에 70퍼 데미지 컷, 약화효과 내성 100% 상승 [12] <12>
    protected MoveLogicResult giyu(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 벨제바브: 적에게 13배 데미지, 방어력 감소, 약화효과 내성 감소(필중), 섬광 효과 (피격데미지 1만 상승) [12] <12>
    protected MoveLogicResult belzebub(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 야치마: 데미지 없음, 아군전체의 체력 5000 회복, 어빌리티 쿨타임 1턴 감소 [9] <9>
    protected MoveLogicResult yachima(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 고죠 사토루: 데미지 없음, 1턴간 적에게 데미지 고정(0) 효과 [재사용불가]
    protected MoveLogicResult gozyo(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 000: 약속의 날개 : 5배 * 5회 데미지, 주인공이 즉시 오의사용가능, 오의재사용 [11] <3 + 6>
    protected MoveLogicResult yakusokuTsubasa(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 000: 종말의 날개 : 20배 데미지 1회, 적의 모든 전조값을 1로 감소 [재사용불가] <9>
    protected MoveLogicResult shumatsuTsubasa(MoveLogicRequest request) {
        Enemy enemy = (Enemy) battleContext.getEnemy();

        if (enemy.getOmen() != null) {
            List<Integer> remainValues = enemy.getOmen().getRemainValues();
            List<Integer> modifiedValues = Collections.nCopies(remainValues.size(), 1);
            omenLogic.manualUpdateOmenValue(enemy, modifiedValues);
        }

        return defaultSummon(request.getMove());
    }

    // 올마이트: 16배 데미지 1회, 2턴간 적의 연속공격 확률 100% 감소(개인) [8]
    protected MoveLogicResult allmight(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 오로로자이야: 2배 데미지 * 15회, 2턴간 주인공이 반드시 트리플어택, 분할 데미지(3회) [12] <재사용불가>
    protected MoveLogicResult ororo(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 베르수시아: 3배 데미지 * 10회, 아군전체에 맹난격 효과, 주인공에게 1턴간 타화자재 효과 [12] <12>
    // 타화자재 효과중 공격행동후 8배 화속성 데미지 * 2회, 적에게 피격데미지 증가 효과
    protected MoveLogicResult versusia(MoveLogicRequest request) {
        saveTriggeredMove(List.of(battleContext.getLeaderCharacter()), "tr_2040448000");
        return defaultSummon(request.getMove());
    }

    protected MoveLogicResult versusiaTriggerAbility(MoveLogicRequest request) {
        return checkCondition.hasEffect(request.getMove().getActor(), "타화자재")
                .map(effect -> resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(request.getMove()))))
                .orElseGet(() -> {
                    moveService.delete(request.getMove());
                    return resultMapper.emptyResult();
                });
    }

    // 흑기린: 데미지 없음, 아군전체의 어빌리티 쿨타임 초기화 [재사용불가, 합체소환 불가]
    protected MoveLogicResult kirin(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    protected MoveLogicResult yggdrasil(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }


}
