function processAttack(responseAttackData) {
    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let hitCount = attackData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let damages = attackData.damages;
    let elementType = attackData.elementTypes[0];
    let attackHitCount = damages.length;
    let additionalDamages = attackData.additionalDamages;
    let additionalAttackHitCount = additionalDamages.reduce((totalSize, damage) => totalSize + damage.length, 0);
    let chargeGauges = attackData.chargeGauges;
    let hps = attackData.hps;
    let hpRates = attackData.hpRates;

    // 준비 - 아군
    let $attackEffectVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className);
    let $idleVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);
    let attackDuration = $attackEffectVideo.get(0).duration * 1000;
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.party-audio-container ' + partySelector + ' .' + moveType.className).attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 아군 일반공격 이펙트 재생
    playVideo($attackEffectVideo, null, $idleVideo);

    // 데미지 채우기
    let $attackDamageWrapper = $('<div>', {class: 'attack-damage-wrapper actor-' + charOrder});
    damages.forEach(function (damage, attackIndex) {
        let $attackDamage = $('<div>', {
            class: 'attack-damage actor-' + charOrder + ' element-type-' + elementType.toLowerCase(),
            text: damage
        }); // 서로 데미지가 겹칠수 있어 actor-1 로 구분
        if (additionalDamages[attackIndex]) {
            additionalDamages[attackIndex].forEach(function (additionalDamage, additionalIndex) {
                // 공격 타수마다 맞게 추격 붙여줌
                $attackDamage.append($('<div>', {
                    class: 'additional-damage element-type-' + elementType.toLowerCase(),
                    text: additionalDamage
                }));
            })
        }
        $attackDamageWrapper.append($attackDamage).prependTo($('#damageContainer'));
    })

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    damages.forEach(function (damage, index) {
        let startDelay = attackDuration / (damages.length + 1) * index;
        setTimeout(function () {
            // 적 피격 이펙트 재생
            playVideo($enemyDamagedVideo, null, $enemyIdleVideo);
            // 데미지 표시
            let $damage = $('.attack-damage-wrapper.actor-' + charOrder + ' .attack-damage').eq(index);
            $damage.addClass('damage-show');
            // 추가데미지 표시
            $damage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).show().addClass('damage-show'); // 추가데미지는 부모와의 fadeIn 겹침을 피하기 위해 display none 설정되어있음
                }, index + 100);
            });

            if (index >= damages.length - 1) {
                // 데미지 제거 (3번째까지, 후행동 공격은 제거하지 않도록
                setTimeout(function () {
                    $('.attack-damage-wrapper.actor-' + charOrder).slice(0, 3).remove();
                }, 1000); // 마지막 데미지 페이드아웃 딜레이 대기
            }
        }, startDelay)
    })

    // 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = attackDuration;
    console.log("[processAttack] total = " + totalEndTime)
    // 최종종료
    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 500));
}


