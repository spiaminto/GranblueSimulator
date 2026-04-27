function renderPotionCount(newVal, oldVal) {
    let potionCounts = newVal;
    let $potionWrappers = $('.potion-icon-wrapper');
    potionCounts.forEach(function (count, index) {
        let $potionWrapper = $potionWrappers.eq(index);
        $potionWrapper.find('.count').text(count + '개');
        if (count <= 0) $potionWrapper.find('.potion-overlay').addClass('not-ready');
    })
}

function renderHalation(newVal, oldVal) {
    // console.debug('[renderHalation] newVal = ', newVal, ' oldVal = ', oldVal);
    let isHalation = newVal;
    isHalation.forEach((isHalation, actorIndex) => {
        if (actorIndex === 0) return;
        if (isHalation) {
            $(`.battle-portrait.actor-${actorIndex} .hp-gauge-wrapper`).addClass('hide');
            $(`.ability-panel.actor-${actorIndex} .hp-gauge-wrapper`).addClass('hide');
        } else {
            $(`.battle-portrait.actor-${actorIndex} .hp-gauge-wrapper`).removeClass('hide');
            $(`.ability-panel.actor-${actorIndex} .hp-gauge-wrapper`).removeClass('hide');
        }
    })
}

window.renderHpInterval = [null, null, null, null, null];

function renderHp(newVal, oldVal) {
    // console.debug('[renderHp] newVal = ', newVal, ' oldVal = ', oldVal);
    let hps = newVal;
    hps.forEach((hp, actorIndex) => {
        if (actorIndex === 0) return; // 적 스킵

        let $portraitHpGaugeValue = $(`.battle-portrait.actor-${actorIndex} .hp-gauge-value .value`);
        let $abilityPanelHpGaugeValue = $(`.ability-panel.actor-${actorIndex} .hp-gauge-value .value`);

        let oldHp = oldVal[actorIndex];
        let hpDiff = hp - oldHp;
        if (hpDiff === 0) {
            $portraitHpGaugeValue.text(Math.max(hp, 0) || 0);
            $abilityPanelHpGaugeValue.text(Math.max(hp, 0) || 0);
            return;
        }

        // 1000ms 간 25ms 마다 렌더링한다고 가정 (총 40회 갱신)
        let intervalHp = hpDiff / 40 - 1;
        let currentHp = oldHp;
        let intervalCount = 0;

        window.clearInterval(window.renderHpInterval[actorIndex]);
        window.renderHpInterval[actorIndex] = window.setInterval(() => {
            currentHp = Math.max(currentHp + intervalHp, 0) || 0; // NaN 대비
            intervalCount++;
            $portraitHpGaugeValue.text(Math.floor(currentHp));
            $abilityPanelHpGaugeValue.text(Math.floor(currentHp));
            if (intervalCount >= 39) {
                window.clearInterval(window.renderHpInterval[actorIndex]);
                window.renderHpInterval[actorIndex] = null;

                $portraitHpGaugeValue.text(Math.max(hp, 0));
                $abilityPanelHpGaugeValue.text(Math.max(hp, 0));
            }
        }, 25);
    });
}

function renderHpRate(newVal, oldVal) {
    // console.debug('[renderHpRate] newVal = ', newVal, ' oldVal = ', oldVal);

    newVal.forEach((hpRate, actorIndex) => {
        if (actorIndex === 0) {
            const $hpContainer = $('.enemy-info-container .hp-container');
            const $hpBar = $hpContainer.find('.hp-bar');
            const oldRate = oldVal ? oldVal[actorIndex] : hpRate;
            const isLayeredHpBar = $hpContainer.attr('data-is-layered-hp-bar');

            // % 수치 갱신
            $hpContainer.find('.value-hp').text(hpRate + '%');

            if (isLayeredHpBar === 'true') {
                $hpBar.find('.hp-layer').css('display', '');

                // 각 레이어의 data-layer-size 기반으로 너비 계산
                let lowerBound = 0;
                $hpBar.find('.hp-layer-container').each(function () {
                    const layerSize = parseFloat($(this).attr('data-layer-size'));
                    const upperBound = lowerBound + layerSize;
                    const filled = Math.min(layerSize, Math.max(0, hpRate - lowerBound));
                    const widthPercent = (filled / layerSize) * 100;

                    $(this).find('.hp-layer-fill').css('width', widthPercent + '%');
                    lowerBound = upperBound;
                });

            } else {
                $hpBar.find('.hp-layer-fill').css('width', hpRate + '%');
            }

            updateGhost($hpBar, oldRate, hpRate, hpRate < oldRate);
            return;
        }

        // 캐릭터
        const $commandHpBar = $(`.battle-portrait.actor-${actorIndex} .hp-gauge .progress-bar`);
        const $abilityPanelHpBar = $(`.ability-panel.actor-${actorIndex} .hp-gauge .progress-bar`);

        $commandHpBar.css('width', hpRate + '%');
        $abilityPanelHpBar.css('width', hpRate + '%');

        if (hpRate <= 25) {
            $commandHpBar.addClass('bg-danger');
            $abilityPanelHpBar.addClass('bg-danger');
        } else {
            $commandHpBar.removeClass('bg-danger');
            $abilityPanelHpBar.removeClass('bg-danger');
        }
    });
}

