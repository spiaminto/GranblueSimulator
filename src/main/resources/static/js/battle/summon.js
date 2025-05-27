$(function () {
    // console.log('summon.js loaded');
    $('#commandContainer .summon-list .summon-list-item:not(.empty)').on('click', function () {
        let characterId = $('#partyCommandContainer .battle-portrait').eq(0).data('character-id');
        let summonId = $(this).data('summon-id');
        requestSummon(characterId, summonId);
    });
});

function requestSummon(characterId, summonId) {
    // console.log('[processAbility] start process charOrder = ' + charOrder + 'abilityOrder = ' + abilityOrder);

    // characterId 는 메인캐릭터만 허용 (나중에 서버에서 검증할것)
    // TODO 통신
    // let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');
    let abilityId = $('#abilityInfo').data('ability-id');
    let moveType = MoveType.SUMMON;
    let responseResults = null;
    $.ajax({
        url: '/api/summon',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            memberId: memberId,
            characterId: characterId,
            moveType: moveType.name,
            summonId: summonId,
            roomId: roomId
        }),
        async: false,
        success: function (response) {
            responseResults = response;
            console.log('==============================summon==========================')
            console.log(responseResults);
            processSummon(responseResults[0])
        },
        error: function (response) {
            console.log('==============================summon==========================')
            console.log(response);
        }
    });
}

function processSummon(responseSummonData) {
    let summonData = responseSummonData;
    let charOrder = responseSummonData.charOrder;
    let moveType = MoveType.byName(responseSummonData.moveType);
    let summonId = responseSummonData.summonId;
    let summonHitCount = summonData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let summonDamages = summonData.damages;
    let elementType = summonData.elementTypes[0];
    let chargeGauges = summonData.chargeGauges;
    let hps = summonData.hps;
    let hpRates = summonData.hpRates;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = summonData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = summonData.battleStatusList;
    // 아군(시전자) 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList[charOrder].length > 0 || addedBuffStatusesList[0].length > 0;
    let hasDebuff = addedDebuffStatusesList[charOrder].length > 0 || addedDebuffStatusesList[0].length > 0;

    // 준비
    let partySelector = '.party-' + charOrder;
    let $summonEffectVideo = $('.summon-video-container .summon-video[data-summon-id=' + summonId + ']')

    let audioPlayers = new Map();
    audioPlayers.set('char', new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());
    let charAudioPlayer = audioPlayers.get('char');

// EFFECT 시작
// 오디오 재생
    let audioInfos = $('.summon-audio-container .summon-audio[data-summon-id=' + summonId + ']').toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    if (audioInfos.length > 0)
        charAudioPlayer.loadSounds(audioInfos).then(() => {
            charAudioPlayer.playAllSounds();
        });

    // 데미지 채우기 -> 어빌리티 에다가 채움
    summonDamages.forEach(function (item, index) {
        $('.ability-damage-wrapper').prepend($('<div>', {
            class: 'ability-damage ability-damage-' + index + ' element-type-' + elementType.toLowerCase(),
            text: item,
            'data-text': item
        }));
    })

    // 소환 이펙트 재생 (오디오 속도가 느리므로, 100ms 정도 딜레이 걸고 재생
    setTimeout(function () {
        $summonEffectVideo.removeClass('hidden').get(0).play();
    }, 100);

    // 아군 오의 이펙트 종료 후 데미지 표시를 위해 + 1000 + 타수만큼 딜레이, 적 피격모션 재생 -> 나중에 시간제로 앞당겨서 할수잇음
    let summonEffectDuration = $summonEffectVideo.get(0).duration * 1000 + 1000 + summonDamages.length * 50;

    $summonEffectVideo.one('ended', function () {
        $(this).addClass('hidden');
        // 적 idle 및 damaged 모션 클래스 찾기
        let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
        let idleMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
        let damagedMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
        // 클래스로 비디오 찾기
        let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
        let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

        // 화면 흔들기
        $('#videoContainer').addClass('shake-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('shake-effect');
        }, 200);

        // idle 숨기고 damaged 재생
        $enemyIdleVideo.addClass('hidden'); // idle 숨김 (인터벌 전에 숨기면 플리커)
        $enemyDamagedVideo.removeClass('hidden').one('ended', function () {
            $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
            $enemyDamagedVideo.addClass('hidden');
        }).get(0).play();

        // 데미지 표시
        $('.ability-damage-wrapper .ability-damage').each(function (index, abilityDamage) {
            $(abilityDamage).fadeTo(10, 0.01).delay(index * 50).fadeTo(10, 1).delay(600).fadeTo(400, 0);
        })

        setTimeout(function () {
            $('.ability-damage-wrapper').children().remove();
        }, 1000 + summonDamages.length * 50);
    });

    // 스테이터스 아이콘 갱신 (이펙트 직후 즉시 갱신)
    setTimeout(function () {
        currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
            let $statusContainer = $('.status-container.actor-' + actorIndex);
            $statusContainer.find('.status').remove(); // 스테이터스 비움
            currentBattleStatuses.forEach(function (status, index) {
                // 어빌리티 패널에 갱신된 스테이터스 추가
                let $statusInfo = $('<div>', {class: 'status status', 'data-status-type': status.type})
                    .append(
                        $('<img>', {
                            src: status.imageSrc,
                            class: 'status-icon' + (status.imageSrc.length < 1 ? ' none-icon' : '')
                        }),
                        $('<div>', {class: 'status-name d-none', text: status.name}),
                        $('<div>', {class: 'status-info-text d-none', text: status.statusText}),
                        $('<div>', {class: 'status-duration d-none', text: status.duration})
                    );
                $statusContainer.append($statusInfo);
            })
        });
    }, summonEffectDuration);

    // BUFF 버프처리 시작
    // 버프 이펙트 요소 내용 채우고 페이드 걸기
    let longestBuffDelay = 0;
    addedBuffStatusesList.forEach(function (addedBuffStatuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        addedBuffStatuses.forEach(function (buffStatus, buffIndex) {
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + buffIndex})
                .append(
                    $('<img>', {src: buffStatus.imageSrc, class: buffStatus.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: buffStatus.effectText})
                );
            $effectContainer.prepend($statusEffect);

            // 페이드 길이 1100 + index * 50 , 3번째 길이 1250
            let additionalStartDelay = buffIndex / 3 >= 1 ? 1250 + 100 : 0; // 3개 이상일경우 딜레이 추가 (3번째가 사라지는 시간 + 안전마진)
            let removeDelay = (1100 * (Math.floor(buffIndex / 3) + 1)) + (50 * buffIndex) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
            longestBuffDelay = Math.max(longestBuffDelay, additionalStartDelay + removeDelay);

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(600).fadeTo(400, 0);
            }, summonEffectDuration + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, summonEffectDuration + additionalStartDelay + removeDelay);
            setTimeout(() => {
                // 마지막 버프효과까지 모두 끝난후
            }, longestBuffDelay)
        });
    });
    let buffEndTime = summonEffectDuration + longestBuffDelay;

