/**
 * 스테이터스 이펙트를 처리 (힐, 버프, 디버프)
 * @param response
 * @param delay 실행 딜레이
 * @param isAccelerated 가속 여부 (true 로 설정시 가속)
 * @return {Promise<unknown>} totalDuration 실행시간
 */
async function processStatusEffect(response, delay = 0, isAccelerated = false) {
    return new Promise(resolve => setTimeout(async () => {
            processStatusIconSync(response.currentBattleStatusesList);
            processAbilityStatusSync(response.abilityCoolDowns, response.abilityUsables);
            let healEffectDuration = await processHealEffect(response.heals, isAccelerated);
            let buffEffectDuration = await processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, isAccelerated);
            let debuffEffectDuration = await processDebuffEffect(response.addedDebuffStatusesList, isAccelerated);
            console.log('[processStatusEffect] healEffectDuration = ', healEffectDuration, ' buffEffectDuration = ', buffEffectDuration, ' debuffEffectDuration = ', debuffEffectDuration, 'delay = ', delay);
            let totalDuration = healEffectDuration + buffEffectDuration + debuffEffectDuration;
            resolve(totalDuration);
        }, delay)
    );
}

/**
 * 커맨드 패널의 현재 스테이터스 아이콘 갱신 (이펙트 종료 직후)
 * @param currentBattleStatusesList 갱신할 현재 스테이터스리스트
 */
function processStatusIconSync(currentBattleStatusesList) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
        let $statusContainer = $('.status-container.actor-' + actorIndex);
        $statusContainer.find('.status').remove(); // 스테이터스 비움
        let $fragment = $('<div>');
        currentBattleStatuses.forEach(function (status, index) {
            let beforeStatus = currentBattleStatuses[index - 1];
            // 어빌리티 패널에 갱신된 스테이터스 추가
            let displayClassName = index > 0 && (beforeStatus.name === status.name || beforeStatus.imageSrc === status.imageSrc) ? 'd-none' : ''; // 이전과 이름이나 아이콘이 같다면, 안보이게 설정
            let $statusInfo = $(`
                <div class="status ${displayClassName}" data-status-type="${status.type}">
                  <img src="${status.imageSrc}" class="status-icon${status.imageSrc.length < 1 ? ' none-icon' : ''}" alt="${status.name} icon">
                  <div class="status-name d-none">${status.name}</div>
                  <div class="status-info-text d-none">${status.statusText}</div>
                  <div class="status-duration d-none" data-duration-type="${status.durationType}">${status.remainingDuration}</div>
                  </img>
                </div>`)
            $fragment.append($statusInfo);
        })
        $statusContainer.append(...$fragment.find('.status'));
    });
}

function processAbilityStatusSync(abilityCooldownsLists, abilityUsablesLists) {
    // 쿨다운 갱신
    abilityCooldownsLists.forEach(function (abilityCooldowns, actorIndex) {
        let $abilityValues = $(`#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel.actor-${actorIndex} .ability-cooldown-text .value`);
        if ($abilityValues.length === 0) return;
        abilityCooldowns.forEach(function (abilityCooldown, index) {
            $abilityValues?.eq(index)?.text(abilityCooldown);
        })
    })
    // 사용가능여부 갱신
    abilityUsablesLists.forEach(function (abilityUsables, actorIndex) {
        let $abilityOverlays = $(`#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel.actor-${actorIndex} .ability-overlay`);
        if ($abilityOverlays.length === 0) return;
        abilityUsables.forEach(function (abilityUsable, index) {
            if (abilityUsable === false) {
                $abilityOverlays.eq(index)?.addClass('none-usable');
            } else {
                $abilityOverlays.eq(index)?.removeClass('none-usable');
            }
        })
    })

}