function updateGhost($hpBar, oldRate, newRate, isDamage) {
    const $ghost = $hpBar.find('.hp-bar-ghost'); // 기본적으로 transition 이 지정되어있음

    if (isDamage) {
        // 데미지 구간만 표시: newRate 위치에서 시작, width = 데미지량
        const damageWidth = oldRate - newRate;
        $ghost.addClass('no-transition');
        $ghost.css({left: newRate + '%', width: damageWidth + '%'});

        requestAnimationFrame(() => {
            $ghost.removeClass('no-transition');
            $ghost.css('width', '0%'); // 트랜지션 재적용 후 감소시킴
        });

    } else {
        // 회복: ghost 즉시 숨김
        $ghost.addClass('no-transition');
        $ghost.css({left: '0%', width: '0%'}); // 트랜지션 없이 즉시 증가시킴

        requestAnimationFrame(() => {
            $ghost.removeClass('no-transition');
        });
    }
}

window.renderBarrierInterval = [null, null, null, null, null];

function renderBarriers(newVal, oldVal) {
    // console.debug('[renderBarrier] newVal = ', newVal, ' oldVal = ', oldVal);
    let barriers = newVal;
    barriers.forEach((barrier, actorIndex) => {
        if (actorIndex === 0) return;
        let oldBarrier = oldVal[actorIndex];

        let $portraitBarrierValue = $(`.battle-portrait.actor-${actorIndex} .barrier-value .value`);
        let $abilityBarrierValue = $(`.ability-panel.actor-${actorIndex} .barrier-value .value`);

        let barrierDiff = barrier - oldBarrier;
        if (!barrierDiff) {
            if (!oldBarrier) {
                // 이전 베리어가 무효값 && 베리어 차이도 무효값 -> ''
                $portraitBarrierValue.text('');
                $abilityBarrierValue.text('');
            } else {
                // 이전베리어가 유효값, 베리어 차이가 무효값 (0포함) -> 유지
                $portraitBarrierValue.text(barrier);
                $abilityBarrierValue.text(barrier);
            }
            return;
        }

        // 1000ms 간 25ms 마다 렌더링한다고 가정 (총 40회 갱신)
        let intervalValue = barrierDiff / 40 - 1;
        let currentBarrier = oldBarrier;
        let intervalCount = 0;

        window.clearInterval(window.renderBarrierInterval[actorIndex]);
        window.renderBarrierInterval[actorIndex] = window.setInterval(() => {
            currentBarrier = Math.max(intervalValue + intervalValue, 0) || 0;
            intervalCount++;
            $portraitBarrierValue.text(Math.floor(currentBarrier));
            $abilityBarrierValue.text(Math.floor(currentBarrier));
            if (intervalCount >= 39) {
                window.clearInterval(window.renderBarrierInterval[actorIndex]);
                window.renderBarrierInterval[actorIndex] = null;

                if (barrier <= 0) {
                    $portraitBarrierValue.text('');
                    $abilityBarrierValue.text('');
                } else {
                    $portraitBarrierValue.text(barrier);
                    $abilityBarrierValue.text(barrier);
                }
            }
        }, 25);

    })
}

function renderEnemyTriggerHps(newVal, oldVal) {
    // console.debug('[renderEnemyTriggerHps] newVal = ', newVal, ' oldVal = ', oldVal);
    let triggerHps = newVal; // 오름차순 정렬되어 전달

    let $hpBar = $('.hp-container.enemy .hp-bar');
    $hpBar.find('.hp-trigger').remove();

    triggerHps.forEach((hpRate, index) => {
        let $hpTrigger = $(`<div class="hp-trigger"></div>`).css('left', hpRate + '%');
        if (index >= triggerHps.length - 1) {
            if (gameStateManager.getState('omen')?.type === OmenType.HP_TRIGGER) {
                applyGlow($hpTrigger, {spread: 2, blur: 7});
            } else {
                applyGlow($hpTrigger, {spread: 1, blur: 3});
            }
        }
        $hpBar.append($hpTrigger);
    })
}

function renderEnemyMaxChargeGauge(newVal, oldVal) {
    // console.debug('[renderEnemyMaxChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    if (newVal <= 0) console.error('[renderEnemyMaxChargeGauge] new chargeGauge <= 0, chargeGauge = ', newVal);
    if (oldVal === undefined) oldVal = 0; // 첫 초기화시 undefined, 이후는 오류
    let diff = Math.abs(newVal - oldVal);
    if (newVal > oldVal) {
        let $chargeTurn = $(`<div class="charge-turn"></div>`)
        // _.range(diff).forEach(index => $('.charge-turn-container.enemy').append($chargeTurn))
        _.times(diff, () => $('.charge-turn-container.enemy').append($chargeTurn.clone()));
    } else {
        // _.range(0, diff).forEach(index => $('.charge-turn-container.enemy .charge-turn:last').remove());
        _.times(diff, () => $('.charge-turn-container.enemy .charge-turn:last').remove());
    }
}