// DEBUFF 디버프 처리 시작
    // 디버프 이펙트 요소 내용 채우고 페이드 걸기
    let debuffStartDelay = hasDebuff ? summonEffectDuration + 1000 : summonEffectDuration; // 버프 없으면 즉시시작
    let longestDebuffDelay = 0;
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [1번째 캐릭] [2번째 캐릭]...
        addedDebuffStatuses.forEach(function (debuffStatus, buffIndex) {
            if (debuffStatus.type === 'DISPEL') { // 디스펠의 경우 별도처리
                return;
            }

            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + buffIndex})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $effectContainer.prepend($statusEffect);

            // 페이드 길이 1100 + index * 50 , 3번째 길이 1250
            let additionalStartDelay = buffIndex / 3 >= 1 ? 1250 + 100 : 0; // 3개 이상일경우 딜레이 추가 (3번째가 사라지는 시간 + 안전마진)
            let removeDelay = (1100 * (Math.floor(buffIndex / 3) + 1)) + (50 * buffIndex) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
            longestDebuffDelay = Math.max(longestDebuffDelay, additionalStartDelay + removeDelay);

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(600).fadeTo(400, 0);
            }, debuffStartDelay + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, debuffStartDelay + additionalStartDelay + removeDelay);
        });
    });
    let debuffEndTime = debuffStartDelay + longestDebuffDelay;

    console.log('summonEffectDuration ', summonEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime)
    let totalEndTime = Math.max(summonEffectDuration, buffEndTime, debuffEndTime);
    console.log("total = " + totalEndTime)

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 500));
}