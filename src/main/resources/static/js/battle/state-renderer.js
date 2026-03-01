function renderPotionCount(newVal, oldVal) {
    let potionCounts = newVal;
    let $potionWrappers = $('.potion-icon-wrapper');
    potionCounts.forEach(function (count, index) {
        let $potionWrapper = $potionWrappers.eq(index);
        $potionWrapper.find('.count').text(count);
        if (count <= 0) $potionWrapper.find('.potion-overlay').addClass('not-ready');
    })
}

window.renderHpInterval = [null, null, null, null, null];

function renderHp(newVal, oldVal) {
    console.log('[renderHp] newVal = ', newVal, ' oldVal = ', oldVal);
    let hps = newVal;
    hps.forEach((hp, actorIndex) => {
        if (actorIndex === 0) return; // 적 스킵

        let $portraitHpGaugeValue = $(`.battle-portrait.actor-${actorIndex} .hp-gauge-value .value`);
        let $abilityPanelHpGaugeValue = $(`.ability-panel.actor-${actorIndex} .hp-gauge-value .value`);

        let oldHp = oldVal[actorIndex];
        let hpDiff = hp - oldHp;
        if (hpDiff === 0) {
            $portraitHpGaugeValue.text(hp);
            $abilityPanelHpGaugeValue.text(hp);
            return;
        }

        // 1000ms 간 25ms 마다 렌더링한다고 가정 (총 40회 갱신)
        let intervalHp = hpDiff / 40 - 1;
        let currentHp = oldHp;
        let intervalCount = 0;

        window.clearInterval(window.renderHpInterval[actorIndex]);
        window.renderHpInterval[actorIndex] = window.setInterval(() => {
            currentHp += intervalHp;
            intervalCount++;
            $portraitHpGaugeValue.text(Math.floor(currentHp));
            $abilityPanelHpGaugeValue.text(Math.floor(currentHp));
            if (intervalCount >= 40) {
                window.clearInterval(window.renderHpInterval[actorIndex]);
                window.renderHpInterval[actorIndex] = null;

                $portraitHpGaugeValue.text(hp);
                $abilityPanelHpGaugeValue.text(hp);
            }
        }, 25);
    });
}

function renderHpRate(newVal, oldVal) {
    console.debug('[renderHpRate] newVal = ', newVal, ' oldVal = ', oldVal);
    let hpRates = newVal;
    hpRates.forEach((hpRate, actorIndex) => {
        // 적
        if (actorIndex === 0) {
            $('.enemy-info-container .hp-container .value-hp').text(hpRate + '%');
            $('.enemy-info-container .hp-container .progress-bar').css('width', hpRate + '%');
            return;
        }
        // 캐릭터
        let $commandHpBar = $(`.battle-portrait.actor-${actorIndex} .hp-gauge .progress-bar`);
        $commandHpBar.css('width', hpRate + '%');
        let $abilityPanelHpBar = $(`.ability-panel.actor-${actorIndex} .hp-gauge .progress-bar`);
        $abilityPanelHpBar.css('width', hpRate + '%');

        // 캐릭터 - 배경색
        if (hpRate <= 25) { // 빈사상태
            $commandHpBar.addClass('bg-danger');
            $abilityPanelHpBar.addClass('bg-danger');
        } else {
            $commandHpBar.removeClass('bg-danger');
            $abilityPanelHpBar.removeClass('bg-danger');
        }
    });
}

window.renderBarrierInterval = [null, null, null, null, null];

