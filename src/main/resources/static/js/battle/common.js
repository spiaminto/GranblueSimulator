
/**
 * 커맨드 패널의 현재 스테이터스 아이콘 갱신 (이펙트 종료 직후)
 * @param currentBattleStatusesList 갱신할 현재 스테이터스리스트
 * @param startDelay 시작 딜레이 (일반적으로 앞의 이펙트 길이)
 */
function processStatusIconSync(currentBattleStatusesList, startDelay) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    setTimeout(function () {
        currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
            let $statusContainer = $('.status-container.actor-' + actorIndex);
            $statusContainer.find('.status').remove(); // 스테이터스 비움
            let $fragment = $('<div>');
            currentBattleStatuses.forEach(function (status, index) {
                let beforeStatus = currentBattleStatuses[index - 1];
                // 어빌리티 패널에 갱신된 스테이터스 추가
                let displayClassName = index > 0 && (beforeStatus.name === status.name || beforeStatus.imageSrc === status.imageSrc) ? 'd-none' : ''; // 이전과 이름이나 아이콘이 같다면, 안보이게 설정
                let $statusInfo = $('<div>', {
                    class: 'status status',
                    'data-status-type': status.type
                }).addClass(displayClassName)
                    .append($('<img>', {
                            src: status.imageSrc,
                            class: 'status-icon' + (status.imageSrc.length < 1 ? ' none-icon' : ''),
                            alt: status.name + ' icon'
                        }),
                        $('<div>', {class: 'status-name d-none', text: status.name}),
                        $('<div>', {class: 'status-info-text d-none', text: status.statusText}),
                        $('<div>', {class: 'status-duration d-none', text: status.duration}));
                $fragment.append($statusInfo);
            })
            $statusContainer.append(...$fragment.find('.status'));
        });
    }, startDelay);
}

