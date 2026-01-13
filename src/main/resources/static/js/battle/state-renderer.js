function renderPotionCount(newVal, oldVal) {
    let potionCounts = newVal;
    let $potionWrappers = $('.potion-icon-wrapper');
    potionCounts.forEach(function (count, index) {
        $potionWrappers.eq(index).find('.count').text(count);
        if (count <= 0) $potionWrappers.find('.potion-overlay').addClass('not-ready');
    })
}

function renderHp(newVal, oldVal) {
    console.log('[renderHp] newVal = ', newVal, ' oldVal = ', oldVal);
    let hps = newVal;
    hps.forEach((hp, actorIndex) => {
        if (actorIndex === 0) return; // 적 스킵
        let oldHp = oldVal[actorIndex];
        let hpDiff = hp - oldHp;
        if (hpDiff === 0) return;

        // 1000ms 간 25ms 마다 렌더링한다고 가정 (총 40회 갱신)
        let intervalHp = hpDiff / 40 - 1;
        let $portraitHpGaugeValue = $(`.battle-portrait.actor-${actorIndex} .hp-gauge .value`);
        let $abilityPanelHpGaugeValue = $(`.ability-panel.actor-${actorIndex} .hp-gauge .value`);
        let currentHp = oldHp;
        let intervalCount = 0;

        let hpInterval = window.setInterval(() => {
            currentHp += intervalHp;
            intervalCount++;
            $portraitHpGaugeValue.text(Math.floor(currentHp));
            $abilityPanelHpGaugeValue.text(Math.floor(currentHp));
            if (intervalCount >= 40) {
                window.clearInterval(hpInterval);
                $portraitHpGaugeValue.text(hp);
                $abilityPanelHpGaugeValue.text(hp);
            }
        }, 25);
    });
}

function renderHpRate(newVal, oldVal) {
    console.log('[renderHpRate] newVal = ', newVal, ' oldVal = ', oldVal);
    let hpRates = newVal;
    hpRates.forEach((hpRate, actorIndex) => {
        if (hpRate === oldVal[actorIndex]) return;
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

function renderEnemyTriggerHps(newVal, oldVal) {
    console.log('[renderEnemyTriggerHps] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.log('[renderEnemyMaxChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.log('[renderChargeGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    let chargeGauges = newVal;
    chargeGauges.forEach((chargeGauge, actorIndex) => {
        if (actorIndex === 0) {
            $('.charge-turn-container.enemy .charge-turn').removeClass('on').each(function (index, element) {
                if (chargeGauge > index) $(element).addClass('on');
            });
        } else {
            $(`.battle-portrait.actor-${actorIndex} .charge-gauge`)
                .find('.value').text(chargeGauge).end()
                .find('.progress-bar').css('width', chargeGauge + '%');
            $(`.ability-panel.actor-${actorIndex} .charge-gauge`)
                .find('.value').text(chargeGauge).end()
                .find('.progress-bar').css('width', chargeGauge + '%');
        }
    })
}


function renderFatalChainGauge(newVal, oldVal) {
    console.log('[renderFatalChainGauge] newVal = ', newVal, ' oldVal = ', oldVal);
    let fatalChainGauge = newVal;
    $('.fatal-chain-gauge-value').find('.value').text(fatalChainGauge);
    $('.fatal-chain-gauge .progress-bar').css('width', fatalChainGauge + '%');
}

function renderAbilityCoolDowns(newVal, oldVal) {
    console.log('[renderAbilityCoolDowns] newVal = ', newVal, ' oldVal = ', oldVal);
    newVal.forEach(function (abilityCooldowns, actorIndex) {
        let $abilityPanels = $(`#abilitySlider .ability-panel.actor-${actorIndex}`); // slick-cloned 까지 전부 렌더링해야 스와이프할때 자연스러움
        abilityCooldowns.forEach(function (cooldown, index) {
            $abilityPanels.get().forEach(abilityPanel => {
                let $abilityIcons = $(abilityPanel).find('.ability-icon');
                let $abilityIcon = $abilityIcons.eq(index);
                if (!$abilityIcon) return;
                let $abilityCooldownText = $abilityIcon.find('.ability-cooldown-text');
                let $abilityOverlay = $abilityIcon.find('.ability-overlay');
                if ($abilityOverlay.hasClass('none-usable')) return; // 재사용 불가 or 봉인 시 쿨다운과 상관없이 사용불가

                if (cooldown > 999) {
                    $abilityCooldownText.text('재사용 불가');
                    $abilityCooldownText.removeClass('invisible');
                    $abilityOverlay.addClass('none-usable');
                } else if (cooldown > 0) {
                    $abilityCooldownText.text(cooldown + '턴');
                    $abilityCooldownText.removeClass('invisible');
                    $abilityOverlay.addClass('not-ready');
                } else {
                    $abilityOverlay.removeClass('not-ready none-usable');
                    $abilityCooldownText.addClass('invisible');
                }
            });
        })
    });
    if ($('#abilityRail .rail-item').length === 0) {
        $('.command-overlay').removeClass('on-rail'); // fallback
    }
}

function renderAbilityUsableIndicator(newVal, oldVal) {
    console.log('[renderAbilityUsableIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    newVal.forEach(function (abilityCooldowns, actorIndex) {
        if (actorIndex === 0) return; // 적 스킵

        let $abilityUsableIndicators = $(`.battle-member-wrapper .battle-portrait.actor-${actorIndex} .ability-usable-indicator-wrapper .ability-usable-indicator`);
        let abilityInfos = gameStateManager.getState(`abilityByActor.${actorIndex}`);

        // abilityType 에 맞는 클래스 추가 (없으면 사용불가)
        abilityCooldowns.forEach(function (abilityCooldown, index) {
            let abilityInfo = abilityInfos[index];
            let $abilityIndicator = $abilityUsableIndicators.eq(index);
            if (!abilityInfo) {
                $abilityIndicator.addClass('none');
                return; // 어빌리티 없음
            }
            let abilitySealed = gameStateManager.getState(`abilitySealeds.${actorIndex}.${index}`);
            let abilityType = abilityInfo.additionalType.toLowerCase();
            if (abilityCooldown <= 0 && !abilitySealed) {
                $abilityIndicator.addClass(abilityType);
            } else {
                $abilityIndicator.removeClass(abilityType);
            }
        })
    })
}

function renderAbilitySealeds(newVal, oldVal) {
    console.log('[renderAbilitySealeds] newVal = ', newVal, ' oldVal = ', oldVal);
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
            })
        })

    })
}