function renderChargeGauge(newVal, oldVal) {
    // console.debug('[renderChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    let chargeGauges = newVal;
    let canChargeAttacks = gameStateManager.getState('canChargeAttacks');
    let chargeAttackActivated = $('#chargeAttackActiveCheck').prop('checked');
    chargeGauges.forEach((chargeGauge, actorIndex) => {
        if (actorIndex === 0) {
            $('.charge-turn-container.enemy .charge-turn').removeClass('on').each(function (index, element) {
                if (chargeGauge > index) $(element).addClass('on');
            });
        } else {
            let $portraitChargeGauge = $(`.battle-portrait.actor-${actorIndex} .charge-gauge`);
            let $abilityChargeGauge = $(`.ability-panel.actor-${actorIndex} .charge-gauge`);

            // 값 반영
            $portraitChargeGauge.find('.value').text(chargeGauge);
            $abilityChargeGauge.find('.value').text(chargeGauge);

            // 오의게이지 progress 반영
            let additionalChargeGauge = chargeGauge > 100 ? chargeGauge - 100 : 0;
            let normalChargeGauge = chargeGauge - additionalChargeGauge;
            $portraitChargeGauge
                .find('.progress.additional .progress-bar').css('width', additionalChargeGauge + '%').end()
                .find('.progress:not(.additional) .progress-bar').css('width', normalChargeGauge + '%');
            $abilityChargeGauge
                .find('.progress.additional .progress-bar').css('width', additionalChargeGauge + '%').end()
                .find('.progress:not(.additional) .progress-bar').css('width', normalChargeGauge + '%');

            // 오의 ON, 오의 사용 가능시 하이라이트
            let canChargeAttack = canChargeAttacks[actorIndex];
            if (canChargeAttack && chargeAttackActivated) {
                $portraitChargeGauge.addClass('active');
                $abilityChargeGauge.addClass('active');
            } else {
                $portraitChargeGauge.removeClass('active');
                $abilityChargeGauge.removeClass('active');
            }
        }
    });
}

function renderFatalChainGauge(newVal, oldVal) {
    // console.debug('[renderFatalChainGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    let fatalChainGauge = newVal;
    $('.fatal-chain-gauge-value').find('.value').text(fatalChainGauge);
    $('.fatal-chain-gauge .progress-bar').css('width', fatalChainGauge + '%');
}

/**
 * 어빌리티 쿨타임 렌더링
 * @param newVal abilityCooldowns 또는 abilitySealeds
 */
function renderAbilityCoolDowns(newVal) {
    // console.debug('[renderAbilityCoolDowns] abilities = ', newVal);
    const allAbilities = gameStateManager.getState('ability');

    let abilitySealeds = gameStateManager.getState('abilitySealeds');
    gameStateManager.getState('abilityCoolDowns').forEach((actorCooldowns, actorIndex) => {
        if (actorIndex === 0) return; // 적 스킵

        // 해당 actorIndex의 어빌리티 메타데이터 (order 순)
        const actorAbilities = Object.values(allAbilities)
            .filter(ability => ability.actorIndex === actorIndex)
            .sort((a, b) => a.order - b.order);

        actorCooldowns.forEach((cooldown, index) => {
            const ability = actorAbilities[index];
            if (!ability) return;

            // slick-cloned 포함 (전부 적용해야 스와이프 시 자연스러움)
            const $abilityIcons = $(`.ability-panel.actor-${actorIndex} .ability-icon[data-move-id="${ability.id}"]`);

            $abilityIcons.each(function () {
                const $abilityIcon = $(this);
                const $cooldownText = $abilityIcon.find('.ability-cooldown-text');
                const $cooldownValue = $abilityIcon.find('.ability-cooldown-text .value');
                const $abilityOverlay = $abilityIcon.find('.ability-overlay');

                // none-usable이면 쿨다운 무시
                // if ($abilityOverlay.hasClass('none-usable')) return;

                if (cooldown > 999) {
                    // cooldown > 999: 재사용 불가
                    $cooldownText.addClass('invisible');
                    $cooldownValue.text('00'); // 레이아웃 유지
                    $abilityOverlay.addClass('none-usable');
                } else if (cooldown > 0) {
                    // 쿨다운 중
                    $cooldownText.removeClass('invisible');
                    $cooldownValue.text(`${cooldown}`);
                    $abilityOverlay.removeClass('none-usable').addClass('not-ready');
                } else {
                    // 사용 가능
                    $cooldownText.addClass('invisible');
                    $cooldownValue.text('00'); // 레이아웃 유지
                    $abilityOverlay.removeClass('not-ready none-usable');
                }

                // abilitySealed 일때, 쿨타임을 무시하고 none-usable 추가
                let abilitySealed = abilitySealeds[actorIndex][index];
                if (abilitySealed) {
                    $abilityOverlay.addClass('none-usable');
                }

            });

            // 인디케이터 업데이트
            updateAbilityIndicator(actorIndex, index, ability, cooldown);
        });
    });
}

/**
 * 배틀 멤버 초상화의 어빌리티 인디케이터 업데이트
 */
function updateAbilityIndicator(actorIndex, index, ability, cooldown) {
    const $indicator = $(`.battle-portrait.actor-${actorIndex} .ability-usable-indicator`).eq(index);
    if ($indicator.length === 0) return;

    const sealed = gameStateManager.getState(`abilitySealeds.${actorIndex}.${index}`);
    const abilityType = ability.abilityType.toLowerCase();


    if (cooldown <= 0 && !sealed) {
        $indicator.addClass(abilityType);
    } else {
        $indicator.removeClass(abilityType);
    }
}

function renderAbilitySealeds(newVal, oldVal) {
    // console.debug('[renderAbilitySealeds] newVal = ', newVal, ' oldVal = ', oldVal);
    newVal.forEach(function (abilitySealeds, actorIndex) {
        let $abilityPanels = $(`#abilitySlider .ability-panel.actor-${actorIndex}`); // slick-cloned 까지 전부 렌더링해야 스와이프할때 자연스러움
        $abilityPanels.get().forEach(abilityPanel => {
            let $abilityOverlays = $(abilityPanel).find('.ability-overlay');
            if ($abilityOverlays.length === 0) return;

            abilitySealeds.forEach(function (abilitySealed, index) {
                if (abilitySealed === true) {
                    $abilityOverlays.eq(index)?.addClass('none-usable');
                } else {
                    $abilityOverlays.eq(index)?.removeClass('none-usable');
                }
            });
        });
    });
}