async function processHealEffect(healArray, isAccelerated = false) {
    let healEffectDuration = isAccelerated ? 250 : 500; // 실제 heal cjs.timeline 의 길이는 440ms 임.
    let perCharacterDelay = 100 // 캐릭터 별 추가딜레이
    let lastHealEffectEndTime = 0;
    let healDuration = 0; // 힐 처리 최종시간

    let healWrappers = [];
    healArray.forEach(function (heal, actorIndex) {
        // console.log('[processHealEffect] heal = ', heal, ' actorIndex = ', actorIndex);
        if (Number.isInteger(heal) && heal >= 0) { // 0인경우도 있음
            let $damageWrapper = actorIndex === 0
                ? $('<div>', {class: 'damage-wrapper ability'}) // 적은 어빌리티 데미지 래퍼를,
                : $('<div>', {class: 'damage-wrapper enemy actor-' + actorIndex}); // 아군은 적의 데미지 래퍼를 사용
            let startDelay = perCharacterDelay * actorIndex; // 캐릭터 순서대로 딜레이
            lastHealEffectEndTime = startDelay + healEffectDuration;

            setTimeout(async function () {
                player.play(Player.playRequest('actor-' + actorIndex, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: 'HEAL'}))
                // 데미지(힐 수치) 채우기 및 돔추가
                let $healWrapper = $damageWrapper;
                healWrappers.push($healWrapper);
                let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
                let isEnemyDamage = actorIndex !== 0; // 아군인 경우 적의 공격데미지 클래스를 사용하기 위함
                let $damageElements = getDamageElement(0, 'NONE', 'attack', 'normal', 0, heal, [], isEnemyDamage);
                let className = heal >= 0 ? 'heal heal-show' : 'enemy-damage-show';
                $healWrapper.append($damageElements.$damage.addClass(className)).appendTo($actorContainer);
                setTimeout(() => {
                    // 캐릭터 대기모션 갱신 (빈사 -> 일반)
                    if (actorIndex > 0) player.play(Player.playRequest('actor-' + actorIndex, Player.getCharacterWaitMotion(actorIndex)));
                }, healEffectDuration / 2) // 이펙트 도중 갱신
            }, startDelay);
        }
    });
    // 삭제
    setTimeout(() => healWrappers.forEach((healWrapper) => $(healWrapper).remove()), lastHealEffectEndTime + Constants.Delay.damageShowDelete);

    healDuration += lastHealEffectEndTime; // 이전딜레이 + 이펙트딜레이
    console.log('[processHealEffect] healDuration = ', healDuration);
    return new Promise(resolve => setTimeout(() => resolve(healDuration), healDuration));
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @return lastBuffFadeoutStartTime 메서드 대기시간 (마지막 버프 이펙트 페이드아웃 시작까지의 길이)
 */
async function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, isAccelerated = false) {
    let lastBuffFadeoutStartTime = 0;
    let buffShowDuration = 1000; // 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms

    let buffCounts = [];
    let statusesList = addedBuffStatusesList.map((_, idx) => {
        let removedDebuffs = (removedDebuffStatusesList[idx] || []).map(debuff => ({
            value: debuff,
            type: 'removedDebuffs'
        }));
        // 버프는 같은 스테이터스가 여러개로 나뉘는 경우가 존재하므로 표시할때 중복제거
        let addedBuffes = (addedBuffStatusesList[idx] || []).map(buff => ({value: buff, type: 'addedBuffs'}));
        addedBuffes = [...new Map(addedBuffes.map(statusObject => [statusObject.value.name, statusObject])).values()];
        let removedBuffes = (removedBuffStatusesList[idx] || []).map(buff => ({value: buff, type: 'removedBuffs'}));
        removedBuffes = [...new Map(removedBuffes.map(statusObject => [statusObject.value.name, statusObject])).values()];
        let resultBuffes = [...removedDebuffs, ...addedBuffes, ...removedBuffes];
        buffCounts.push(resultBuffes.length);
        return resultBuffes;
    }); // 표시 순서 removedDebuff -> addedBuff -> removedBuff
    // console.log('[processBuffEffect] statusesList = {}', statusesList);

    let partyBuffCounts = buffCounts.slice(1, buffCounts.length).reduce((acc, count) => acc + count, 0);
    console.log('[processBuffEffect] partyBuffCounts = ', partyBuffCounts);

    statusesList.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        if (statuses.length === 0) return;

        let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
        let currentEffectContainerIndex = $actorContainer.find('.status-effect-wrapper').length;

        let $statusEffectWrapper = $('<div>', {class: 'status-effect-wrapper actor-' + actorIndex + ' status-index-' + currentEffectContainerIndex});
        $statusEffectWrapper.addClass(actorIndex === 0 ? 'enemy' : 'party');

        statuses.forEach(function (statusObject, statusIndex) {
            let status = statusObject.value;

            let statusTypeName = status.type.toLowerCase();
            statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(status.name) ? 'none' : statusTypeName;
            let iconClassName = status.imageSrc.length < 1 ? 'none-icon' : '';
            let statusRemovedClassName = statusObject.type === 'removedBuffs' || statusObject.type === 'removedDebuffs' ? 'status-removed' : '';
            let $statusEffect = $(`
              <div class="status-effect status-effect-${statusIndex} ${statusTypeName} ${statusRemovedClassName}">
                <img src="${status.imageSrc}" class="${iconClassName}">
                <span class="status-effect-text">${status.effectText}</span>
              </div>
            `);
            $statusEffectWrapper.append($statusEffect);

            let enemyDelay = actorIndex === 0 && partyBuffCounts > 0 ? 500 : 0; // 적의경우 전체 딜레이을 500ms 만큼 늦춤
            let statusForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let statusPageCount = Math.floor(statusIndex / statusForPage); // 현재 표시할 스테이터스의 페이지 (0 부터)
            let startDelay = enemyDelay + buffShowDuration * statusPageCount + (50 * (statusIndex % statusForPage)); // 이펙트당 buffShowDuration 만큼 보여줌
            lastBuffFadeoutStartTime = startDelay + 800; // 가장 늦게 시작하는 버프가 fadeout 하는 시점

            setTimeout(() => {
                let maxOpacity = 1;
                if ($statusEffect.is('.status-removed')) { // 스테이터스 제거 효과
                    player.play(Player.playRequest(`actor-${actorIndex}`, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: BASE_ABILITY.DISPEL}));
                    maxOpacity = 0.7;
                }

                // 스테이터스 이펙트표시
                $statusEffect.fadeTo(50, maxOpacity).delay(750).fadeTo(200, 0); // CHECK buffShowDuration 과 맞춰야함

                if (statusIndex % statusForPage + 1 === statusForPage) // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, statusIndex % statusForPage + 1).remove(), 1100);
                if (statusIndex >= statuses.length - 1) // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), buffShowDuration);

            }, startDelay);
        });

        $actorContainer.append($statusEffectWrapper);
    });

    console.log('[processBuffEffect] lastBuffFadeoutStartTime = ', lastBuffFadeoutStartTime);
    // 마지막 버프 이펙트의 페이드아웃이 시작되면 다음 작업으로
    return new Promise(resolve => setTimeout(() => resolve(lastBuffFadeoutStartTime), lastBuffFadeoutStartTime));
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @returns lastDebuffFadeoutStartTime 메서드 대기시간 (마지막 디버프 이펙트 페이드아웃 시작까지의 길이)
 */
