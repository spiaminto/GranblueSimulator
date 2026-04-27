package com.gbf.granblue_simulator.metadata.domain.statuseffect;

import lombok.Getter;

public enum StatusEffectTargetType {
    SELF("character"),   // 캐릭터 자기자신
    SELF_AND_LEADER_CHARACTER("character"), // 자신과 주인공
    SELF_AND_NEXT_CHARACTER("character"),  // 자신과 다음 캐릭터
    SELF_AND_LOWEST_HP_CHARACTER("character"), // 자신과 남은 체력비율 가장 낮은 아군

    LEADER_CHARACTER("character"), // 주인공
    FIRST_CHARACTER("character"), // 남아있는 캐릭터중 첫번째 (페이탈 체인 게이지 등)
    NEXT_CHARACTER("character"),

    PARTY_MEMBERS("character"),  // 아군 전체 (적의 공격 타겟시 발생하는 상태효과의 경우 전부 이거, 랜덤타겟시 별도로 구분로직 o)
    PARTY_MEMBERS_NOT_SELF("character"), // 자신을 제외한 아군전체

    ENEMY("enemy"),  // 적 (적 자신일때도 이것 사용)

    ALL_PARTY_MEMBERS("allCharacter"),    // 참전자 아군 전체
    ALL_ENEMIES("allEnemy"), // 참전자 적 전체

    ;

    @Getter
    private final String category;

    StatusEffectTargetType(String category) {
        this.category = category;
    }

    /**
     * 참전자 타겟 여부 확인 (효과 부여 우열 연산시 사용)
     */
    public boolean isAllMemberTarget() {
        return this == ALL_PARTY_MEMBERS || this == ALL_ENEMIES;
    }

    public boolean isSameCategory(StatusEffectTargetType targetType) {
        return this.category.equals(targetType.category);
    }
}