/**
 * 소환석 쿨다운 렌더링
 * @param newVal - summonCooldowns 또는 usedSummon
 */
function renderSummonCooldowns(newVal, oldVal) {
    // console.debug('[renderSummonCooldowns] newVal =', newVal, 'oldVal =', oldVal);
    const $summons = $('#partyCommandContainer .summon-display-wrapper .summon-list-item:not(.empty)');

    gameStateManager.getState('summonCooldowns').forEach((cooldown, index) => {
        const $summon = $summons.eq(index);
        if ($summon.length === 0) return;

        const $summonCooldownEl = $summon.find('.summon-cooldown');
        const $summonOverlay = $summon.find('.summon-overlay');

        // data 속성 업데이트
        $summon.attr({
            // 'data-cooldown': cooldown,
            // 'data-usable': cooldown <= 0
        });

        // 쿨다운별 처리
        if (cooldown > 999) {
            // 재사용 불가
            $summonCooldownEl.text('재사용 불가');
            $summonCooldownEl.removeClass('invisible');
            $summonOverlay.removeClass('not-ready').addClass('none-usable');
        } else if (cooldown > 0) {
            // 쿨다운 중
            $summonCooldownEl.text(`${cooldown}턴`);
            $summonCooldownEl.removeClass('invisible');
            $summonOverlay.removeClass('none-usable').addClass('not-ready');
        } else {
            // 사용 가능
            $summonCooldownEl.addClass('invisible');
            $summonCooldownEl.text('00'); // 레이아웃 유지
            $summonOverlay.removeClass('not-ready none-usable');
        }
    });

    // 소환석 이미 사용한경우 사용 불가능하도록 설정 (쿨다운 갱신 후)
    if (gameStateManager.getState('usedSummon') === true) {
        $summons.find('.summon-overlay').addClass('on-rail');
    } else {
        $summons.find('.summon-overlay').removeClass('on-rail');
    }
}

function renderUnionSummonChance(newVal, oldVal) {
    // console.debug('[renderUnionSummonChance newVal = ', newVal, ' oldVal = ', oldVal);
    let unionSummonInfo = newVal;
    if (unionSummonInfo === null) return;

    let $wrapper = $('.union-summon-chance-wrapper');
    let $imgs = $wrapper.find('img');
    let $portrait = $imgs.eq(0);
    $portrait.attr('src', unionSummonInfo.portraitImageSrc);

    $wrapper.css('display', 'block');
    setTimeout(() => $wrapper.css('display', 'none'), 3000);
}

/**
 * 소환석 버튼 사용가능 여부 렌더링
 * @param newVal leaderId
 */
function renderSummonButton(newVal, oldVal) {
    // console.debug('[renderSummonButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let leaderActorId = gameStateManager.getState('leaderActorId'); // number or null
    if (leaderActorId) {
        // 주인공이 살아있을때만 소환가능
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').removeClass('not-ready');
    } else {
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').addClass('not-ready');
    }
}

window.characterPortraitStatusShowHideInterval = [];

function renderCurrentStatusEffectsIcons(newVal, oldVal) {
    // console.debug('[renderCurrentStatusEffects] newVal =', newVal, ' oldVal = ', oldVal);
    newVal.forEach(function (currentStatusEffects, actorIndex) {

        let $fragment = $('<div>');
        currentStatusEffects.forEach(function (status, index) {
            let beforeStatus = currentStatusEffects[index - 1];
            let displayClassName = index > 0 && (beforeStatus.name === status.name && beforeStatus.iconSrc === status.iconSrc) ? 'd-none' : '';
            let $statusInfo = $(`
                <div class="status ${displayClassName}" data-status-type="${status.type}">
                  <img src="${status.iconSrc}" class="status-icon${status.iconSrc.length < 1 ? ' none-icon' : ''}" alt="${status.name} icon">
                </div>`);
            $fragment.append($statusInfo);
        });

        // 어빌리티레일 + 초상화 - 상태효과 아이콘
        let $statusContainers = $('.status-container.actor-' + actorIndex); // .slick-cloned 로 복제된 컨테이너까지 들어옴
        $statusContainers.each(function () {
            let $newContainer = $(this).clone(true);
            $newContainer.find('.status').remove();
            $newContainer.append($fragment.find('.status').clone()); // fragment 복제 필수
            $(this).replaceWith($newContainer);
        });

        // 상태효과 아이콘 갯수 초과시 끊어서 보여주기
        if (actorIndex === 0) {
            // 적 HP 바 위쪽 아이콘
            let $enemyStatusEffects = $('.status-container.enemy .status');
            // 인터벌 및 애니메이션큐 초기화
            clearInterval(window.enemyStatusShowHideInterval);
            $enemyStatusEffects.stop(true, true).show(0);

            if ($enemyStatusEffects.length > 16) {
                let statusShowHideCallback = function () {
                    let $frontStatuses = $('.status-container.enemy .status').slice(0, 16);
                    $frontStatuses.hide(0).delay(2000).show(0);
                }
                statusShowHideCallback();
                window.enemyStatusShowHideInterval = setInterval(statusShowHideCallback, 4000);
            } else {
                $enemyStatusEffects.show(0);
            }

        } else {
            // 캐릭터 - 초상화
            let $actorStatusEffects = $(`.battle-portrait .status-container.actor-${actorIndex} .status`);

            clearInterval(window.characterPortraitStatusShowHideInterval[actorIndex]);
            $actorStatusEffects.stop(true, true).show(0);

            if ($actorStatusEffects.length > 8) {
                let statusShowHideCallback = function () {
                    let $statusContainer = $(`.battle-portrait .status-container.actor-${actorIndex}`);
                    let isBackStatusCountExceeded = $actorStatusEffects.length - 8 > 8;
                    let sliceCount = isBackStatusCountExceeded ? 16 : 8;
                    let $frontStatuses = $actorStatusEffects.slice(0, sliceCount);
                    $frontStatuses
                        .hide(0, function () {
                            $statusContainer.removeClass('extend');
                        })
                        .delay(2000)
                        .show(0, function () {
                            if (isBackStatusCountExceeded) {
                                $statusContainer.addClass('extend');
                            }
                        });
                }
                statusShowHideCallback();
                window.characterPortraitStatusShowHideInterval[actorIndex] = setInterval(statusShowHideCallback, 4000);
            } else {
                $actorStatusEffects.show(0);
            }

            // 캐릭터 - 어빌리티 슬라이더 상태효과 아이콘: 별도 #abilitySlider.afterChange.statusHide 트리거
            if ($('.slick-active .status-container.actor-' + actorIndex).length > 0) {
                $('#abilitySlider').trigger('afterChange.statusHide');
            }
        }
    });
}