function renderSummonCooldowns(newVal, oldVal) {
    console.log('[renderSummonCooldowns] newVal = ', newVal, ' oldVal = ', oldVal);
    let $summons = $('#partyCommandContainer .summon-display-wrapper .summon-list-item:not(.empty)');
    newVal.forEach(function (cooldown, index) {
        let $summon = $summons.eq(index);
        if (!$summon) return;
        let $summonCooldown = $summon.find('.summon-cooldown');
        let $summonOverlay = $summon.find('.summon-overlay');

        if (cooldown > 999) {
            $summonCooldown.text('재사용 불가');
            $summonCooldown.removeClass('invisible');
            $summonOverlay.addClass('none-usable');
        } else if (cooldown > 0) {
            $summonCooldown.text(cooldown + '턴');
            $summonCooldown.removeClass('invisible');
            $summonOverlay.addClass('not-ready');
        } else {
            $summonCooldown.addClass('invisible');
            $summonOverlay.removeClass('not-ready none-usable');
        }
    });
}

function renderUnionSummonChance(newVal, oldVal) {
    console.log('[renderUnionSummonChance newVal = ', newVal, ' oldVal = ', oldVal);
    let unionSummonId = newVal;
    if (unionSummonId === null) return;

    let $wrapper = $('.union-summon-chance-wrapper');
    let $imgs = $wrapper.find('img');
    let $portrait = $imgs.eq(0);
    $portrait.attr('src', Constants.summon[unionSummonId].portraitSrc);

    $wrapper.css('display', 'block');
    setTimeout(() => $wrapper.css('display', 'none'), 3000);
}

