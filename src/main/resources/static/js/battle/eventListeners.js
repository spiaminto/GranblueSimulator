$(function () {

    //모달 공통 ============================================================================================================
    // 배틀화면에서, 모달을 #modalContainer 내부에서 정상작동시키기 위한 처리
    $('.modal').on('show.bs.modal', (event) => {
        const trigger = event.relatedTarget // 버튼 클릭으로 열렸을때, event.relatedTarget 로 트리거 요소 접근 가능
        $('#modalContainer').css('z-index', '9999'); // 열리면 이쪽이 기존 컨테이너 대신 앞으로
        // backdrop 컨테이너 안으로 넣기
        requestAnimationFrame(() => $('#modalContainer').append($('body > .modal-backdrop')));
        // body에 붙는 기본 부작용(스크롤 잠금/패딩) 제거
        $('body').removeClass('modal-open').css('padding-right', '');
    });
    $('.modal').on('shown.bs.modal', (event) => {
        $('#modalContainer').append($('body > .modal-backdrop')); // 보험
    })
    $('.modal').on('hidden.bs.modal', (event) => {
        $('#modalContainer').css('z-index', '-1'); // 닫히면 컨테이너 뒤로
    })

    //사운드 버튼 ============================================================================================================
    $('.sound-toggle-button').on('click', function () {
        new Audio(Sounds.ui.BEEP.src).play(); // window.audio 객체 사용하지 않고 재생
        let soundStatus = localStorage.getItem('soundStatus');
        if (soundStatus === 'on') {
            window.audio.updateDisabled(true);
            localStorage.setItem('soundStatus', 'off');
            $(this).removeClass('btn-outline-light').addClass('btn-outline-secondary');
        } else if (soundStatus === 'off') {
            window.audio.updateDisabled(false);
            localStorage.setItem('soundStatus', 'on');
            $(this).removeClass('btn-outline-secondary').addClass('btn-outline-light');
        }
    })

    //오의 on off 리스너 ============================================================================================================
    $('#chargeAttackActiveCheck').on('change', function (event) {
        if (player.locked) {
            event.target.checked = !event.target.checked; // 플레이어 잠금시 변경불가
            return;
        }
        $(this).prop('disabled', true);
        requestToggleChargeAttack($(this).prop('checked'));
        setTimeout(() => $(this).prop('disabled', false), 500);
    });

    //소환석 ============================================================================================================
    // 합체소환석 누르면 소환석 리스트 열기
    $('#commandContainer .union-summon-chance-wrapper').on('click', function () {
        $('.summon-display-button').click();
    });

    //어빌리티 슬라이더 ====================================================================================================
    // 배틀 초상화 클릭 -> 어빌리티 슬라이더 오픈
    $('.battle-portrait:not(.empty)').on('click', function () {
        let battlePortraitIndex = $('.battle-portrait:not(.empty)').index(this);
        $('#abilitySlider').slick('slickGoTo', battlePortraitIndex, true, {speed: 0})
        // 요소 보이게
        $('#abilitySlider').css('z-index', '5');
        $('.ability-slider-slide-button-wrapper').show();
        $('.ability-back-button').show();
    })

    // 어빌리티 뒤로 버튼 -> 어빌리티 슬라이더 닫기
    $('.ability-back-button').on('click', function () {
        $('#abilitySlider .ability-panel').removeClass('active'); // 패널 마킹 삭제
        clearInterval(statusShowHideInterval); // 스테이터스 끊어보여주기 인터벌 해제
        // 요소 숨김
        $('#abilitySlider').css('z-index', '-1');
        $('.ability-slider-slide-button-wrapper').hide();
        $(this).hide();

        player.renewCharacterWait();
        playSe(Sounds.ui.BUTTON_CLOSE.src);
    });

    // 어빌리티 슬라이더 좌 우 버튼 클릭 이동
    $('.ability-slider-slide-button-wrapper').on('click', function () {
        let $abilitySlider = $('#abilitySlider');
        $(this).hasClass('to-right') ? $abilitySlider.slick('slickNext') : $abilitySlider.slick('slickPrev');
    })

    // 어빌리티 슬라이더 열기 + 스와이프 전처리 이벤트
    $('#abilitySlider').on('beforeChange', function (event, slick, currentSlideIndex, nextSlideIndex) {
        if (player.locked) return; // 플레이어가 잠겻을 경우 전처리 없음, 잠겻어도 어빌리티 확인은 가능
        console.debug('[#abilitySlider.beforeChange], currentSlideIndex = ', currentSlideIndex, ' nextSlideIndex = ', nextSlideIndex); // nextSlideIndex 가 active 됨. from - currentSlideIndex, to - nextSlideIndex

        let $abiltiyPanels = $('#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel');
        let $fromAbilityPanel = $abiltiyPanels.eq(currentSlideIndex);
        let $toAbilityPanel = $abiltiyPanels.eq(nextSlideIndex); // slideIndex 는 실제 캐릭터 순서와 상관없이 요소갯수로 지정됨
        let fromActorOrder = Number($fromAbilityPanel.attr('data-actor-order')); // 캐릭터가 중간에 비어있을 수 있어 order 로 직접접근

        // 어빌리티 패널에 마킹 (모션 재생보다 우선해야됨)
        $toAbilityPanel.addClass('active');
        $fromAbilityPanel.removeClass('active');

        // 모션 재생
        if (currentSlideIndex === nextSlideIndex) {
            // 슬라이더 이동 없음 (첫 클릭 or 동일 클릭)
            player.play(Player.playRequest(`actor-${fromActorOrder}`, Player.c_animations.ABILITY));
            return;
        } else {
            // 슬라이더 이동 있음
            let toActorOrder = Number($abiltiyPanels.eq(nextSlideIndex).attr('data-actor-order'));
            console.debug('[#abilitySlider.beforeChange] toActorOrder', toActorOrder, 'fromActorOrder', fromActorOrder);

            player.renewCharacterWait();

            // 슬라이더 active 캐릭터 ABILITY 모션재생 (stageIndex 를 고려해 이쪽을 나중에 재생)
            player.play(Player.playRequest(`actor-${toActorOrder}`, Player.c_animations.ABILITY));
        }
    })

    // 어빌리티 슬라이더에서 어빌리티 아이콘 클릭
    $('#abilitySlider .ability-icon').on('click', onAbilityIconClicked);

    // 어빌리티 커맨드에서 스테이터스 12개 이상인경우 끊어서 보여주기
    let statusShowHideInterval = null;
    $('#abilitySlider').on('afterChange', function (event, slick, currentSlideIndex) {
        // console.log('current', currentSlideIndex);
        clearInterval(statusShowHideInterval);
        let $currentStatuses = $('.slick-active .status-container.party .status:not(.d-none)'); // 안보이는거 제외한 스테이터스
        if ($currentStatuses.length > 11) {
            let statusShowHideCallback = function () {
                // console.log('interval', currentSlideIndex + 1);
                let $frontStatues = $('.slick-active .status-container.party .status').slice(0, 11); // 갱신되면 다시찾아야됨
                $frontStatues.show(0).delay(2000).hide(0);
            }
            statusShowHideCallback();
            statusShowHideInterval = setInterval(statusShowHideCallback, 4000);
        }
    });

    // 어빌리티 슬라이더에서 캐릭터에 부여된 상태효과 상세 확인
    $('.show-status-info-button').on('click', function () {
        openBattleStatusInfo($(this).closest('.status-container').attr('data-actor-index'));
    });

    // 커맨드 정보 모달에서 커맨드의 상태효과 상세 확인
    $('.show-status-effect-details-check').on('change', function (event) {
        if ($(this).prop('checked')) {
            $(this).closest('.modal-content').find('.status-effect-info-wrapper').removeClass('d-none');
            localStorage.setItem('abilityStatusEffectInfoCheck', 'true');
        } else {
            $(this).closest('.modal-content').find('.status-effect-info-wrapper').addClass('d-none');
            localStorage.setItem('abilityStatusEffectInfoCheck', 'false');
        }
    });

    // 커맨드 정보 모달에서 사용 클릭
    $('.use-ability-button').on('click', function () {
        $('#abilityInfoModal .close-ability-info-modal-button').click();
        let moveId = $(this).closest('.modal-footer').attr('data-move-id');
        processMoveClick(moveId);
    });

    // 기타행동 (오의+서포트) 모달열기
    $('.other-move-info-button').on('click', function () {
        let actorIndex = $(this).attr('data-actor-index');
        openOtherMoveInfoModal(actorIndex);
    });

    //가드 ==============================================================================================================
    $('.guard-button').each(function (index, guardButton) {
        let pressStart = 0;
        const executionDelay = 500; // 최소 실행 딜레이
        const longPressThreshold = 800; // 길게누르기 지연시간
        let longPressTimer = null; // 길게누르기 자동실행 타이머
        let mouseUpTriggered = false; // 길게누르기 자동실행으로 mouseup 트리거 됫는지 확인하는 플래그

        $(guardButton).on('mousedown touchstart', function (e) {
            e.preventDefault();
            if (player.locked) return;
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
            if (player.locked) return;
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

    //포션 =============================================================================================================
    // 포션 아이콘 클릭
    $('.potion-icon-container .potion-icon-wrapper').on('click', function () {
        let potionType = $(this).attr('data-potion-type'); // single, all, elixir
        let potionInfo = '';
        let $potionTargetRadioContainer = $('.potion-detail-container .potion-target-radio-container');
        switch (potionType) {
            case 'single':
                potionInfo = '아군 캐릭터 1명의 체력을 절반 회복합니다.';
                $potionTargetRadioContainer.show();
                break;
            case 'all' :
                potionInfo = '아군 캐릭터 전체의 체력을 절반 회복합니다.';
                $potionTargetRadioContainer.hide();
                break;
            case 'elixir':
                potionInfo = '미구현';
                $potionTargetRadioContainer.hide();
                break;
            default:
                console.warn('[.potion-icon-container .potion-icon-wrapper click event] potionType default case potionType =', potionType)
        }
        $('.potion-detail-container .potion-info').text(potionInfo);

        let $potionWrappers = $('.potion-icon-container .potion-icon-wrapper');
        $potionWrappers.removeClass('selected');
        $(this).addClass('selected');

        // 검증 (포션 상세 내역은 보여주기)
        let useButtonDisabled = false;
        let potionCount = stage.gGameStatus.potion.counts[$potionWrappers.index($(this))];
        let isPotionNotReady = $(this).find('.potion-overlay').is('.not-ready');
        let isQuestCleared = stage.gGameStatus.isQuestCleared;
        if (potionCount <= 0 || isPotionNotReady || isQuestCleared) useButtonDisabled = true;

        $('#usePotionButton')
            .attr('data-potion-type', potionType)
            .prop('disabled', useButtonDisabled);
    })

    // 포션 사용 클릭
    $('#usePotionButton').on('click', function () {
        if (player.locked) return;
        let potionType = $(this).attr('data-potion-type');
        let potion = gameStateManager.getState('potion.' + potionType.toLowerCase());
        let potionTargetCharOrder = potionType === 'single' ? $('.potion-target-radio-container input[name="potionTarget"]:checked').val() : -1;
        potion.actorId = potionTargetCharOrder !== -1 ? gameStateManager.getState('actorIds.' + potionTargetCharOrder) : gameStateManager.getState('actorIds').find((id, index) => !!index && !!id);
        appendToAbilityRail(potion);
        $('#potionModal .close-button').click();
    })

    //페이탈 체인, 공격 ====================================================================================================
    // 페이탈 체인 클릭
    $('.fatal-chain-gauge-wrapper').on('click', function () {
        if (player.locked) return;
        openCommandInfoModal(gameStateManager.getState('fatalChain'));
    })

    // 공격버튼 클릭
    $('#attackButton').on('click', onAttackButtonClicked);

    //방 관련 ============================================================================================================
    // 방 나가기 이벤트 등록
    $('.exit-room-button').on('click', function () {
        if (!confirm("방에서 퇴장하면 클리어시 획득 가능한 아이템이나 공헌도를 모두 잃습니다")) return;
        $('#exitRoomForm').submit();
    })

    //채팅 ============================================================================================================
    $('#chatSendBtn').on('click', function () {
        let content = $('#chatInput').val().trim();
        if (!content) return;
        requestSendChat('TEXT', content);
        $('#chatInput').val('');
    });

    $('#chatInput').on('keydown', function (e) {
        if (e.key === 'Enter') $('#chatSendBtn').click();
    });

    // 스탬프 패널 토글
    $('#toggleStampBtn').on('click', function () {
        $('#stampPanel').toggle();
    });

    // 스탬프 클릭
    $('#stampPanel .stamp-item').on('click', function () {
        requestSendChat('STAMP', $(this).data('stamp-key'));
        $('#stampPanel').hide();
    });
    
    // 숏메시지
    $('.short-message-button').on('click', function () {
        requestSendChat('TEXT', $(this).text());
    });

    //기타 ===============================================================================================================
    // 어빌리티 레일 mutationObserver 등록
    const abilityRailMutationObserver = new MutationObserver((entries) =>
        setTimeout(() => handleAbilityRailMutation(entries), stage.processing.response.hasEffect ? 1000 : 0)); // 참전자 버프 효과중이면 살짝 딜레이
    abilityRailMutationObserver.observe(document.querySelector('#abilityRail'), {childList: true});

});