function renderMoveNameIndicator(newVal, oldVal) {
    // console.debug('[renderMoveNameIndicator] newVal = ', newVal, ' oldVal = ', oldVal);

    const $container = $('.move-name-info-container');
    // 새 행 생성 후 컨테이너에 추가
    const $newRow = $(`
      <div class="move-name-info" style="display:none;">
        <span class="move-name-info-text"></span>
      </div>`);
    $newRow.find('.move-name-info-text').text(newVal);
    $container.append($newRow).show();

    // 개별 행 단위로 fade-in -> 대기 -> fade-out
    $newRow.fadeIn(100).delay(1300).fadeOut(100, function () {
        $(this).remove()
    });
}

function renderMoveResultHonorIndicator(newVal, oldVal) {
    // console.debug('[renderMoveResultHonorIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let honor = newVal;
    if (honor === 0) return;
    $('.honor-container')
        .find('.honor-value').text(honor).end()
        .animate({left: '0px'}, 50).delay(1500).animate({left: '-40%'}, 100);
}

function renderOmen(newVal, oldVal) {
    // console.debug('[renderOmen] newVal = ', newVal, ' oldVal = ', oldVal); // {OmenDto} stage.gGameStatus.omen
    let omen = newVal;

    // 전조 해제 / 브레이크
    if (omen.isEmpty() || omen.isBreak) {
        $('.omen-container-top').removeClass('activated');
        $('.omen-container-bottom.enemy').removeClass('activated');
        $('.charge-turn-container.enemy .charge-turn').each(function (index, element) {
            removeGlow($(element).removeClass('active')); // CT 턴 액티브 해제
        });
        return;
    }

    //전조 발동 또는 진행중
    // 상단 컨테이너
    let $cancelConditions = [];
    omen.cancelConditions.forEach((cancelCondition, index) => {
        let isImpossibleCancelCondition = cancelCondition.type === 'IMPOSSIBLE';
        let remainValueString = cancelCondition.remainValue > 10000 ? cancelCondition.remainValue.toLocaleString('ko-KR') : cancelCondition.remainValue.toString();
        let $omen = $(
            `<div class="omen-wrapper ${omen.type.className}">
                  <i class="bi bi-check2-circle icon-bold text-dark-blue"></i>
                  <span class="omen-prefix ">${cancelCondition.info}</span>
                  <span class="omen-value">${isImpossibleCancelCondition ? '' : ' : ' + remainValueString}</span>
                </div>`
        );
        let oldCondition = oldVal.cancelConditions[index];
        if (!!oldCondition && oldCondition.remainValue !== cancelCondition.remainValue) {
            $omen.find('.omen-value').css('color', 'white').animate({opacity: 1}, 300, function () {
                $(this).css('color', 'black')
            });
        }
        $cancelConditions.push($omen);
    });
    $('.omen-container-top').addClass('activated').empty().append(...$cancelConditions);

    let omenBackgroundColor = getComputedStyle($cancelConditions[0].get(0)).backgroundColor;
    $cancelConditions.forEach(($omen, index) => {
        applyGlow($omen, {color: omenBackgroundColor, blur: 4, spread: 2})
    });

    // 하단 컨테이너
    let $bottomOmenContainer = $('.omen-container-bottom.enemy');
    $bottomOmenContainer
        .addClass('activated')
        .html($(
            `<div class="omen-wrapper ${omen.type.className}">
                  <div class="omen-prefix">${omen.type.info}: ${omen.name}</div>
                </div>`
        ));
    applyGlow($bottomOmenContainer.find('.omen-wrapper'), {color: omenBackgroundColor, blur: 4, spread: 2})

    // CT기 CT 턴 액티브
    if (omen.type === OmenType.CHARGE_ATTACK) {
        $('.charge-turn-container.enemy .charge-turn').each(function (index, element) {
            applyGlow($(element).addClass('active'), {color: 'rgba(255, 243, 205, 0.8)', spread: 3, blur: 4});
        })

    }
}

