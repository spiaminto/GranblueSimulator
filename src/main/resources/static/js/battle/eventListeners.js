$(function () {

    // 오의 on off 리스너
    $('#chargeAttackActiveCheck').on('change', function () {
        $(this).prop('disabled', true);
        requestToggleChargeAttack($(this).prop('checked'));
        setTimeout(() => {
            $(this).prop('disabled', false);
        }, 500);
    });

    // 소환석 클릭 이벤트리스너
    $('#commandContainer .summon-list .summon-list-item:not(.empty)').on('click', function () {
        let characterId = $('#partyCommandContainer .battle-portrait').eq(0).data('character-id');
        let summonId = $(this).data('summon-id');
        $('.summon-display-button').click();
        requestMove(characterId, summonId);
    });

    // 어빌리티 슬라이더 버튼 클릭
    $('.ability-slider-slide-button-wrapper').on('click', function () {
        $(this).hasClass('to-right') ? $('#abilitySlider').slick('slickNext') : $('#abilitySlider').slick('slickPrev');
    })

    // 어빌리티 슬라이더 이벤트 (모션 재생)
    $('#abilitySlider').on('beforeChange', function (event, slick, currentSlideIndex, nextSlideIndex) {
        console.log('beforeChange', currentSlideIndex, nextSlideIndex);
        player.play(Player.playRequest(`actor-${nextSlideIndex + 1}`, Player.c_animations.ABILITY));
        let beforeActor = player.actors.get(`actor-${currentSlideIndex + 1}`);
        if (player.effectPlayingActorIndex !== beforeActor.actorIndex) {
            player.play(Player.playRequest(`actor-${currentSlideIndex + 1}`, Player.getCharacterWaitMotion(currentSlideIndex + 1)));
        }
    })

    // 배틀 초상화 클릭 -> 어빌리티 슬라이더 오픈
    $('.battle-portrait').on('click', function () {
        let abilitySliderWidth = $('#abilitySlider .slick-track').width();
        let battlePortraitIndex = $(this).index();
        // console.log('[.battle-portrait.onClick] battlePortraitindex = ' + battlePortraitIndex);
        $('#abilitySlider').slick('slickGoTo', battlePortraitIndex, true, {speed: 0})
        player.play(Player.playRequest(`actor-${battlePortraitIndex + 1}`, Player.c_animations.ABILITY));
        $('#abilitySlider').css('z-index', '1');
        $('.present-container').addClass('hidden');
        $('.ability-slider-slide-button-wrapper').show();
        $('.ability-back-button').show();
    })

    // 어빌리티 뒤로 버튼 -> 어빌리티 슬라이더 닫기
    $('.ability-back-button').on('click', function () {
        $('#abilitySlider').css('z-index', '-1');
        $('.ability-slider-slide-button-wrapper').hide();
        $('.present-container').removeClass('hidden');
        clearInterval(statusShowHideInterval); // 스테이터스 끊어보여주기 인터벌 해제
        player.actors.values()
            .filter(actor => Player.c_animations.ABILITY === actor.playingMotion && player.effectPlayingActorIndex !== actor.actorIndex)
            .forEach(actor => player.play(Player.playRequest(`actor-${actor.actorIndex}`, Player.getCharacterWaitMotion(actor.actorIndex))));
        $(this).hide();
    });

    // 어빌리티 커맨드에서 스테이터스 12개 이상인경우 끊어서 보여주기
    let statusShowHideInterval = null;
    $('#abilitySlider').on('afterChange', function (event, slick, currentSlideIndex) {
        // console.log('current', currentSlideIndex);
        clearInterval(statusShowHideInterval);
        let $currentStatuses = $('.slick-active .status-container .status:not(.d-none)'); // 안보이는거 제외한 스테이터스
        if ($currentStatuses.length > 11) {
            let statusShowHideCallback = function () {
                // console.log('interval', currentSlideIndex + 1);
                let $frontStatues = $('.slick-active .status-container .status').slice(0, 11); // 갱신되면 다시찾아야됨
                $frontStatues.show(0).delay(1000).hide(0);
            }
            statusShowHideCallback();
            statusShowHideInterval = setInterval(statusShowHideCallback, 2000);
        }
    })

    // 어빌리티 슬라이더에서 스테이터스 상세확인
    $('.show-status-info-button-wrapper').on('click', function () {
        openStatusWrapperInfo($(this).closest('.status-container'));
    })

    // 가드버튼 이벤트 등록
    $('.guard-button').each(function (index, guardButton) {
        let pressStart = 0;
        const executionDelay = 500; // 최소 실행 딜레이
        const longPressThreshold = 800; // 길게누르기 지연시간
        let longPressTimer = null; // 길게누르기 자동실행 타이머
        let mouseUpTriggered = false; // 길게누르기 자동실행으로 mouseup 트리거 됫는지 확인하는 플래그

        $(guardButton).on('mousedown touchstart', function () {
            // console.log('[.guardButton mouseDown, touchStart] pressStart = ', pressStart);
            const now = Date.now();
            if (now - pressStart < executionDelay) {
                mouseUpTriggered = true;
                return;
            }
            pressStart = now; // 시작시간 기록
            longPressTimer = setTimeout(() => {
                $(guardButton).trigger('mouseup'); // 길게누르기 시간 경과시 자동으로 mouseup 트리거
                mouseUpTriggered = true;
            }, longPressThreshold);
        });

        $(guardButton).on('mouseup touchend', function () {
            const now = Date.now();
            // console.log('[.guardButton mouseup eventListener] times start = ', pressStart, ' end = ', now, 'duration = ', now - pressStart, 'mouseUpTriggered = ', mouseUpTriggered);
            if (mouseUpTriggered) {
                mouseUpTriggered = false; // 자동으로 mouseup 발생시 플래그 초기화후 처리없이 반환
                return;
            }
            clearTimeout(longPressTimer);
            const pressDuration = now - pressStart;
            if (pressDuration >= longPressThreshold) {
                // 길게 누르기
                requestGuard(index + 1, 'PARTY_MEMBERS');
            } else {
                // 짧게 클릭
                requestGuard(index + 1, 'SELF');
            }
        });
    });

    // 방 나가기 이벤트 등록
    $('.exit-room-button').on('click', function () {
        if (!confirm("방에서 퇴장하면 클리어시 획득 가능한 아이템이나 공헌도를 모두 잃습니다")) return;
        $('#exitRoomForm').submit();
    })
})