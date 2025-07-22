function processAbility(response) {
    // 준비 - 아군
    let $partyVideo = getVideo(response.charOrder, response.moveType);
    let effectDuration = $partyVideo.effect ? $partyVideo.effect.get(0).duration * 1000 - 100 : 0; // 어빌리티 이펙트 재생 길이, seconds to milliseconds, 영상 딜레이 100ms 보정
    if ($partyVideo.effect != null && $partyVideo.motion == null) $partyVideo.effect.addClass('large'); // 모션 없는 어빌리티는 900p 어택과 같이 별도 클래스 추가
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 시작
    // 오디오 재생
    let audioSrcs = $('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className).toArray().map(audio => $(audio).attr('src'));
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => window.effectAudioPlayer.playAllSounds());

    // 아군 이펙트, 모션 재생
    if ($partyVideo.effect) {
        playVideo($partyVideo.effect, $partyVideo.motion, $partyVideo.idle);
    }

    // 데미지 삽입 (미리 삽입해놔야됨)
    let currentAbilityDamageWrapperIndex = $('.ability-damage-wrapper').length;
    let $abilityDamageWrapper = $('<div>', {class: 'ability-damage-wrapper ability-index-' + currentAbilityDamageWrapperIndex});
    if (response.damages.length > 0) {
        response.damages.forEach(function (damage, damageIndex) {
            let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], 'ability', damageIndex, damage, []);
            $abilityDamageWrapper.prepend($damageElements.$damage);
        })
        $('#abilityDamageContainer').append($abilityDamageWrapper);
    }

    // 데미지 마다 표시, 적 피격 모션 재생
    let damageShowClass = response.totalHitCount > 3 ? 'multiple-ability-damage-show' : 'ability-damage-show'
    let effectHitDuration = (effectDuration - 200) / response.damages.length;
    response.damages.forEach(function (damage, damageIndex, damageArray) {
        let startDelay = effectHitDuration * damageIndex; // CHECK /2 했는데 추이를 지켜보고 수정
        if (damageIndex === 0) {
            $enemyVideo.idle.addClass('left-hidden').get(0).pause();
            $enemyVideo.idle.get(0).currentTime = 0;
            $enemyVideo.effect.removeClass('hidden');
        }
        setTimeout(function () {
            // 피격 비디오 연속재생의 경우 idle 과 교차재생하면 부자연스러워 지므로 연속재생후 마지막에 되돌림
            $enemyVideo.effect.get(0).currentTime = 0; // 빼면 부자연스러워짐
            $enemyVideo.effect.get(0).play();
            // 데미지 표시
            $abilityDamageWrapper.find('.ability-damage').eq(response.damages.length - 1 - damageIndex).addClass(damageShowClass);
            if (damageIndex >= damageArray.length - 1) { // 마지막
                console.log('[processDamageAbility] $abilityDamageWrapper', $abilityDamageWrapper)
                setTimeout(function () {
                    // 적 모션 정상화
                    $enemyVideo.idle.removeClass('left-hidden').get(0).play(); // 가끔 멈춰서 재생갱신
                    $enemyVideo.effect.addClass('hidden');
                    // 마지막 데미지 페이드 아웃시 전체 제거
                    setTimeout(() => $abilityDamageWrapper.remove() , 1000);
                }, effectHitDuration); // 막타대기
            }
        }, startDelay);
    });

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration);

    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, healEndTime, buffEndTime, debuffEndTime);
    console.log('[processAbility] totalTime', totalEndTime, 'abilityDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
}