function renderGuards(newVal, oldVal) {
    // console.debug('[renderGuards] newVal = ', newVal, ' oldVal = ', oldVal);
    let guardStates = newVal;
    guardStates.forEach(function (guardState, actorOrder) {
        if (guardState) {
            $(`#actorContainer > .actor-${actorOrder}`).find('.guard-status').addClass('guard-on');
            $(`.advanced-command-container .guard-button.party-${actorOrder} .guard-img`).attr('src', '/assets/img/gl/ui-guard-on.png');
        } else {
            $(`#actorContainer > .actor-${actorOrder}`).find('.guard-status').removeClass('guard-on');
            $(`.advanced-command-container .guard-button.party-${actorOrder} .guard-img`).attr('src', '/assets/img/gl/ui-guard-off.png');
        }
    });
}

function renderAttackButton(newVal, oldVal) {
    // console.debug('[renderAttackButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let isAttackClicked = gameStateManager.getState('isAttackClicked');
    let isQuestCleared = gameStateManager.getState('isQuestCleared');
    let isQuestFailed = gameStateManager.getState('isQuestFailed');

    if (isQuestCleared) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-next.png').css({
            'left': '15%',
            'width': '100%'
        })
    } else if (isQuestFailed) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-rejoin.png');
    } else if (isAttackClicked) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack-cancel.png');
    } else {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack.png');
    }
}

function renderMemberInfoContainer(newVal, oldVal) {
    // console.debug('[renderMemberInfoContainer] newVal = ', newVal, ' oldVal = ', oldVal);
    let memberInfos = newVal;
    let $memberInfoWrappers = [];
    memberInfos.forEach(function (memberInfo, index) {
        let $memberInfoWrapper = $(`
          <div class="member-info-wrapper">
            <div class="element-type ${memberInfo.leaderActorElementType.toLowerCase()}"></div>
            <div class="member-username">
            ${escapeHtml(memberInfo.username)}
            </div>
            <div class="d-flex align-items-center justify-content-between">
              <div class="member-leader-actor-name">[${memberInfo.leaderActorName}]</div>
              <div class="honor"><span class="value">${memberInfo.honor.toLocaleString()}</span>pt</div>
            </div>
            <div class="honor-icon place-${index}"></div>
          </div>
        `);
        $memberInfoWrappers.push($memberInfoWrapper);
    })
    $('.member-info-container').empty().append(...$memberInfoWrappers);
}

function renderChatMessages(newVal, oldVal) {
    // console.debug('[renderChatMessages] newVal = ', newVal, ' oldVal = ', oldVal);
    let newChats = newVal;
    let $chatMessageContainer = $('.chat-message-container');
    const isScrolledToBottom = $chatMessageContainer[0].scrollHeight - $chatMessageContainer.scrollTop() <= $chatMessageContainer.outerHeight() + 20; // 유저 스크롤 여부 미리 확인

    newChats.forEach(chat => {
        // 채팅 메시지 목록
        const time = new Date(chat.createdAt).toLocaleTimeString('ko-KR', {hour: '2-digit', minute: '2-digit'});
        let content = chat.type === 'STAMP' ? `<img src="/static/gbf/img/stamp/${chat.chatStamp}.png" style="width:56px; height:56px; object-fit:contain;;">` : escapeHtml(chat.content);
        let $chatMessage = $(`
            <div class="chat-message ${chat.type === 'STAMP' ? 'stamp-chat' : ''}">
              <div class="chat-message-header">
                <strong class="username">${escapeHtml(chat.username)}</strong>
                <span class="time">${time}</span>
              </div>
              <div class="chat-message-content">
                ${content}      
              </div>
            </div>
        `);
        $chatMessageContainer.append($chatMessage);

        // 채팅 팝업
        if (oldVal === null) return; // 첫입장시(chatMessage === null) 팝업 렌더링 없음

        const randomTop = Math.floor(Math.random() * 70);
        const randomLeft = Math.floor(Math.random() * 70);
        const $popup = $('<div>').addClass('chat-popup-wrapper').css({
            position: 'absolute',
            top: `${randomTop}%`,
            left: `${randomLeft}%`
        }).hide();
        const popupHeader = `<div class="chat-popup-name">${chat.username}</div>`;

        if (chat.type === 'STAMP') {
            $popup.html(`${popupHeader}<img src="/static/gbf/img/stamp/${chat.chatStamp}.png" style="width: 56px">`);
        } else {
            const $text = $('<div>').addClass('chat-popup-text').text(chat.content);
            $popup.html(popupHeader).append($text);
        }

        $('#battleCanvas .chat-popup-container').append($popup);

        // 애니메이션 및 제거
        $popup.fadeIn(200).delay(2500).fadeOut(400, function () {
            // $(this).remove();
        });

    });


    // 자동 스크롤 (유저가 위쪽 스크롤 중이면 안함)
    if (isScrolledToBottom) {
        $chatMessageContainer.scrollTop($chatMessageContainer[0].scrollHeight);
    }
}

function renderTurnIndicator(newVal, oldVal) {
    // console.debug('[renderTurnIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let currentTurn = newVal;
    $('.turn-indicator .value').text(currentTurn); // topMenu + battleCanvas
    $('#battleCanvas .turn-indicator-container').addClass('show').on('transitionend', function () {
        setTimeout(() => $(this).removeClass('show'), 2000)
    });
}

function renderRemainingTimeIndicator(newVal, oldVal) {
    // // console.debug('[renderRemainingTimeIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let remainingTime = newVal;
    $('.remaining-time-indicator .value').text(remainingTime);
}