function processHealEffect(healArray, effectMotionDuration) {
    let healDelay = effectMotionDuration;
    if (healArray.length === 0 || healArray.reduce((a, b) => a + b, 0) === 0) return healDelay;
    // let audioPlayer = new AudioPlayer().init();
    let healEffectDuration = 500;

    let $enemyDamageWrappers = new Map(); // 적의 데미지 컨테이너에 힐 표시 래퍼를 추가하여 사용
    let healWrappers = [];
    let lastStartDelay = 0;
    healArray.forEach(function (heal, actorIndex) {
        // console.log('[processHealEffect] heal = ', heal, ' actorIndex = ', actorIndex);
        if (heal !== 0) {
            let $damageWrapper = actorIndex === 0
                ? $('<div>', {class: 'damage-wrapper ability'}) // 적은 어빌리티 데미지 래퍼를,
                : $('<div>', {class: 'damage-wrapper enemy actor-' + actorIndex}); // 아군은 적의 데미지 래퍼를 사용
            let startDelay = effectMotionDuration + (100 * actorIndex); // 캐릭터 순서대로 100씩 딜레이
            lastStartDelay = startDelay;

            setTimeout(function () {
                player.playMotions(Player.playRequest('actor-' + actorIndex, [Player.c_animations.ABILITY_MOTION_EMPTY], null, 'HEAL'))
                // 데미지(힐 수치) 채우기 및 돔추가
                let $healWrapper = $damageWrapper;
                healWrappers.push($healWrapper);
                let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
                let isEnemyDamage = actorIndex !== 0; // 아군인 경우 적의 공격데미지 클래스를 사용하기 위함
                let $damageElements = getDamageElement(0, 'NONE', 'attack', 0, heal, [], isEnemyDamage);
                $healWrapper.append($damageElements.$damage.addClass('heal heal-show')).appendTo($actorContainer);
            }, startDelay);
        }
    });
    // 삭제
    setTimeout(() => healWrappers.forEach((healWrapper) => $(healWrapper).remove()), lastStartDelay + Constants.Delay.damageShowDelete);

    healDelay = lastStartDelay + healEffectDuration;
    return healDelay;
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @param effectVideoDuration 이펙트 비디오 길이 (시작 전 딜레이)
 * @returns longestBuffEndTime 가장 긴 버프 끝시간 (다음 시작 딜레이로 사용)
 */
function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, effectVideoDuration) {
    let lastBuffFadeoutStartTime = effectVideoDuration; // 버프 없을땐 이전 딜레이 (이펙트 딜레이) 만큼
    let buffShowDuration = 1000; // 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms
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
            return [...removedDebuffs, ...addedBuffes, ...removedBuffes];
        }
    );
    // console.log('[processBuffEffect] statusesList = {}', statusesList);
    // 표시 순서 removedDebuff -> addedBuff -> removedBuff
    let audioPlayer = new AudioPlayer().init();
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
            let $statusEffect = $('<div>', {class: `status-effect status-effect-index-${statusIndex} ${statusTypeName}`})
                .append(
                    $('<img>', {src: status.imageSrc, class: status.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: status.effectText})
                );
            $statusEffectWrapper.append($statusEffect);

            let type = statusObject.type;
            let $statusRemovedEffect = null;
            if (type === 'removedDebuffs' || type === 'removedBuffs') {
                let statusEffectPosition = $statusEffect.position();
                $statusRemovedEffect = $('<div>', {class: 'status-effect status-removed-effect'}).css({
                    top: statusEffectPosition.top - 5,
                    left: statusEffectPosition.left - 10,
                    width: $statusEffect.find('.status-effect-text').width() + 30, // img 크기까지
                    height: $statusEffect.find('.status-effect-text').height()
                });
                $statusEffectWrapper.append($statusRemovedEffect);
            }

            let statusForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let statusPageCount = Math.floor(statusIndex / statusForPage); // 현재 표시할 스테이터스의 페이지 (0 부터)
            let startDelay = effectVideoDuration + (buffShowDuration * statusPageCount) + (150 * (statusIndex % statusForPage)); // 이펙트당 buffShowDuration 만큼 보여줌
            lastBuffFadeoutStartTime = startDelay + 800; // 가장 늦게 시작하는 버프가 fadeout 하는 시점

            setTimeout(() => {
                if ($statusRemovedEffect != null) {
                    // 스테이터스 제거 효과
                    player.playMotions(Player.abilityRequest(`actor-${actorIndex}`, [Player.c_animations.ABILITY_EFFECT_ONLY], BASE_ABILITY.DISPEL));
                    // audioPlayer.loadAndPlay(GlobalSrc.STATUS_REMOVED.audio);
                    $statusEffect.addClass('status-removed');
                    $statusRemovedEffect.addClass('active');
                } else {
                    $statusEffect.fadeTo(100, 1).delay(700).fadeTo(200, 0); // CHECK buffShowDuration 과 맞춰야함
                }

                if (statusIndex % statusForPage + 1 === statusForPage) {
                    // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, statusIndex % statusForPage + 1).remove(), 1100);
                }

                if (statusIndex >= statuses.length - 1) {
                    // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), buffShowDuration);
                }
            }, startDelay);
        });

        $actorContainer.append($statusEffectWrapper);
    });
    return lastBuffFadeoutStartTime;
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @param debuffStartDelay 시작딜레이 (이펙트딜레이 + 버프딜레이)
 * @returns longestDebuffEndTime 가장 긴 디버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processDebuffEffect(addedDebuffStatusesList, debuffStartDelay) {
    let lastDebuffFadeoutStartTime = debuffStartDelay; // 디버프 없을댄 이전 딜레이 (이펙트 + 버프 딜레이) 만큼
    let debuffShowDuration = 1000; // 디버프 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [적][아][아][아][아]
        if (addedDebuffStatuses.length === 0) return;
        let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
        let currentEffectContainerIndex = $actorContainer.find('.status-effect-wrapper').length;
        let $statusEffectWrapper = $('<div>', {class: 'status-effect-wrapper actor-' + actorIndex + ' status-index-' + currentEffectContainerIndex});
        $statusEffectWrapper.addClass(actorIndex === 0 ? 'enemy' : 'party');

        addedDebuffStatuses.forEach(function (debuffStatus, debuffIndex) {
            let statusTypeName = debuffStatus.type.toLowerCase();
            statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(debuffStatus.name) ? 'none' : statusTypeName;
            let $statusEffect = $('<div>', {class: `status-effect status-effect-${debuffIndex} ${statusTypeName}`})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $statusEffectWrapper.append($statusEffect);

            let debuffForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 디버프 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let debuffPageCount = Math.floor(debuffIndex / debuffForPage); // 현재 표시할 디버프의 페이지 (0 부터)
            let startDelay = debuffStartDelay + (debuffShowDuration * debuffPageCount) + (150 * (debuffIndex % debuffForPage)); // 이펙트당 debuffShowDuration 만큼 보여줌
            lastDebuffFadeoutStartTime = debuffStartDelay + 800; // 마지막 디버프가 페이드아웃 시작하는 시점

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(700).fadeTo(200, 0); // CHECK debuffShowDuration 이랑 맞춰야함

                if (debuffIndex % debuffForPage + 1 === debuffForPage) {
                    // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, debuffIndex % debuffForPage + 1).remove(), 1100);
                }

                if (debuffIndex >= addedDebuffStatuses.length - 1) {
                    // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), debuffShowDuration);
                }
            }, startDelay);
        });

        $statusEffectWrapper.appendTo($actorContainer);
    });
    return lastDebuffFadeoutStartTime;
}

