$(function () {
    // console.log('summon.js loaded');
    $('#commandContainer .summon-list .summon-list-item:not(.empty)').on('click', function () {
        let characterId = $('#partyCommandContainer .battle-portrait').eq(0).data('character-id');
        let summonId = $(this).data('summon-id');
        requestSummon(characterId, summonId);
    });
});

function requestSummon(characterId, summonId) {
    // characterId 는 메인캐릭터만 허용 (나중에 서버에서 검증할것)
    // TODO 통신
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');
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
            console.log('[requestSummon] response = ', responseResults)
            processSummon(responseResults[0])
        },
        error: function (response) {
            console.log('[requestSummon] error, response = ', response)
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
    let heals = summonData.heals;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = summonData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = summonData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = summonData.battleStatusList;

    // 준비
    let partySelector = '.party-' + charOrder;
    let $summonEffectVideo = $('.summon-video-container .summon-video[data-summon-id=' + summonId + ']')
    let summonEffectDuration = $summonEffectVideo.get(0).duration * 1000;
    let summonEffectHitDelay = summonEffectDuration - 250;
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

// EFFECT 시작
// 오디오 재생
    let audioSrc = $('.summon-audio-container .summon-audio[data-summon-id=' + summonId + ']').attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 데미지 채우기 -> 어빌리티 에다가 채움
    summonDamages.forEach(function (damage, index) {
        let $damageElement = getDamageElement(charOrder, elementType, 'ability', index, damage, []);
        $('.ability-damage-wrapper').prepend($damageElement.$damage);
    })

    // 소환 이펙트 재생
    playVideo($summonEffectVideo, null, null);

    // 피격 이펙트, 데미지 표시
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyDamagedVideo, null, $enemyIdleVideo);
        // 화면 흔들기
        $('#videoContainer').addClass('push-left-down-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('push-left-down-effect');
        }, 150);
        // 데미지 표시
        let damageShowClass = summonDamages.length > 2 ? 'multiple-damage-show' : 'damage-show'
        let $abilityDamages = $('.ability-damage-wrapper .ability-damage');
        $abilityDamages.each(function (index, abilityDamage) {
            setTimeout(function () {
                $(abilityDamage).addClass(damageShowClass);
            }, index * 100)
            if (index >= $abilityDamages.length - 1) {
                // 마지막에 제거
                setTimeout(function () {
                    $abilityDamages.remove();
                }, 1000);
            }
        })
    }, summonEffectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(currentBattleStatusesList, summonEffectDuration);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(heals, summonEffectDuration + 500);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(summonEffectDuration, buffEndTime, debuffEndTime);
    console.log('[processSummon] totalTime', totalEndTime, 'abilityDuration ', summonEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 100));
}