// 초기 렌더링 ===========================================================================================================

/**
 * 전체 어빌리티 초기 렌더링
 */
function renderAllAbilities(abilities) {
    // console.debug('[renderAllAbilities] abilities = ', abilities);
    // 기존 어빌리티 초기화
    $(`.slick-slide:not(.slick-cloned) .ability-wrapper`).empty();

    // 새로 렌더링
    Object.entries(abilities).forEach(([abilityId, ability]) => {
        renderSingleAbility(abilityId, ability);
    });
}

/**
 * 개별 어빌리티 렌더링
 * @param {string} abilityId - '5104'
 * @param {Object} ability - ability 객체
 */
function renderSingleAbility(abilityId, ability) {
    if (!ability) { // 삭제된 경우
        $(`.ability-icon[data-move-id="${abilityId}"]`).remove();
        return;
    }

    const $abilityWrapper = $(`#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel.actor-${ability.actorIndex} .ability-wrapper`);
    let $abilityIcon = $abilityWrapper.find(`.ability-icon[data-move-id="${abilityId}"]`);

    if ($abilityIcon.length === 0) {
        // 새로 생성
        $abilityIcon = createAbilityElement(ability);
        // 삽입
        const $siblings = $abilityWrapper.find('.ability-icon');
        if ($siblings.length === 0) {
            $abilityWrapper.append($abilityIcon);
        } else {
            const $target = $siblings.filter((index, sibling) => parseInt($(sibling).attr('data-order')) >= ability.order).first();
            if ($target.length === 0) {
                $abilityWrapper.append($abilityIcon); // 없으면 wrapper 에 (맨앞)
            } else {
                $target.before($abilityIcon);
                if ($target.attr('data-order') == ability.order) $target.remove(); // order 같으면 기존 아이콘 삭제
            }
        }
    }
    syncSlickClones(ability.actorIndex);
}

/**
 * 어빌리티 슬라이더에서, 자연스러운 표시를 위해 slick-cloned 요소 에도 변경사항 적용
 * @param actorIndex
 */
function syncSlickClones(actorIndex) {
    // 원본
    const originalHtml = $(`.slick-slide:not(.slick-cloned) .ability-panel.actor-${actorIndex} .ability-wrapper`).html();
    // clone에 덮어쓰기
    $(`.slick-cloned .ability-panel.actor-${actorIndex} .ability-wrapper`).html(originalHtml);
    $(`.slick-cloned .ability-icon`).off('click').on('click', onAbilityIconClicked);
}

/**
 * 어빌리티(아이콘) 요소 생성
 */
function createAbilityElement(ability) {
    const isReady = ability.cooldown <= 0;
    const cooldownText = ability.cooldown > 0 ? `${ability.cooldown}턴` : '';

    const $abilityIcon = $(`
        <div class="ability-icon"
             data-move-id="${ability.id}"
             data-order="${ability.order}"
             >
            <img src="${ability.iconImageSrc || ''}" alt="abilityIcon"/>
            <div class="ability-cooldown-text ${cooldownText ? '' : 'invisible'}">
                <span class="value">${cooldownText ? cooldownText : '00'}</span>
                턴
            </div>
            <div class="ability-overlay command-overlay ${isReady ? '' : 'not-ready'}" data-move-id="${ability.id}"></div>
        </div>
    `);

    // 클릭 이벤트
    $abilityIcon.on('click', onAbilityIconClicked);

    return $abilityIcon;
}

/**
 * 전체 어빌리티 사용 인디케이터 초기 렌더링
 * @param abilities
 */
function renderAllAbilityIndicators(abilities) {
    // console.debug('[renderAllAbilityIndicators] abilities = ', abilities);

    for (let actorIndex = 1; actorIndex <= 4; actorIndex++) {
        const $indicators = $(`.battle-member-wrapper .battle-portrait.actor-${actorIndex} .ability-usable-indicator-wrapper .ability-usable-indicator`);

        const actorAbilities = Object.values(abilities)
            .filter(ability => ability.actorIndex === actorIndex)
            .sort((a, b) => a.order - b.order);

        actorAbilities.forEach((ability, index) => {
            const $indicator = $indicators.eq(index);

            // 없음
            if (!ability) {
                $indicator.addClass('none');
                return;
            }

            // 사용 가능여부 표시
            const abilitySealed = ability.sealed || false;
            const abilityType = ability.abilityType.toLowerCase();
            if (ability.cooldown <= 0 && !abilitySealed) {
                $indicator.addClass(abilityType);
            } else {
                $indicator.removeClass(abilityType);
            }
        });
    }
}

