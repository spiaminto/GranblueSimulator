package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode
@ToString
public class StatusEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer duration; // 효과 시간 (실시간) [턴, 초, 레벨]
    private Integer level; // 레벨 (실시간)
    private String iconSrc;

    @Builder.Default
    private int activeModifierCount = -1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_status_effect_id")
    private BaseStatusEffect baseStatusEffect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Actor actor;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @CreationTimestamp
    private LocalDateTime updatedAt; // 생성시 초기화, 레벨 증가시 업데이트. 효과가 적용될때만(+레벨상승) 해당 필드갱신

    @Transient
    private boolean isTransient; // NO_EFFECT, MISS 등

    /**
     * 연관관계 함께 매핑
     *
     * @param actor
     * @return
     */
    public StatusEffect mapActor(Actor actor) {
        this.actor = actor;
        actor.getStatusEffects().add(this);
        return this;
    }

    /**
     * 정보 전달을 위해 set 만 (연관관계 X)
     *
     * @param actor
     * @return
     */
    public StatusEffect setActor(Actor actor) {
        this.actor = actor;
        return this;
    }

    /**
     * 외부에 보여줄 효과 인지 여부, 보여주면 true
     */
    public boolean isDisplayable() {
        return this.baseStatusEffect.isDisplayable();
    }

    /**
     * 리필제 효과인지 확인
     *
     * @return DurationType 이 LEVEL_INFINITE 이면 true
     */
    public boolean isRefillable() {
        return this.baseStatusEffect.getDurationType() == StatusDurationType.LEVEL_INFINITE;
    }

    protected void updateLevel(int level) {
        this.level = Math.clamp(level, 0, this.baseStatusEffect.getMaxLevel());
        this.iconSrc = this.getCurrentIconSrc();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 들어온 level 값 만큼 자신의 level 을 추가 <br>
     * 최고레벨 이상 증가하지 않음
     *
     * @param addLevel
     */
    public void addLevel(int addLevel) {
        this.updateLevel(this.level + addLevel);
    }

    /**
     * 들어온 level 값 만큼 자신의 level 을 감소 및 url 갱신 <br>
     * 0 미만으로 줄어들지 않음
     *
     * @param subtractLevel
     */
    public void subtractLevel(int subtractLevel) {
        this.updateLevel(this.level - subtractLevel);
    }

    public boolean isMaxLevel() {
        return this.level >= this.baseStatusEffect.getMaxLevel();
    }

    /**
     * activeModifierCount 수정
     *
     * @param activeModifierCount: 최소 0 부터 적용가능. 카운트 초과시 전체적용
     */
    public void updateActiveModifierCount(int activeModifierCount) {
        if (this.baseStatusEffect.getModifiers().size() <= activeModifierCount) this.activeModifierCount = -1;
        else {
            this.activeModifierCount = Math.max(activeModifierCount, 0);
        }
    }

    /**
     * 모든 BaseMove.modifiers 반환 <br>
     */
    public Map<StatusModifierType, StatusModifier> getBaseModifiers() {
        return this.baseStatusEffect.getModifiers();
    }

    /**
     * 활성화 되어있는 modifiers 만 반환 <br>
     * 실제 런타임에서 StatusEffect 의 modifier 적용시 이쪽을 사용
     */
    public Map<StatusModifierType, StatusModifier> getActiveModifiers() {
        if (this.activeModifierCount < 0) return this.baseStatusEffect.getModifiers();
        else {
            Map<StatusModifierType, StatusModifier> result = new LinkedHashMap<>();
            Set<Map.Entry<StatusModifierType, StatusModifier>> entries = this.getBaseStatusEffect().getModifiers().entrySet();
            int i = 1;
            for (Map.Entry<StatusModifierType, StatusModifier> entry : entries) {
                if (i <= this.activeModifierCount) break; // count  유효값 0~, 1부터 등록
                result.put(entry.getKey(), entry.getValue());
                i++;
            }
            return result;
        }
    }

    /**
     * 남은 효과시간을 반환 (턴, 초) 영속일시 9999
     *
     * @return
     */
    public int getRemainingDuration() {
        if (this.baseStatusEffect.getDurationType().isInfinite()) {
            return 9999;
        } else if (this.baseStatusEffect.getDurationType().isTimeBased()) {
            return Math.max(this.duration - (int) createdAt.until(LocalDateTime.now(), ChronoUnit.SECONDS), 0);
        } else if (this.baseStatusEffect.getDurationType().isTurnBased()) {
            return this.duration;
        } else {
            throw new IllegalArgumentException("[getDuration] Unknown duration type.");
        }
    }

    public boolean isExpired() {
        return this.getRemainingDuration() <= 0;
    }

    protected void updateDuration(int duration) {
        this.duration = Math.max(0, duration);
        this.iconSrc = this.getCurrentIconSrc();
    }

    /**
     * 같은 버프가 다시걸렷을때 효과시간 초기화
     */
    public void resetDuration() {
        if (this.baseStatusEffect.getDurationType().isTimeBased()) {
            LocalDateTime now = LocalDateTime.now();
            int elapsedSeconds = (int) createdAt.until(now, ChronoUnit.SECONDS);
            this.updateDuration(elapsedSeconds + baseStatusEffect.getDuration());
        } else {
            this.updateDuration(this.baseStatusEffect.getDuration());
        }
    }

    /**
     * 효과시간을 연장 (시간제는 연장불가)
     *
     * @param duration 연장할 턴 수
     */
    public void addDuration(int duration) {
        if (this.baseStatusEffect.getDurationType().isTimeBased())
            throw new IllegalArgumentException("[addDuration] Time based duration is not supported.");
        this.updateDuration(this.duration + duration);
    }

    /**
     * 효과시간을 단축 (시간제는 단축불가)
     *
     * @param duration
     */
    public void subtractDuration(int duration) {
        if (this.baseStatusEffect.getDurationType().isTimeBased())
            throw new IllegalArgumentException("[subtractDuration] Time based duration is not supported.");
        this.updateDuration(this.duration - duration);
    }

    /**
     * 턴 종료시 삭제되도록 duration 조정 (마운트, ...)
     */
    public void expireAtTurnEnd() {
        this.updateDuration(0); // 강화 효과 연장에 반응하지 않게 하기 위해 0으로 감소
    }

    /**
     * 영속인지 확인
     *
     * @return
     */
    public boolean isPerpetual() {
        return this.baseStatusEffect.getDurationType().isInfinite();
    }

    /**
     * 참전자 효과인지 확인
     *
     * @return
     */
    public boolean isForAllEnemies() {
        return this.getBaseStatusEffect().getTargetType() == StatusEffectTargetType.ALL_ENEMIES;
    }

    public boolean isSameFromStatus(StatusEffect statusEffect) {
        return this.getBaseStatusEffect().getId().equals(statusEffect.getBaseStatusEffect().getId());
    }

    /**
     * 해당 StatusModifierType 이 있는지 확인
     *
     * @param modifierType
     * @return 있으면 true
     */
    public boolean hasModifier(StatusModifierType modifierType) {
        return this.baseStatusEffect.getModifier(modifierType) != null;
    }

    /**
     * StatusModifierType 에 맞는 modifier 가져옴
     *
     * @param modifierType
     * @return 없으면 null
     */
    public StatusModifier getModifier(StatusModifierType modifierType) {
        return this.baseStatusEffect.getModifier(modifierType);
    }

    /**
     * StatusModifierType 에 맞는 modifier 의 value 에 level 을 적용하여 반환 (소숫점 둘째자리 내림, 리필식은 레벨 적용 x)
     *
     * @param modifierType 없으면 에러나니 직접 코드상에서 지정해서 호출
     * @throws IllegalArgumentException 없는 StatusModifierType 으로 요청시 발생
     */
    public double getModifierValue(StatusModifierType modifierType) {
        StatusModifier modifier = this.baseStatusEffect.getModifier(modifierType);
        if (modifier == null) // 없으면 특정 값 설정하지 않고 즉시 에러내는게 디버깅 할때 나을듯
            throw new IllegalArgumentException("없는 StatusModifier 접근, modifier = " + modifierType.name() + " 현재 스테이터스 = " + this.baseStatusEffect.toString());
        double modifierInitValue = modifier.getInitValue();
        return this.baseStatusEffect.getDurationType() != StatusDurationType.LEVEL_INFINITE && this.level > 0
                ? Math.floor(modifierInitValue * level * 100) / 100.0
                : modifierInitValue;
    }

    /**
     * MISS, RESIST 등의 이펙트 생성시 사용
     *
     * @param effectText "MISS", "RESIST", "NO EFFECT", ...
     * @return transient StatusEffect (isTransient = true, id = null)
     */
    public static StatusEffect getTransientStatusEffect(StatusEffectType type, String effectText, Actor actor) {
        return StatusEffect.builder()
                .duration(0)
                .baseStatusEffect(BaseStatusEffect.builder()
                        .type(type)
                        .duration(0)
                        .durationType(StatusDurationType.TURN)
                        .name(effectText)
                        .effectText(effectText)
                        .build())
                .level(0)
                .iconSrc("")
                .isTransient(true)
                .build()
                .setActor(actor);
    }

    /**
     * BaseStatusEffect 로 부터 저장을 위한 StatusEffect 를 생성후 반환 (actor set, 매핑 필요)
     */
    public static StatusEffect fromBaseEffect(BaseStatusEffect baseEffect, Actor actor) {
        StatusEffect statusEffect = StatusEffect.builder()
                .actor(actor)
                .duration(baseEffect.getDuration())
                .baseStatusEffect(baseEffect)
                .level(baseEffect.getMaxLevel() > 0 ? 1 : 0)
                .iconSrc(baseEffect.getIconSrcs().isEmpty() ? "" : baseEffect.getIconSrcs().getFirst())
                .build();
        statusEffect.iconSrc = statusEffect.getCurrentIconSrc();
        return statusEffect;
    }

    /**
     * 현재 조건에 맞는 iconSrc 를 가져옴, 필요시 반환 값으로 iconSrc 갱신
     */
    protected String getCurrentIconSrc() {
        if (this.baseStatusEffect.getIconSrcs().isEmpty()) return "";

        String currentIconSrc = "";
        if (this.baseStatusEffect.getMaxLevel() > 0) {
            // 레벨제
            currentIconSrc = this.baseStatusEffect.getIconSrcs().get(Math.clamp(this.level - 1, 0, this.baseStatusEffect.getIconSrcs().size() - 1));
        } else if (this.baseStatusEffect.getTargetType() == StatusEffectTargetType.ENEMY
                && this.baseStatusEffect.getDurationType() == StatusDurationType.TURN
                && this.baseStatusEffect.getDuration() > 0) {
            // 레벨제 아님 && 적(개인)타겟 && 턴제 && 0턴 초과 -> 효과 아이콘이 남은 턴을 따라감 (1턴이 0번째)
            currentIconSrc = this.baseStatusEffect.getIconSrcs().get(Math.clamp(this.duration - 1, 0, this.baseStatusEffect.getIconSrcs().size() - 1));
        } else {
            // 일반
            currentIconSrc = this.baseStatusEffect.getIconSrcs().getFirst();
        }
        return currentIconSrc;
    }

}