function renderSummonButton(newVal, oldVal) {
    console.log('[renderSummonButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let leaderId = newVal;
    if (leaderId === null) {
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').addClass('not-ready');
    } else {
        $('#partyCommandContainer .summon-button-wrapper .summon-button-overlay').removeClass('not-ready');
    }
}

function renderCurrentStatusEffectsIcons(newVal, oldVal) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    newVal.forEach(function (currentStatusEffects, actorIndex) {
        let $statusContainer = $('.status-container.actor-' + actorIndex);
        let $fragment = $('<div>');
        currentStatusEffects.forEach(function (status, index) {
            let beforeStatus = currentStatusEffects[index - 1];
            // 어빌리티 패널에 갱신된 스테이터스 추가
            let displayClassName = index > 0 && (beforeStatus.name === status.name || beforeStatus.imageSrc === status.imageSrc) ? 'd-none' : ''; // 이전과 이름이나 아이콘이 같다면, 안보이게 설정
            let $statusInfo = $(`
                <div class="status ${displayClassName}" data-status-type="${status.type}">
                  <img src="${status.imageSrc}" class="status-icon${status.imageSrc.length < 1 ? ' none-icon' : ''}" alt="${status.name} icon">
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
    console.log('[renderMoveNameIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let moveName = newVal;
    $('.move-name-info-container')
        .find('.move-name-info-text').text(moveName).end()
        .fadeIn(100).delay(800).fadeOut(100);
}

function renderMoveResultHonorIndicator(newVal, oldVal) {
    console.log('[renderMoveResultHonorIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let honor = newVal;
    if (honor === 0) return;
    $('.honor-container')
        .find('.honor-value').text(honor).end()
        .animate({left: '0px'}, 50).delay(1500).animate({left: '-40%'}, 100);
}

function renderOmen(newVal, oldVal) {
    console.log('[renderOmen] newVal = ', newVal, ' oldVal = ', oldVal); // {OmenDto} stage.gGameStatus.omen
    let omen = newVal;
    let omenValueChanged = oldVal && omen.remainValue !== oldVal.remainValue;
    let isImpossibleCancelOmenValue = omen.cancelCondition && !!omen.cancelCondition.includes('불가'); // 해제불가시 remainValue 렌더링 안함
    if (!omen.type) { // 전조 해제
        // 전조 컨테이너 deactivate
        $('.omen-container-top').removeClass('activated');
        $('.omen-container-bottom.enemy').removeClass('activated');
        // CT 턴 액티브 해제
        $('.charge-turn-container.enemy .charge-turn').removeClass('active');
    } else { // 전조 발동 또는 진행중
        $('.omen-container-top')
            .addClass('activated')
            .html($(
                `<div class="omen-text ${omen.type.className}">
                  <span class="omen-prefix">${omen.cancelCondition}</span>
                  <span class="omen-value">${isImpossibleCancelOmenValue ? '' : omen.remainValue}</span>
                  <span class="omen-info">${omen.info}</span>
                </div>`
            ));
        if (omenValueChanged) {
            $('.omen-container-top .omen-value').css('color', 'white').animate({opacity: 1}, 300, function () {
                $(this).css('color', 'black')
            });
        }
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
    }
}

function renderGuards(newVal, oldVal) {
    console.log('[renderGuards] newVal = ', newVal, ' oldVal = ', oldVal);
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
    console.log('[renderAttackButton] newVal = ', newVal, ' oldVal = ', oldVal);
    let isAttackClicked = gameStateManager.getState('isAttackClicked');
    let isQuestCleared = gameStateManager.getState('isQuestCleared');
    let isQuestFailed = gameStateManager.getState('isQuestFailed');

    if (isQuestCleared) {
        $('#attackButtonWrapper img')
            .attr('src', '/static/assets/img/ui/ui-next.png')
            .css({'left': '10%', 'width': '100%'})
    } else if (isQuestFailed) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-rejoin.png');
    } else if (isAttackClicked) {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack-cancel.png');
    } else {
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack.png');
    }
}

function renderMemberInfoContainer(newVal, oldVal) {
    console.log('[renderMemberInfoContainer] newVal = ', newVal, ' oldVal = ', oldVal);
    let memberInfos = newVal;
    $('.member-info-container').empty();
    let $memberInfoWrappers = [];
    memberInfos.forEach(function (memberInfo, index) {
        let $memberInfoWrapper = $(`
          <div class="member-info-wrapper">
            <div class="element-type ${memberInfo.leaderActorElementType.toLowerCase()}"></div>
            <div class="member-username">
            ${memberInfo.username}
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
    $('.member-info-container').append(...$memberInfoWrappers);
}

function renderTurnIndicator(newVal, oldVal) {
    console.log('[renderTurnIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let currentTurn = newVal;
    $('.turn-indicator .value').text(currentTurn); // topMenu + battleCanvas
    $('#battleCanvas .turn-indicator-container').addClass('show').on('transitionend', function () {
        setTimeout(() => $(this).removeClass('show'), 1500)
    });
}

function renderRemainingTimeIndicator(newVal, oldVal) {
    // console.log('[renderRemainingTimeIndicator] newVal = ', newVal, ' oldVal = ', oldVal);
    let remainingTime = newVal;
    $('.remaining-time-indicator .value').text(remainingTime);
}