/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param charOrder
 * @param elementType
 * @param type 데미지 타입, 'attack', 'ability', chargeAttack'
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemyDamage optional boolean
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(charOrder, elementType, type, index, damage, additionalDamages, isEnemyDamage = false) {
    let typeClassName;
    switch (type) {
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
            new Error('[getDamageElement] invalid type, type = ' + type);
    }
    let actorClassName = ' actor-' + charOrder;
    let elementClassName = ' element-type-' + elementType.toLowerCase();
    let damageIndexClassName = ' damage-index-' + index;
    let enemyClassName = isEnemyDamage ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';

    // 데미지 요소
    let $damage = $('<div>', {
        class: typeClassName + actorClassName + elementClassName + damageIndexClassName + elementClassName + missClassName + enemyClassName,
        text: damage
    });
    // 추격 요소
    let $additionalDamage = $('<div>', {
        class: 'additional-damage-wrapper ' + enemyClassName + actorClassName + elementClassName + missClassName,
        text: additionalDamages
    }).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $('<div>', {
            class: 'additional-damage' + elementClassName,
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
        let omenInfo = '전조 효과 : ' + $('.omen-container .omen-info').text();
        let omenTypeClassName = $('.omen-container .omen-text').attr('class').split(' ')[1];
        omenInfo = omenInfo.replace(/\\n/g, '<br>특수기 효과:');
        let $omenInfo = $('<div>').addClass('status-info omen ' + omenTypeClassName).html(omenInfo);
        $statusInfoModal.append($omenInfo).append($('<hr>'));
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
        let statusDuration = $status.find('.status-duration').text();
        statusDuration = Number.parseInt(statusDuration) > 999 ? '영속' : statusDuration + '턴';
        let $statusInfo = $('<div>').addClass('status-info ' + statusType)
            .append($('<div>').addClass('status-info-icon-wrapper')
                .append($('<img>').addClass('status-info-icon').attr('src', iconSrc)))
            .append($('<div>').addClass('status-info-text').text(statusInfoText)
                .prepend($('<span>').addClass('status-info-name fw-bold').text(statusName + ": "))
                .append($('<span>').addClass('status-info-duration fw-bold').text(' [남은시간: ' + statusDuration + ']')));
        $statusInfoModal.append($statusInfo).append($('<hr>'));
    })
    $('.status-modal-button').click();
}