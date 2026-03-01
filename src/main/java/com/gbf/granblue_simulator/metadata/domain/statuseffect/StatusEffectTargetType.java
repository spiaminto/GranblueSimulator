package com.gbf.granblue_simulator.metadata.domain.statuseffect;

public enum StatusEffectTargetType {
    SELF,   // 캐릭터 자기자신
    SELF_AND_LEADER_CHARACTER, // 자신과 주인공
    SELF_AND_NEXT_CHARACTER,  // 자신과 다음 캐릭터
    SELF_AND_LOWEST_HP_CHARACTER, // 자신과 남은 체력비율 가장 낮은 아군

    LEADER_CHARACTER, // 주인공

    PARTY_MEMBERS,  // 아군 전체 (적의 공격 타겟시 발생하는 상태효과의 경우 전부 이거, 랜덤타겟시 별도로 구분로직 o)
    PARTY_MEMBERS_NOT_SELF, // 자신을 제외한 아군전체

    TARGETED, // 런타임시 지정된 타겟 사용 (적의 랜덤타겟 공격시 피격대상만)

    ENEMY,  // 적 (적 자신일때도 이것 사용)

    ALL_PARTY_MEMBERS,    // 참전자 아군 전체
    ALL_ENEMIES, // 참전자 적 전체

    MANUAL, // 로직 내부에서 직접 지정시 사용 (랜덤 타겟 공격에 따른 타겟 직접지정시)

    ;

    /**
     * 참전자 타겟 여부 확인 (효과 부여 우열 연산시 사용)
     */
    public boolean isAllMemberTarget() {
        return this == ALL_PARTY_MEMBERS || this == ALL_ENEMIES;
    }
}
