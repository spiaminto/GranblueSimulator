package com.gbf.granblue_simulator.metadata.domain.omen;

import lombok.Getter;

@Getter
public enum OmenCancelType {

    DAMAGE(), // 데미지 량
    CHARGE_ATTACK_DAMAGE, // 오의 데미지  
    ABILITY_DAMAGE, // 어빌리티 데미지
    
    HIT_COUNT(), // 히트수
    TWO_HUNDRED_THOUSAND_DAMAGE_COUNT(), // 20만 데미지 X 히트
    
    CHARGE_ATTACK_COUNT(), // 오의 횟수
    TRIPLE_ATTACK_COUNT, // 트리플 어택 횟수
    
    DISPEL_COUNT(), // 디스펠 횟수
    DEBUFF_COUNT(), // 디버프 횟수

    USE_FATAL_CHAIN, // 페이탈 체인 사용
    USE_ABILITY_COUNT(), // 어빌리티 사용횟수

    // 해제불가
    IMPOSSIBLE(),
    ;

}