async function processDebuffEffect(addedDebuffStatusesList, isAccelerated = false) {
    let lastDebuffFadeoutStartTime = 0; // 디버프 없을댄 이전 딜레이 (이펙트 + 버프 딜레이) 만큼
    let debuffShowDuration = 1000; // 디버프 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms

    let partyDebuffCount = addedDebuffStatusesList.slice(1, addedDebuffStatusesList.length).reduce((acc, debuffStatuses) => acc + debuffStatuses.length, 0);
    console.log('[processDebuffEffect] partyDebuffCount = ', partyDebuffCount);

    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [적][아][아][아][아]
        if (addedDebuffStatuses.length === 0) return;

        let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
        let currentEffectContainerIndex = $actorContainer.find('.status-effect-wrapper').length;
        let $statusEffectWrapper = $('<div>', {class: 'status-effect-wrapper actor-' + actorIndex + ' status-index-' + currentEffectContainerIndex});
        $statusEffectWrapper.addClass(actorIndex === 0 ? 'enemy' : 'party');

        addedDebuffStatuses.forEach(function (status, debuffIndex) {
            let statusTypeName = status.type.toLowerCase();
            statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(status.name) ? 'none' : statusTypeName;
            let iconClassName = status.imageSrc.length < 1 ? 'none-icon' : '';
            let $statusEffect = $(`
              <div class="status-effect status-effect-${debuffIndex} ${statusTypeName} ">
                <img src="${status.imageSrc}" class="${iconClassName}">
                <span class="status-effect-text">${status.effectText}</span>
              </div>
            `);
            $statusEffectWrapper.append($statusEffect);

            let enemyDelay = actorIndex === 0 && partyDebuffCount > 0 ? 500 : 0; // 적의경우 전체 딜레이을 500ms 만큼 늦춤
            let debuffForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 디버프 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let debuffPageCount = Math.floor(debuffIndex / debuffForPage); // 현재 표시할 디버프의 페이지 (0 부터)
            let startDelay = enemyDelay + debuffShowDuration * debuffPageCount + (50 * (debuffIndex % debuffForPage)); // 이펙트당 debuffShowDuration 만큼 보여줌
            lastDebuffFadeoutStartTime = startDelay + 800; // 마지막 디버프가 페이드아웃 시작하는 시점

            setTimeout(() => {
                $statusEffect.fadeTo(50, 1).delay(750).fadeTo(200, 0); // CHECK debuffShowDuration 이랑 맞춰야함

                if (debuffIndex % debuffForPage + 1 === debuffForPage)  // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, debuffIndex % debuffForPage + 1).remove(), 1100);
                if (debuffIndex >= addedDebuffStatuses.length - 1) // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), debuffShowDuration);

            }, startDelay);
        });

        $statusEffectWrapper.appendTo($actorContainer);
    });

    console.log('[processDebuffEffect] lastDebuffFadeoutStartTime = ', lastDebuffFadeoutStartTime);
    return new Promise(resolve => setTimeout(() => resolve(lastDebuffFadeoutStartTime), lastDebuffFadeoutStartTime));
}