function renderBarriers(newVal, oldVal) {
    console.debug('[renderBarrier] newVal = ', newVal, ' oldVal = ', oldVal);
    let barriers = newVal;
    barriers.forEach((barrier, actorIndex) => {
        if (actorIndex === 0) return;
        let oldBarrier = oldVal[actorIndex];
        if (oldBarrier === 0 && barrier === 0) return;

        let $portraitBarrierValue = $(`.battle-portrait.actor-${actorIndex} .barrier-value .value`);
        let $abilityBarrierValue = $(`.ability-panel.actor-${actorIndex} .barrier-value .value`);

        let barrierDiff = barrier - oldBarrier;
        if (barrierDiff === 0) {
            $portraitBarrierValue.text(barrier);
            $abilityBarrierValue.text(barrier);
            return;
        }

        // 1000ms 간 25ms 마다 렌더링한다고 가정 (총 40회 갱신)
        let intervalValue = barrierDiff / 40 - 1;
        let currentBarrier = oldBarrier;
        let intervalCount = 0;

        window.clearInterval(window.renderBarrierInterval[actorIndex]);
        window.renderBarrierInterval[actorIndex] = window.setInterval(() => {
            currentBarrier += intervalValue;
            intervalCount++;
            $portraitBarrierValue.text(Math.floor(currentBarrier));
            $abilityBarrierValue.text(Math.floor(currentBarrier));
            if (intervalCount >= 40) {
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
    console.debug('[renderEnemyTriggerHps] newVal = ', newVal, ' oldVal = ', oldVal);
    let triggerHps = newVal;
    let currentEnemyHpRate = gameStateManager.getState('hpRates')[0];

    let $hpBar = $('.hp-container.enemy .hp-bar');
    $hpBar.find('.hp-trigger').remove();

    let $hpTrigger = $(`<div class="hp-trigger"></div>`);
    triggerHps.forEach((hpRate, actorIndex) => {
        if (hpRate > currentEnemyHpRate) return;
        $hpBar.append($hpTrigger.clone().css('left', hpRate + '%'));
    })
}

function renderEnemyMaxChargeGauge(newVal, oldVal) {
    console.debug('[renderEnemyMaxChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderFatalChainGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    let fatalChainGauge = newVal;
    $('.fatal-chain-gauge-value').find('.value').text(fatalChainGauge);
    $('.fatal-chain-gauge .progress-bar').css('width', fatalChainGauge + '%');
}

/**
 * 어빌리티 쿨타임 렌더링
 * @param newVal abilityCooldowns 또는 abilitySealeds
 */
function renderAbilityCoolDowns(newVal) {
    console.debug('[renderAbilityCoolDowns] abilities = ', newVal);
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
                const $cooldownTextEl = $abilityIcon.find('.ability-cooldown-text');
                const $abilityOverlay = $abilityIcon.find('.ability-overlay');

                // none-usable이면 쿨다운 무시
                // if ($abilityOverlay.hasClass('none-usable')) return;

                if (cooldown > 999) {
                    // cooldown > 999: 재사용 불가
                    $abilityIcon.attr({
                        // 'data-cooldown': cooldown,
                        // 'data-usable': false
                    });
                    $cooldownTextEl.text('x').css('visibility', 'hidden');
                    $cooldownTextEl.removeClass('invisible');
                    $abilityOverlay.addClass('none-usable');
                } else if (cooldown > 0) {
                    // 쿨다운 중
                    $abilityIcon.attr({
                        // 'data-cooldown': cooldown,
                        // 'data-usable': false
                    });
                    $cooldownTextEl.text(`${cooldown}턴`);
                    $cooldownTextEl.removeClass('invisible');
                    $abilityOverlay.removeClass('none-usable').addClass('not-ready');
                } else {
                    // 사용 가능
                    $abilityIcon.attr({
                        // 'data-cooldown': cooldown,
                        // 'data-usable': true
                    });
                    $cooldownTextEl.addClass('invisible');
                    $cooldownTextEl.text('00'); // 레이아웃 유지
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
    const abilityType = ability.additionalType.toLowerCase();

    if (cooldown <= 0 && !sealed) {
        $indicator.addClass(abilityType);
    } else {
        $indicator.removeClass(abilityType);
    }
}

function renderAbilitySealeds(newVal, oldVal) {
    console.debug('[renderAbilitySealeds] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderSummonCooldowns] newVal =', newVal, 'oldVal =', oldVal);
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
    console.debug('[renderUnionSummonChance newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderSummonButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let leaderActorId = gameStateManager.getState('leaderActorId'); // number or null
    if (leaderActorId) {
        // 주인공이 살아있을때만 소환가능
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').removeClass('not-ready');
    } else {
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').addClass('not-ready');
    }
}

function renderCurrentStatusEffectsIcons(newVal, oldVal) {
    console.debug('[renderCurrentStatusEffects] newVal =', newVal, ' oldVal = ', oldVal);
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    newVal.forEach(function (currentStatusEffects, actorIndex) {
        let $statusContainer = $('.status-container.actor-' + actorIndex);
        let $fragment = $('<div>');
        currentStatusEffects.forEach(function (status, index) {
            let beforeStatus = currentStatusEffects[index - 1];
            // 어빌리티 패널에 갱신된 스테이터스 추가
            let displayClassName = index > 0 && (beforeStatus.name === status.name || beforeStatus.iconSrc === status.iconSrc) ? 'd-none' : ''; // 이전과 이름이나 아이콘이 같다면, 안보이게 설정
            let $statusInfo = $(`
                <div class="status ${displayClassName}" data-status-type="${status.type}">
                  <img src="${status.iconSrc}" class="status-icon${status.iconSrc.length < 1 ? ' none-icon' : ''}" alt="${status.name} icon">
                  <div class="status-name d-none">${status.name}</div>
                  <div class="status-info-text d-none">${status.statusText}</div>
                  <div class="status-duration d-none" data-duration-type="${status.durationType}">${status.remainingDuration}</div>
                </div>`)
            $fragment.append($statusInfo);
        })
        $statusContainer.find('.status').remove(); // 스테이터스 비움
        $statusContainer.append(...$fragment.find('.status'));
    });
}

function renderMoveNameIndicator(newVal, oldVal) {
    console.debug('[renderMoveNameIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let moveName = newVal;
    $('.move-name-info-container')
        .find('.move-name-info-text').text(moveName).end()
        .fadeIn(100).delay(800).fadeOut(100);
}

function renderMoveResultHonorIndicator(newVal, oldVal) {
    console.debug('[renderMoveResultHonorIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let honor = newVal;
    if (honor === 0) return;
    $('.honor-container')
        .find('.honor-value').text(honor).end()
        .animate({left: '0px'}, 50).delay(1500).animate({left: '-40%'}, 100);
}

function renderOmen(newVal, oldVal) {
    console.debug('[renderOmen] newVal = ', newVal, ' oldVal = ', oldVal); // {OmenDto} stage.gGameStatus.omen
    let omen = newVal;
    // 전조 발동 또는 진행중
    if (!omen.isEmpty()) {
        // 상단 컨테이너
        let $cancelConditions = [];
        omen.cancelConditions.forEach((cancelCondition, index) => {
            let isImpossibleCancelCondition = cancelCondition.cancelType === 'IMPOSSIBLE';
            let $omen = $(
                `<div class="omen-text ${omen.type.className}">
                  <span class="omen-prefix">${cancelCondition.info}</span>
                  <span class="omen-value">${isImpossibleCancelCondition ? '' : ' : ' + cancelCondition.remainValue}</span>
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

        // 하단 컨테이너
        $('.omen-container-bottom.enemy')
            .addClass('activated')
            .html($(
                `<div class="omen-text ${omen.type.className}">
                  <span class="omen-prefix">${omen.name}</span>
                </div>`
            ));

        // CT기 CT 턴 액티브
        if (omen.type === OmenType.CHARGE_ATTACK) {
            $('.charge-turn-container.enemy .charge-turn').addClass('active');
        }

    } else {
        // 전조 브레이크 / 해제
        // 전조 컨테이너 deactivate
        $('.omen-container-top').removeClass('activated');
        $('.omen-container-bottom.enemy').removeClass('activated');
        // CT 턴 액티브 해제
        $('.charge-turn-container.enemy .charge-turn').removeClass('active');
    }
}

function renderGuards(newVal, oldVal) {
    console.debug('[renderGuards] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderAttackButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let isAttackClicked = gameStateManager.getState('isAttackClicked');
    let isQuestCleared = gameStateManager.getState('isQuestCleared');
    let isQuestFailed = gameStateManager.getState('isQuestFailed');

    if (isQuestCleared) {
        $('#attackButtonWrapper img')
            .attr('src', '/static/assets/img/ui/ui-next.png')
            .css({'left': '15%', 'width': '100%'})
    } else if (isQuestFailed) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-rejoin.png');
    } else if (isAttackClicked) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack-cancel.png');
    } else {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack.png');
    }
}

function renderMemberInfoContainer(newVal, oldVal) {
    console.debug('[renderMemberInfoContainer] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderChatMessages] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.debug('[renderTurnIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let currentTurn = newVal;
    $('.turn-indicator .value').text(currentTurn); // topMenu + battleCanvas
    $('#battleCanvas .turn-indicator-container').addClass('show').on('transitionend', function () {
        setTimeout(() => $(this).removeClass('show'), 1500)
    });
}

function renderRemainingTimeIndicator(newVal, oldVal) {
    // console.debug('[renderRemainingTimeIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let remainingTime = newVal;
    $('.remaining-time-indicator .value').text(remainingTime);
}

// 초기 렌더링 ===========================================================================================================

/**
 * 전체 어빌리티 초기 렌더링
 */
function renderAllAbilities(abilities) {
    console.debug('[renderAllAbilities] abilities = ', abilities);
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
            const $target = $siblings.filter((index, sibling) => parseInt($(sibling).attr('data-order')) > ability.order).first();
            // order 앞 순서 있으면 뒤에, 없으면 맨 앞에 삽입
            $target.length > 0 ? $target.before($abilityIcon) : $abilityWrapper.append($abilityIcon);
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
    console.debug('[renderAllAbilityIndicators] abilities = ', abilities);

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
            const abilityType = ability.additionalType.toLowerCase();
            if (ability.cooldown <= 0 && !abilitySealed) {
                $indicator.addClass(abilityType);
            } else {
                $indicator.removeClass(abilityType);
            }
        });
    }
}

/**
 * 전체 소환석 초기 렌더링
 * @param {Object} summons - summon 객체 (id를 key로)
 */
function renderAllSummons(summons) {
    console.debug('[renderAllSummons] summons = ', summons);
    const $summonList = $('#partyCommandContainer .summon-display-wrapper .summon-list');
    $summonList.empty();

    // order 순 정렬
    const leaderSummons = Object.values(summons).sort((a, b) => a.order - b.order);

    // 소환석 렌더링
    leaderSummons.forEach(summon => {
        const $summonElement = createSummonElement(summon);
        $summonList.append($summonElement);
    });

    // 더미 추가 (최대 4개까지 빈 칸 채우기)
    const emptyCount = 4 - leaderSummons.length;
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
        if (player.locked) return;
        let summon = gameStateManager.getState(`summon.${$(this).attr('data-move-id')}`);
        openCommandInfoModal(summon);
    });

    return $summon;
}