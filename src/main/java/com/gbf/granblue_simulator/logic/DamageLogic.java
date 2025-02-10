package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DamageLogic {

    private final Integer baseAttackDamageSoftCap = 40000; // 통상공격 소프트캡
    private final Integer baseAttackDamageHardCap = 60000; // 통상공격 하드캡
    private final Integer baseChargeAttackDamageSoftCap = 370000; // 오의 소프트캡
    private final Integer baseChargeAttackDamageHardCap = 480000; // 오의 하드캡
    private final Integer baseAbilityDamageSoftCap = 40000; // 어빌리티 상한 소프트캡 ( 배율 곱해서 최종계싼 )
    private final Integer baseAbilityDamageHardCap = 60000; // 어빌리티 상한 하드캡 (배율 곱해서 최종계산 )

    private final Double softCapApplyRate = 0.25; // 소프트캡 이후 적용 배율
    private final Double hardCapApplyRate = 0.1; // 하드캡 이후 적용 배율

    // 평타와 오의에 적용되는 대 데미지 제한 (1타)
    private final Double exDamageCapApplyRate = 0.01; // 대데미지 제한
    private final Integer exDamageCap = 660000; // 대데미지 제한

    // 어빌리티에 적용되는 대 데미지 제한 (연타 합산, 1타도 적용)
    private final Double abilityExDamageCapApplyRate = 0.01;
    private final Integer abilityExDamageCap = 1300000; // 어빌리티 최종데미지 하드캡
    private final CommonLogic commonLogic;


    public DamageLogicResult processAttack(BattleActor character, BattleActor enemy) {
        Map<String, List<Integer>> attackDamageMap = getAttackDamage(character, enemy);
        List<Integer> damages = attackDamageMap.get("damages");
        List<Integer> additionalDamages = attackDamageMap.get("additionalDamages");
        Integer enemyHp = enemy.getHp();
        for (Integer damage : damages) {
            enemyHp -= damage;
        }
        for (Integer additionalDamage : additionalDamages) {
            enemyHp -= additionalDamage;
        }
        enemy.setHp(Math.max(enemyHp, 0));

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(damages).additionalDamages(additionalDamages).build();
    }

    protected Map<String, List<Integer>> getAttackDamage(BattleActor character, BattleActor enemy) {
        double charAtk = character.getAtk();
        List<StatusEffect> additionalDamageEffects = commonLogic.getAdditionalDamageEffects(character);

        double enemyDef = enemy.getDef();
        double enemyDamageCut = enemy.getTakenDamageCut();
        double takenDamageFixedDown = enemy.getTakenDamageFixedDown();

        double resultDamage = 0;
        List<Double> resultAdditionalDamages = new ArrayList<>();
        int hitCount = 1;

        // 테스트용 재연산 (임의 값으로 확인하려고) =================================
        charAtk = (double) character.getActor().getBaseAttackPoint() // 1000
                * (1 + character.getWeaponAtkUpRate())
                * (1 + character.getAtkUpRate())
                * (1 + character.getJammedRate())
                * (1 + character.getAtkUpUniqueRate())
                * (1 + character.getStrengthRate());
        enemyDef = enemyDef * (1 + (-0.5)); // 방어 하한까지 깎음
        // ===================================================================
        
        // 데미지연산 0 : atk 부가연산 (크리티컬, )
        double criticalRate = character.getCriticalRate();
        double criticalDamageRate = character.getCriticalDamageRate();
        charAtk = (int) (charAtk * (Math.random() < criticalRate ? 1 + criticalDamageRate : 1));

        log.info("criticalRate = {}", criticalRate);
        log.info("charAtk = {}" , charAtk);

        // 데미지 연산 1 : 적의 방어 연산
        if (enemyDamageCut >= 1.0) {
            resultDamage = 0;
        } else {
            resultDamage = (charAtk * (1 - enemyDamageCut) / enemyDef) - takenDamageFixedDown;
        }

        log.info("resultDamage 1 = {}", resultDamage);

        // 데미지 연산 2 : 데미지 상한 감쇠
        Double damageCapRate = character.getDamageCapRate() > 0 ? character.getDamageCapRate() / 100 : 0;
        double attackDamageSoftCap = (baseAttackDamageSoftCap * (1 + damageCapRate));
        double attackDamageHardCap = (baseAttackDamageHardCap * (1 + damageCapRate));
        if (resultDamage > attackDamageSoftCap) {
           resultDamage = (attackDamageSoftCap + (resultDamage - attackDamageSoftCap) * softCapApplyRate);
           if (resultDamage > attackDamageHardCap) {
               resultDamage = (attackDamageHardCap + (resultDamage - attackDamageHardCap) * hardCapApplyRate);
           }
        }

        log.info("resultDamage 2 = {}", resultDamage);

        // 데미지 연산 2.5 추격
        for (StatusEffect effect : additionalDamageEffects) {
            resultAdditionalDamages.add(resultDamage * effect.getCalcValue());
        }

        // 데미지 연산 3 : 요다메 연산
        Double amplifyDamageRate = character.getAmplifyDamageRate();
        Integer supplementalDamage = character.getSupplementalDamage();
        resultDamage = resultDamage * (1 + amplifyDamageRate) + supplementalDamage;
        for (Double additionalDamage : resultAdditionalDamages) {
            additionalDamage = additionalDamage * (1 + amplifyDamageRate) + supplementalDamage;
        }

        log.info("resultDamage 3 = {}", resultDamage);

        // 데미지 연산 4 : 타격 횟수
        double doubleAttackRate = character.getDoubleAttackUpRate() + character.getDoubleAttackDownRate();
        double tripleAttackRate = character.getTripleAttackUpRate() + character.getTripleAttackDownRate();
        if (Math.random() < tripleAttackRate) {
            hitCount = 3;
        } else if (Math.random() < doubleAttackRate) {
            hitCount = 2;
        }

        // 데미지 연산 5 :  대 데미지 감쇠
        if (resultDamage > exDamageCap) {
            resultDamage = (exDamageCap + (resultDamage - exDamageCap) * exDamageCapApplyRate);
        }

        log.info("hitCount = {}", hitCount);

        // hitCount 만큼의 size 를 가지는 List 를 생성하며 이 List 의 모든 원소의 값은 resultDamage
        int damage = (int) Math.round(resultDamage);
        List<Integer> damages = new ArrayList<>(Collections.nCopies(hitCount, damage));
        List<Integer> additionalDamages = resultAdditionalDamages.stream().map(d -> (int) Math.round(d)).toList();

        log.info("=========damage calc finished");
        damages.forEach(d -> log.info("damage = {}", d));
        return Map.of("damages", damages, "additionalDamages", additionalDamages);
    }
    
// 오의

    /**
     * 오의 데미지를 계산
     * @param character
     * @param enemy
     * @param damageRate 오의는 데미지 배율이 가변이므로 캐릭터 로직단계에서 전달
     * @return
     */
    public DamageLogicResult processChargeAttack(BattleActor character, BattleActor enemy, double damageRate) {
        Integer damage = getChargeAttackDamage(character, enemy, damageRate);
        Integer enemyHp = enemy.getHp();
        enemy.setHp(Math.max(enemyHp - damage, 0));

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(List.of(damage)).build();
    }

    protected Integer getChargeAttackDamage(BattleActor character, BattleActor enemy, double damageRate) {
        double charAtk = character.getAtk();
        Move chargeAttack = character.getActor().getMoves().get(MoveType.CHARGE_ATTACK);

        double enemyDef = enemy.getDef();
        double enemyDamageCut = enemy.getTakenDamageCut();
        double takenDamageFixedDown = enemy.getTakenDamageFixedDown();

        double resultDamage = 0;
        int hitCount = 1;

        // 테스트용 재연산 (임의 값으로 확인하려고) =================================
        charAtk = (double) character.getActor().getBaseAttackPoint() // 1000
                * (1 + damageRate) // 오의 데미지 배율
                * (1 + character.getWeaponAtkUpRate())
                * (1 + character.getAtkUpRate())
                * (1 + character.getJammedRate())
                * (1 + character.getAtkUpUniqueRate())
                * (1 + character.getStrengthRate());
        enemyDef = enemyDef * (1 + (-0.5)); // 방어 하한까지 깎음
        // ===================================================================

        // 데미지연산 0 : atk 부가연산 (크리티컬, )
        double criticalRate = character.getCriticalRate();
        double criticalDamageRate = character.getCriticalDamageRate();
        charAtk = (int) (charAtk * (Math.random() < criticalRate ? 1 + criticalDamageRate : 1));

        log.info("criticalRate = {}", criticalRate);
        log.info("charAtk = {}" , charAtk);

        // 데미지 연산 1 : 적의 방어 연산
        if (enemyDamageCut >= 1.0) {
            resultDamage = 0;
        } else {
            resultDamage = (charAtk * (1 - enemyDamageCut) / enemyDef) - takenDamageFixedDown;
        }

        log.info("resultDamage 1 = {}", resultDamage);

        // 데미지 연산 2 : 데미지 상한 감쇠
        Double damageCapRate = character.getDamageCapRate() > 0 ? character.getDamageCapRate() / 100 : 0;
        double chargeAttackDamageSoftCap = (baseChargeAttackDamageSoftCap * (1 + damageCapRate));
        double chargeAttackDamageHardCap = (baseChargeAttackDamageHardCap * (1 + damageCapRate));
        if (resultDamage > chargeAttackDamageSoftCap) {
            resultDamage = (chargeAttackDamageSoftCap + (resultDamage - chargeAttackDamageSoftCap) * softCapApplyRate);
            if (resultDamage > chargeAttackDamageHardCap) {
                resultDamage = (chargeAttackDamageHardCap + (resultDamage - chargeAttackDamageHardCap) * hardCapApplyRate);
            }
        }

        log.info("resultDamage 2 = {}", resultDamage);

        // 데미지 연산 3 : 요다메 연산
        Double amplifyDamageRate = character.getAmplifyDamageRate();
        Integer supplementalDamage = character.getSupplementalDamage();
        resultDamage = resultDamage * (1 + amplifyDamageRate) + supplementalDamage;

        log.info("resultDamage 3 = {}", resultDamage);

        // 대 데미지 감쇠
        if (resultDamage > exDamageCap) {
            resultDamage = (exDamageCap + (resultDamage - exDamageCap) * exDamageCapApplyRate);
        }

        // 오의는 1히트 고정
        int damage = (int) Math.round(resultDamage);

        log.info("=========damage calc finished");
        return damage;
    }
    
// 어빌리티

    /**
     * 어빌리티 데미지를 계산
     * @param character
     * @param enemy
     * @param damageRate 어빌리티는 데미지 배율이 가변이므로 로직 단계에서 넘겨줌
     * @param hitCount 어빌리티는 히트수가 가변이므로 로직단계에서 넘겨줌
     * @return
     */
    public DamageLogicResult processAbilityAttack(BattleActor character, BattleActor enemy, double damageRate, int hitCount) {
        List<Integer> damages = getAbilityDamage(character, enemy, damageRate, hitCount);
        Integer enemyHp = enemy.getHp();
        for (Integer damage : damages) {
            enemyHp -= damage;
        }
        enemy.setHp(Math.max(enemyHp, 0));

        // 필요한 값 리턴
        return DamageLogicResult.builder().damages(damages).build();
    }

    protected List<Integer> getAbilityDamage(BattleActor character, BattleActor enemy, double damageRate, int hitCount) {
        double charAtk = character.getAtk();

        double enemyDef = enemy.getDef();
        double enemyDamageCut = enemy.getTakenDamageCut();
        double takenDamageFixedDown = enemy.getTakenDamageFixedDown();

        double resultDamage = 0;

        // 테스트용 재연산 (임의 값으로 확인하려고) =================================
        charAtk = (double) character.getActor().getBaseAttackPoint() // 1000
                * (1 + damageRate) // 어빌리티 데미지 배율
                * (1 + character.getWeaponAtkUpRate())
                * (1 + character.getAtkUpRate())
                * (1 + character.getJammedRate())
                * (1 + character.getAtkUpUniqueRate())
                * (1 + character.getStrengthRate());
        enemyDef = enemyDef * (1 + (-0.5)); // 방어 하한까지 깎음
        // ===================================================================

        // 데미지연산 0 : atk 부가연산 (크리티컬, )
        double criticalRate = character.getCriticalRate();
        double criticalDamageRate = character.getCriticalDamageRate();
        charAtk = (int) (charAtk * (Math.random() < criticalRate ? 1 + criticalDamageRate : 1));

        log.info("criticalRate = {}", criticalRate);
        log.info("charAtk = {}" , charAtk);

        // 데미지 연산 1 : 적의 방어 연산
        if (enemyDamageCut >= 1.0) {
            resultDamage = 0;
        } else {
            resultDamage = (charAtk * (1 - enemyDamageCut) / enemyDef) - takenDamageFixedDown;
        }

        log.info("resultDamage 1 = {}", resultDamage);

        // 데미지 연산 2 : 데미지 상한 감쇠
        Double damageCapRate = character.getDamageCapRate() > 0 ? character.getDamageCapRate() / 100 : 0;
        double abilityDamageSoftCap = (baseAbilityDamageSoftCap * (1 + damageCapRate)) * damageRate; // 어빌리티 데미지는 상한이 배율에 따라 다름.
        double abilityDamageHardCap = (baseAbilityDamageHardCap * (1 + damageCapRate)) * damageRate;
        if (resultDamage > abilityDamageSoftCap) {
            resultDamage = (abilityDamageSoftCap + (resultDamage - abilityDamageSoftCap) * softCapApplyRate);
            if (resultDamage > abilityDamageHardCap) {
                resultDamage = (abilityDamageHardCap + (resultDamage - abilityDamageHardCap) * hardCapApplyRate);
            }
        }

        log.info("resultDamage 2 = {}", resultDamage);

        // 데미지 연산 3 : 요다메 연산
        Double amplifyDamageRate = character.getAmplifyDamageRate();
        Integer supplementalDamage = character.getSupplementalDamage();
        resultDamage = resultDamage * (1 + amplifyDamageRate) + supplementalDamage;

        log.info("resultDamage 3 = {}", resultDamage);

        // 데미지 연산 4 : 타수로 합산치 계산 후 대데미지 감쇠 (합산하여 조건연산후 다시 1타 데미지를 나누어 구함)
        if (resultDamage * hitCount > abilityExDamageCap) {
            resultDamage = abilityExDamageCap + (resultDamage * hitCount - abilityExDamageCap) * exDamageCapApplyRate / hitCount;
        }

        log.info("resultDamage4 = {}, hitCount = {}", resultDamage, hitCount);
        int damage = (int) Math.round(resultDamage);
        List<Integer> damages = new ArrayList<>(Collections.nCopies(hitCount, damage));

        log.info("=========damage calc finished");
        return damages;
    }


}