/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param charOrder
 * @param elementType
 * @param moveType 행동 타입, 'attack', 'ability', chargeAttack'
 * @param damageType 데미지 타입 'NORMAL', 'ADVANTAGE', ....
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemyDamage optional boolean
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(charOrder, elementType, moveType, damageType, index, damage, additionalDamages, isEnemyDamage = false) {
    let typeClassName;
    switch (moveType) {
        case 'attack':
            typeClassName = ' attack-damage';
            break;
        case 'ability':
            typeClassName = ' ability-damage';
            break;
        case 'charge-attack':
            typeClassName = ' charge-attack-damage';
            break;
        default:
            new Error('[getDamageElement] invalid type, type = ' + moveType);
    }

    let damageTypeClassname = ' ' + damageType?.toLowerCase();
    let actorClassName = ' actor-' + charOrder;
    let elementClassName = ' element-type-' + elementType.toLowerCase();
    let damageIndexClassName = ' damage-index-' + index;
    let enemyClassName = isEnemyDamage ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';

    // 데미지 요소
    let $damage = $('<div>', {
        class: typeClassName + actorClassName + elementClassName + damageIndexClassName + elementClassName + missClassName + enemyClassName + damageTypeClassname,
        text: damage
    });
    // 추격 요소
    let $additionalDamage = $('<div>', {
        class: 'additional-damage-wrapper ' + enemyClassName + actorClassName + elementClassName + missClassName + damageTypeClassname,
        text: additionalDamages
    }).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $('<div>', {
            class: 'additional-damage' + elementClassName + enemyClassName,
            text: additionalDamage
        })
    ));
    return {$damage, $additionalDamage};
}

function openStatusWrapperInfo($statusContainer) {
    // console.log('[openStatusWrapperInfo] statusContainer = ', $statusContainer);
    let $statusInfoModal = $('#statusInfoModal .status-info-wrapper');
    $statusInfoModal.children().remove();
    let isEnemy = $statusContainer.is('.enemy');
    let isOmenActivated = $('.omen-container').is('.activated');
    if (isEnemy && isOmenActivated) { // 적이고, 전조 있으면 전조정보 표시
        let omenInfoText = $('.omen-container .omen-info').text().replace(/\\n/g, '<br><b>특수기 효과 : </b>'); // 전조효과 : ㅁㄴㅇㄹ (\n) 특수기효과 : ㅁㄴㅇㄹ
        let omenTypeClassName = $('.omen-container .omen-text').attr('class').split(' ')[1];
        let $omenInfo = $(`
          <div class="status-info omen ${omenTypeClassName}">
            <b>전조 효과 : </b>
            ${omenInfoText} 
          </div>
          <hr>
        `);
        $statusInfoModal.append($omenInfo);
    }
    $statusContainer.find('.status').each(function (index, status) {
        let $status = $(status);
        let iconSrc = $status.find('.status-icon').attr('src');
        let statusInfoText = $status.find('.status-info-text').text();
        let statusName = $status.find('.status-name').text();
        let statusType =
            $status.data('status-type') === 'BUFF' || $status.data('status-type') === 'DISPEL_GUARD'
                ? 'status-buff'
                : 'status-debuff';
        let $statusDuration = $status.find('.status-duration');
        let remainingDuration = $statusDuration.text();
        let $statusInfo = $(`
            <div class="status-info ${statusType}">
              <div class="status-info-icon-wrapper">
                <img class="status-info-icon" src="${iconSrc}">
              </div>
              <div class="status-info-text">
                <span class="status-name fw-bold">${statusName}</span>
                <span class="status-info-duration fw-bold">[ 남은시간: ${remainingDuration} ]</span>
                <br>
                ${statusInfoText}
              </div>
            </div>
            <hr>
        `)
        $statusInfoModal.append($statusInfo);
    })
    $('.status-modal-button').click();
}

function cancelAttack() {
    $('#abilityRail .rail-item-attack').remove(); // 레일에서 삭제
    $('#attackButtonWrapper').removeClass('cancel');
    $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack.png');
    player.lockPlayer(false); // 공격 취소시 락 해제
    effectAudioPlayer.loadAndPlay(GlobalSrc.CANCEL_ATTACK.audio);
}

/**
 *  sync 인터벌
 * @return {number} timerId
 */
function doSync() {
    // return window.setInterval(function () {
    //     requestSync();
    // }, 5000);
}