function processEnemyAttack(responseAttackData) {

    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let hitCount = moveType === MoveType.SINGLE_ATTACK ? 1 : moveType === MoveType.DOUBLE_ATTACK ? 2 : 3;
    let damages = attackData.damages;
    let elementTypes = attackData.elementTypes;
    let additionalDamages = attackData.additionalDamages;
    let chargeGauges = attackData.chargeGauges;
    let hps = attackData.hps;
    let hpRates = attackData.hpRates;
    // 적 한정
    let targetOrders = attackData.enemyAttackTargetOrders;
    let isAllTarget = attackData.allTarget; // 전체공격여부
    let isAllTargetSubstituted = isAllTarget && targetOrders.every(target => target === targetOrders[0]) // 전체공격, 모든타겟 동일한경우

    // 준비
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let $enemyAttackVideo = $('.enemy-video-container .' + moveType.className);
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + moveType.className).attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 일반공격 이펙트 길이
    let attackDuration = $enemyAttackVideo.get(0).duration * 1000; // ms 변환 및 100ms 영상보정
    let attackHitDuration = attackDuration / hitCount;

    // 적 일반공격 이펙트 재생 - 히트수가 영상에 적용되어있어서 1회만
    $enemyIdleVideo.addClass('hidden'); // idle 모션 숨김
    $enemyAttackVideo.removeClass('hidden').one('ended', function () {
        $enemyIdleVideo.removeClass('hidden').get(0).play();

        $(this).addClass('hidden');
        // 데미지 엘리먼트 제거
        setTimeout(function () {
            $('.enemy-damage-wrapper').children().remove();
        }, 1000);
        // 차지턴 갱신
    }).get(0).play();

    //  데미지 마다 반복
    console.log(targetOrders, isAllTarget)
    damages.every(function (damage, damageIndex) {
        let effectDelay = isAllTarget ?
            attackHitDuration * Math.floor(damageIndex / targetOrders.length) : // 전체타겟의 경우 공격 한번당 파티 인원만큼 이펙트를 지연시키지 않고 모두 재생함
            attackHitDuration * damageIndex; // 단일 타겟의 경우 공격
        let targetOrder = isAllTarget ? targetOrders[damageIndex % targetOrders.length] : targetOrders[damageIndex]; // 데미지가 발생한 타겟순서, 전체타겟의 경우 1,2,3,4 만옴
        // console.log('targetorder', targetOrder, 'effectdelay', effectDelay);

        if (isAllTargetSubstituted && damageIndex % targetOrders.length !== 0) {
            // 전체공격 이면서 감싸기 && 데미지가 해당 타수의 첫번째가 아닌경우 ex) targetOrders = [1, 1, 1, 1, 1, 1, 1, 1] 전체공격, 타겟 4명, 타수2회, 조건 진입은 index 가 1, 2, 3, 5, 6, 7
            let $attackDamage = $('.enemy-damage-wrapper .enemy-attack-damage.actor-' + targetOrder).last(); // 아직 안없어진 이전 데미지와 겹침 방지
            // console.log('damage ', damage, $attackDamage.text(), Number.parseInt($attackDamage.text()), Number.parseInt($attackDamage.text()) + damage)
            $attackDamage.text(Number.parseInt($attackDamage.text()) + damage);
            if (additionalDamages[damageIndex]) {
                let $additionalDamage = $attackDamage.find('.additional-damage:first');
                $additionalDamage.text(Number.parseInt($additionalDamage.text()) + additionalDamages[damageIndex]);
            }
            return true; // 이후 처리 무시 (모션)
        }

        // 데미지 채우기 및 표시
        let $attackDamage = $('<div>', {
            class: 'damage enemy-attack-damage actor-' + targetOrder + ' element-type-' + elementTypes[damageIndex].toLowerCase(),
            text: damage
        });
        if (additionalDamages[damageIndex]) {
            additionalDamages[damageIndex].forEach(function (additionalDamage, additionalIndex) {
                // 공격 타수마다 맞게 추격 붙여줌
                $attackDamage.append($('<div>', {
                    class: 'damage additional-damage' + ' element-type-' + elementTypes[damageIndex].toLowerCase(),
                    text: additionalDamage
                }));
            })
        }
        $attackDamage.delay(effectDelay) // 각 공격 종료 다음에 데미지가 나와야함
            .fadeTo(10, 1).delay(400).fadeTo(400, 0).appendTo($('.enemy-damage-wrapper'));

        // 아군의 피격 이펙트 재생
        let $targetIdleVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.IDLE.className);
        let $targetDamagedVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.DAMAGED.className);
        setTimeout(function () {
            $targetDamagedVideo.removeClass('hidden').get(0).play();
            $targetIdleVideo.addClass('hidden'); // idle 보일경우 숨김

            // 아군 피격 모션을 idle 로 되돌림
            setTimeout(function () {
                $targetIdleVideo.removeClass('hidden');
                $targetDamagedVideo.addClass('hidden');
            }, attackHitDuration - 50); // 이건 이미 이펙트별 딜레이가 적용되어잇으로 공격 횟수별로 걸어주면 됨
        }, effectDelay + 50 * (damageIndex + 1)); // 공격보다 피격이 약간 느리게 시작, 캐릭터별 순서대로 50씩 딜레이 추가

        return true;
    });

    let totalEndTime = isAllTarget ? attackDuration + 100 : attackDuration * damages.length + 100;
    console.log("total = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        // 최종종료
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 500));

// 현재 일반공격의 스테이터스 갱신은 없음
}