function renderAllChargeAttacks(chargeAttacks) {
    /*
    {
    "type": "CHARGE_ATTACK",
    "id": 9305,
    "name": "영준호걸",
    "order": 1,
    "actorId": 2630,
    "actorIndex": 1,
    "info": "[데미지]4.5배[/데미지]3턴간 아군 전체에 재생 효과, 스트렝스 효과",
    "cooldown": 0,
    "maxCooldown": 0,
    "iconImageSrc": "",
    "portraitImageSrc": "",
    "cutinImageSrc": "",
    "abilityType": "",
    "cjsName": "",
    "statusEffects": [
        {
            "type": "BUFF",
            "name": "재생",
            "iconSrc": "/static/gbf/img/status/status_1002.png",
            "effectText": "재생",
            "statusText": "턴 종료시 체력을 2000 회복",
            "durationType": "TURN",
            "duration": 3,
            "level": 0,
            "maxLevel": 0,
            "remainingDuration": 3,
            "removed": false,
            "removable": true,
            "resistible": true
        },
        {
            "type": "BUFF",
            "name": "스트렝스",
            "iconSrc": "/static/gbf/img/status/status_1240.png",
            "effectText": "스트렝스",
            "statusText": "자신의 현재 체력의 비율에 비례하여 공격력이 최대 20% 증가",
            "durationType": "TURN",
            "duration": 3,
            "level": 0,
            "maxLevel": 0,
            "remainingDuration": 3,
            "removed": false,
            "removable": true,
            "resistible": true
        }
    ]
}
     */
    Object.values(chargeAttacks).forEach(chargeAttack => {
        let iconHtmls = chargeAttack.statusEffects.length > 0
            ? chargeAttack.statusEffects.map(statusEffect => $('<img class="status-icon">').attr('src', statusEffect.iconSrc).get(0).outerHTML).join('\n')
            : '';

        $(`.ability-panel.actor-${chargeAttack.actorIndex} .charge-attack-wrapper .move-popover-label.charge-attack`).remove();

        let $popoverLabel = $(`
            <div class="move-popover-label charge-attack">
                <span class="header">오의: </span>
                <span>${chargeAttack.name}</span><i class="ms-1 me-1 bi bi-question-circle"></i>
                ${iconHtmls}
            </div>
        `);

        let $popoverButton = $(`
            <button class="open-move-popover charge-attack"></button>
        `);
        $popoverButton.attr('data-actor-id', chargeAttack.actorId);
        $popoverButton.attr('data-move-type', chargeAttack.type);
        $popoverButton.attr('data-move-order', 0);

        $popoverLabel.find('.header').after($popoverButton); // 오의: <- 뒤에 버튼 붙임 (위치 고정)
        $popoverLabel.on('click', function (event) {
            event.stopPropagation(); // 라벨로 대신클릭 (width = 1)
            $(this).find('.open-move-popover').click();
        });

        $(`.ability-panel.actor-${chargeAttack.actorIndex} .charge-attack-wrapper`).append($popoverLabel);
    });
}

function renderAllSupportAbilities(supportAbilityObj) {
    // console.debug('[renderAllSupportAbilities] supportAbilityObj = ', supportAbilityObj);

    Object.values(supportAbilityObj).forEach(supportAbilities => {
        if (supportAbilities.length === 0) return;

        let actorIndex = supportAbilities[0].actorIndex;
        $(`.ability-panel.actor-${actorIndex} .support-ability-wrapper .open-move-popover.support-ability`).remove();

        supportAbilities.forEach((supportAbility) => {

            let iconSrc = supportAbility.statusEffects.length > 0 ? supportAbility.statusEffects[0].iconSrc : '';
            let $popoverButton = $(`
            <button class="btn btn-xxsm btn-outline-light open-move-popover support-ability support-ability-${supportAbility.order}">
              <span>서포트 ${supportAbility.order}</span>
              <img class="status-icon" src=${iconSrc}>
            </button>
        `);
            $popoverButton.attr('data-actor-id', supportAbility.actorId);
            $popoverButton.attr('data-move-type', supportAbility.type);
            $popoverButton.attr('data-move-order', supportAbility.order);
            $(`.ability-panel.actor-${supportAbility.actorIndex} .support-ability-wrapper`).append($popoverButton);
        });
    })
}

/**
 * 전체 소환석 초기 렌더링
 * @param {Object} summons - summon 객체 (id를 key로)
 */
function renderAllSummons(summons) {
    // console.debug('[renderAllSummons] summons = ', summons);
    const $summonList = $('#partyCommandContainer .summon-display-wrapper .summon-list');
    $summonList.empty();

    // order 순 정렬
    const leaderSummons = Object.values(summons).sort((a, b) => a.order - b.order);

    // 소환석 렌더링
    leaderSummons.forEach(summon => {
        const $summonElement = createSummonElement(summon);
        $summonList.append($summonElement);
    });

    // 더미 추가
    const emptyCount = 5 - leaderSummons.length;
    for (let i = 0; i < emptyCount; i++) {
        $summonList.append(`
            <div class="summon-list-item empty">
                <img src="/assets/img/summon/empty.jpg">
            </div>
        `);
    }
}

/**
 * 소환석 DOM 생성
 * @param {Object} summon - 소환석 객체
 * @returns {jQuery} 소환석 엘리먼트
 */
function createSummonElement(summon) {
    const cooldownText = summon.cooldown > 999 ? '재사용 불가'
        : summon.cooldown > 0 ? summon.cooldown + '턴' : '';
    const cooldownClass = summon.cooldown > 0 ? '' : 'invisible';
    const overlayClass = summon.cooldown > 999 ? 'none-usable'
        : summon.cooldown > 0 ? 'not-ready' : '';

    const $summon = $(`
        <div class="summon-list-item"
             data-move-id="${summon.id}"
             data-order="${summon.order}"
             >
            <img src="${summon.portraitImageSrc || '/assets/img/summon/empty.jpg'}">
            <div class="summon-cooldown ${cooldownClass}">${cooldownText}</div>
            <div class="summon-overlay command-overlay ${overlayClass}" data-move-id="${summon.id}"></div>
        </div>
    `);

    // 클릭 이벤트
    $summon.on('click', function () {
        let summon = gameStateManager.getState(`summon.${$(this).attr('data-move-id')}`);
        openCommandInfoModal(summon);
    });

    return $summon;
}