function processEnemyAbility(response) {
    let isEnemyPowerUp = response.enemyPowerUp;
    let isEnemyCtMax = response.enemyCtMax;

    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let $enemyVideo = getVideo(0, response.moveType, idleMoveType);
    let effectDuration = $enemyVideo.effect ? $enemyVideo.effect.get(0).duration * 1000 : 0;
    let $globalEffectVideo =
        isEnemyPowerUp ? $('.global-video-container .enemy-power-up') :
            isEnemyCtMax ? $('.global-video-container .enemy-ct-max') : null;
    if ($globalEffectVideo) { // 글로벌 이펙트의 경우 처리 가속
        $enemyVideo.effect = $globalEffectVideo.eq(0);
        effectDuration = effectDuration / 2;
    }

    // 준비 - 아군 (0번은 사용안함)
    let $partyVideos = [-1, 1, 2, 3, 4]
        .map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));

    // 오디오 재생
    let audioSelector =
        isEnemyPowerUp ? '.global-audio-container .enemy-power-up' :
            isEnemyCtMax ? '.global-audio-container .enemy-ct-max ' : '.enemy-audio-container .' + response.moveType.className;
    let audioSrcs = $(audioSelector).toArray().map(element => $(element).attr('src'));
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => {
        window.effectAudioPlayer.playAllSounds();
    });

    // 이펙트 재생
    if ($enemyVideo.effect) {
        let $playIdleVideo = isEnemyPowerUp || isEnemyCtMax ? null : $enemyVideo.idle; // 적 파워업 또는 CTMAX 의 경우 이펙트만 재생
        playVideo($enemyVideo.effect, null, $playIdleVideo);
    }

    // 데미지 후처리 (데미지 표시, 아군 피격 재생)
    if (response.damages.length > 0) enemyDamagesPostProcess(response, $enemyVideo, $partyVideos);

    // 스테이터스 아이콘 갱신
    // 이펙트가 없는 서포트 어빌리티는 기다리지 않음
    let effectEndTime = effectDuration === 0 ? 0 : effectDuration + 500;
    processStatusIconSync(response.currentBattleStatusesList, effectEndTime);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectEndTime);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processEnemyAbility] totalTime', totalEndTime, 'abilityDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
}

function processFatalChain(response) {
    // 준비 - 아군
    let $effectVideo = $('.global-video-container .fatal-chain-video');
    let effectStartDelay = 100; // 파티 공격 ~ 페이탈 체인 이펙트 시작까지 딜레이
    let effectDuration = $effectVideo.get(0).duration * 1000 + effectStartDelay;
    let $partyFirstAbilityVideos = [1, 2, 3, 4] // 아군의 어빌리티 모션 전체 재생
        .map(number => getVideo(number, MoveType.FIRST_ABILITY, MoveType.IDLE_DEFAULT));
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 시작
// 오디오 재생
    let audioSrc = $('.global-audio-container .fatal-chain-audio').attr('src');
    window.effectAudioPlayer.loadSound(audioSrc, true).then(() => {
        setTimeout(() => window.effectAudioPlayer.playAllSounds(), effectStartDelay);
    });

    // 데미지 삽입 (페이탈 체인은 데미지 1회)
    let currentAbilityDamageWrapperIndex = $('.ability-damage-wrapper').length;
    let $abilityDamageWrapper = $('<div>', {class: 'ability-damage-wrapper ability-index-' + currentAbilityDamageWrapperIndex});
    let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], 'ability', 0, response.damages[0], []);
    $abilityDamageWrapper.prepend($damageElements.$damage).appendTo($('#abilityDamageContainer'));

    // 파티 전체 모션 재생
    $partyFirstAbilityVideos.forEach(function (partyVideo) {
        playVideo(partyVideo.motion, null, partyVideo.idle);
    })
    // 페이탈 체인 이펙트 재생
    setTimeout(() => playVideo($effectVideo, null, null), effectStartDelay);

    // 피격 이펙트, 데미지 표시
    let effectHitDelay = effectDuration - 500; // 데미지 히트 딜레이
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyVideo.effect, null, $enemyVideo.idle);
        // 데미지 표시
        $abilityDamageWrapper.find('.ability-damage').addClass('party-attack-damage-show');
        setTimeout(function () {
            $abilityDamageWrapper.remove();
        }, 1500);
    }, effectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectHitDelay + 600);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, effectHitDelay + 600);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processFatalChain] totalTime', totalEndTime, 'effectDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
}