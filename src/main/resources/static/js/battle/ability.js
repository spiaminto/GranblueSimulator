function processAbility(charOrder, abilityOrder) {

    let globalAbilityAudioPlayer = new Audio();
    let abilityAudioPlayers = [new Audio(), new Audio()]; // 최대 두개

    let audioPlayers = new Map();
    audioPlayers.set('char', new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());

    // 어빌리티 누름
    let charAudioPlayer = audioPlayers.get('char');
    let partySelector = '.party-' + charOrder;
    let motionAbilityVideo = $(partySelector + '.motion-ability-' + abilityOrder);

    // 오디오 플레이어에 SE src 로드 (속도를 위해 통신전에 미리 로드)
    let audioSrcs = $(partySelector + '-audio.ability-' + abilityOrder).toArray().map(element => $(element).attr('src'));
    charAudioPlayer.loadSounds(audioSrcs).then(() => {

        console.log('audio ready, buffer = ', charAudioPlayer.buffers.size)

        // TODO 통신
        //...
        let data = null; // 받아온 데이터
        let hasDamage = true;
        let abilityHitCount = 3; // 어빌리티 히트수
        let hasBuff = true;
        let buffTarget = [1, 2, 3]; // 버프타겟, charOrder
        let hasDebuff = true;

        // 어빌리티 길이 가져옴
        let abilityDuration = motionAbilityVideo.data('duration');
        abilityDuration = abilityDuration * 1000 - 300; //  ms 로 변환 (및 딜레이 보정)
        console.log('abilityDuration = ' + abilityDuration)

        // 반복 플레이
        let abilitySePlayCount = 0;
        let abilitySePlayInterval = setInterval(function () {

            // 적 모션
            if (hasDamage) {
                playEnemyDamagedMotion();
            }
            // SE 재생
            charAudioPlayer.playAllSounds();
            
            //인터벌 클리어
            if (++abilitySePlayCount >= abilityHitCount) {
                clearInterval(abilitySePlayInterval);
            }
        }, abilityDuration / (abilityHitCount));

        // TODO 데미지 재생
        
        // 적 피격 모션 재생
        function playEnemyDamagedMotion() {
            $('.motion-enemy-idle').addClass('hidden');
            let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
            motionDamagedElement.play();
            $(motionDamagedElement).one('ended', function () {
                $(this).addClass('hidden')
                $('.motion-enemy-idle').removeClass('hidden');
            })
            return true;
        }

        // 아군 공격 모션 재생 (오디오 속도가 느리므로, 딜레이 걸고 재생
        setTimeout(function () {
            motionAbilityVideo.removeClass('hidden').get(0).play();

            // 끝나고 이펙트 숨김 처리
            $(motionAbilityVideo).one('ended', function () {
                $(this).addClass('hidden');
            })

            // 버프 이펙트 잇을경우 등록
            if (hasBuff === true) {
                $(motionAbilityVideo).one('ended', function () {
                    $.each(buffTarget, function (index, item) {
                        let charOrder = item;
                        // 미리 내용 채움
                        //...
                        $('.status-effect-container-' + charOrder + ' .status-effect').each(function (index, item) {
                            $(this).fadeIn('fast').delay(300).fadeOut();
                        })
                    })
                })
            }

            // 버프 이펙트 있을경우 등록
            if (hasDebuff) {
                $(motionAbilityVideo).one('ended', function () {
                    // 미리 내용 채움
                    //...
                    $('#enemyStatusEffectContainer' + ' .status-effect').each(function (index, item) {
                        $(this).fadeIn('fast').delay(300).fadeOut();
                    })
                })
            }

            // TODO 화면 갱신


        }, 100);

    })
    // audioSrcs.each(async function (index, item) {
    //     await charAudioPlayer.loadSound('char', $(item).attr('src'));
    // }).get();




}