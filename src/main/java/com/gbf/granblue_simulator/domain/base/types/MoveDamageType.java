package com.gbf.granblue_simulator.domain.base.types;

/**
 * 행동에 따른 데미지 발생 타입 <br>
 * 데미지 개별의 타입이 아닌, 행동 전체를 기준으로 나타낸 타입
 */
public enum MoveDamageType {
    NORMAL, // 기본
    
    // 공격에 따른 타입
    ADVANTAGED, // 강상성
    CRITICAL,   // 크리티컬
    
    // 방어에 따른 타입
    DISADVANTAGED,   // 약상성
    BLOCK,  // 블록됨
    CUT,    // 컷됨
    BLOCK_CUT, // 블록 + 컷됨 (블록이 확률형이라 별도로 표시)
    ;
    
    // 기본적으로 공격에 따른 타입보다 방어에 따른 타입을 표시
    // 우선순위 (덮어쓰기) 는 위에서 아래순으로
}
