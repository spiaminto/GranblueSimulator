$(function () {

    // 오의 on off 리스너
    $('#chargeAttackActiveCheck').on('change', function (event) {
        if (player.locked) {
            event.target.checked = !event.target.checked; // 바꾸지 않음
            return;
        }
        $(this).prop('disabled', true);
        requestToggleChargeAttack($(this).prop('checked'));
        setTimeout(() => {
            $(this).prop('disabled', false);
        }, 500);
    });

    // 소환석 클릭 이벤트리스너
    $('#commandContainer .summon-list .summon-list-item:not(.empty)').on('click', function () {
        if (player.locked) return;
        let summonId = $(this).data('summon-id');
        let summon = stage.gGameStatus.summon[summonId];
        openAbilityInfoModal(summon);
    });

    // 배틀 초상화 클릭 -> 어빌리티 슬라이더 오픈
    $('.battle-portrait:not(.empty)').on('click', function () {
        let battlePortraitIndex = $('.battle-portrait:not(.empty)').index(this);
        $('#abilitySlider').slick('slickGoTo', battlePortraitIndex, true, {speed: 0})

        $('#abilitySlider').css('z-index', '5');
        $('.present-container').addClass('hidden');
        $('.ability-slider-slide-button-wrapper').show();
        $('.ability-back-button').show();
    })

    // 어빌리티 뒤로 버튼 -> 어빌리티 슬라이더 닫기
    $('.ability-back-button').on('click', function () {
        $('#abilitySlider').css('z-index', '-1');
        $('.ability-slider-slide-button-wrapper').hide();
        $('.present-container').removeClass('hidden');
        $('#abilitySlider .ability-panel').removeClass('active'); // 패널 마킹 삭제
        clearInterval(statusShowHideInterval); // 스테이터스 끊어보여주기 인터벌 해제
        player.getCharacters()
            .filter(actor => player.effectPlayingActorIndex !== actor.actorIndex)
            .forEach(actor => player.play(Player.playRequest(`actor-${actor.actorIndex}`, player.getCharacterWaitMotion(actor.actorIndex))));
        $(this).hide();
        window.effectAudioPlayer.loadAndPlay(Sounds.global.BUTTON_CLOSE.src);
    });

    // 어빌리티 슬라이더 좌 우 버튼 클릭 이동
    $('.ability-slider-slide-button-wrapper').on('click', function () {
        let $abilitySlider = $('#abilitySlider');
        $(this).hasClass('to-right') ? $abilitySlider.slick('slickNext') : $abilitySlider.slick('slickPrev');
    })

    // 어빌리티 슬라이더 열기 + 스와이프 전처리 이벤트
    $('#abilitySlider').on('beforeChange', function (event, slick, currentSlideIndex, nextSlideIndex) {
        // 플레이어가 잠겻을 경우 전처리 없음, 잠겻어도 어빌리티 확인은 가능
        if (player.locked) return;
        // nextSlideIndex 가 active 됨. from - currentSlideIndex, to - nextSlideIndex
        console.log('beforeChange', currentSlideIndex, nextSlideIndex);
        // 캐릭터가 중간에 비어있을 경우를 대비해 해당 슬라이드의 character-order 직접 접근
        let $abiltiyPanels = $('#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel');
        let $fromAbilityPanel = $abiltiyPanels.eq(currentSlideIndex);
        let $toAbilityPanel = $abiltiyPanels.eq(nextSlideIndex);
        let fromActorOrder = Number($fromAbilityPanel.attr('data-actor-order'));

        // 어빌리티 패널에 마킹 (모션 재생보다 우선해야됨)
        $toAbilityPanel.addClass('active');
        $fromAbilityPanel.removeClass('active');

        // 모션 재생
        if (currentSlideIndex === nextSlideIndex) {
            // 슬라이더 이동 없음 (첫 클릭 or 동일 클릭)
            player.play(Player.playRequest(`actor-${fromActorOrder}`, Player.c_animations.ABILITY));
            return;
        } else  {
            // 슬라이더 이동 있음
            let toActorOrder = Number($abiltiyPanels.eq(nextSlideIndex).attr('data-actor-order'));
            console.log('toActorOrder', toActorOrder, 'fromActorOrder', fromActorOrder);

            // 슬라이더 active 해제된 캐릭터 기본 wait 모션 재생 (이펙트 수행중이 아닐경우)
            let fromActor = player.actors.get(`actor-${fromActorOrder}`);
            if (player.effectPlayingActorIndex !== fromActor.actorIndex) {
                player.play(Player.playRequest(`actor-${fromActorOrder}`, player.getCharacterWaitMotion(fromActorOrder)));
            }

            // 슬라이더 active 캐릭터 ABILITY 모션재생 (stageIndex 를 고려해 이쪽을 나중에 재생)
            player.play(Player.playRequest(`actor-${toActorOrder}`, Player.c_animations.ABILITY));
        }
    })

    // 어빌리티 슬라이더에서 어빌리티 클릭
    $('#abilitySlider .ability-icon').on('click', onAbilityIconClicked);

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
    });

    // 어빌리티 슬라이더에서 스테이터스 상세확인
    $('.show-status-info-button-wrapper').on('click', function () {
        openStatusInfoWrapper($(this).closest('.status-container'));
    })

    // 가드버튼 이벤트 등록
    $('.guard-button').each(function (index, guardButton) {
        let pressStart = 0;
        const executionDelay = 500; // 최소 실행 딜레이
        const longPressThreshold = 800; // 길게누르기 지연시간
        let longPressTimer = null; // 길게누르기 자동실행 타이머
        let mouseUpTriggered = false; // 길게누르기 자동실행으로 mouseup 트리거 됫는지 확인하는 플래그

        $(guardButton).on('mousedown touchstart', function () {
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

    // 포션 아이콘 클릭
    $('.potion-icon-container .potion-icon-wrapper').on('click', function () {
        let potionType = $(this).attr('data-potion-type'); // single, all, elixir
        let potionInfo = '';
        let $potionTargetRadioContainer = $('.potion-detail-container .potion-target-radio-container');
        switch (potionType) {
            case 'single':
                potionInfo = '파티멤버 1명의 체력을 절반 회복합니다.';
                $potionTargetRadioContainer.show();
                break;
            case 'all' :
                potionInfo = '파티멤버 전체의 체력을 절반 회복합니다.';
                $potionTargetRadioContainer.hide();
                break;
            case 'elixir':
                potionInfo = '사망한 파티 멤버가 부활하며, 모든 파티멤버의 체력을 전부 회복합니다.';
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
        processMoveClick(potion);
        $('#potionModal .close-button').click();
    })

    // 페이탈 체인 클릭
    $('.fatal-chain-gauge-wrapper').on('click', function () {
        if (player.locked) return;
        openAbilityInfoModal(gameStateManager.getState('fatalChain'));
    })

    // 공격버튼 클릭
    $('#attackButton').on('click', onAttackButtonClicked);

    // 방 나가기 이벤트 등록
    $('.exit-room-button').on('click', function () {
        if (!confirm("방에서 퇴장하면 클리어시 획득 가능한 아이템이나 공헌도를 모두 잃습니다")) return;
        $('#exitRoomForm').submit();
    })

    // 어빌리티 레일 mutationObserver 등록
    const abilityRailMutationObserver = new MutationObserver((entries) => {
        console.warn('abilityRailMutationObserver entries = ', entries);
        let $abilityRail = $(entries[0].target); // #abilityRail
        let $addedRailItem = $(entries[0].addedNodes);
        let $removedRailItem = $(entries[0].removedNodes);
        let isExecuted = false; // 처리 수행이 있었다면 true 로 반환

        if ($abilityRail.children('.rail-item').length < 1) return false; // 남은 아이템 없음

        let $latestRailItem = $abilityRail.children('.rail-item').first();
        let railItemType = $latestRailItem.attr('data-rail-item-type');

        if (($addedRailItem.hasClass('rail-item-1')) // 첫 아이템 추가
            || $removedRailItem.hasClass('executed') // 아이템 실행이 완료된 후 삭제됨 -> 다음 아이템 실행
        ) {
            let actorId = $latestRailItem.attr('data-actor-id');
            console.log('[#abilityRail.mutationObserver] railItemType = ', railItemType);
            switch (railItemType) {
                case 'ABILITY':
                case 'FATAL_CHAIN':
                case 'SUMMON':
                    let moveId = $latestRailItem.attr('data-move-id');
                    if (railItemType === 'FATAL_CHAIN') actorId = stage.gGameStatus.actorIds.slice(1).find(id => !!id); // 현재 프론트 캐릭터중 첫번째 캐릭터의 id 로 설정
                    setTimeout(() => requestMove(actorId, moveId, railItemType), Constants.Delay.globalMoveDelay);
                    isExecuted = true;
                    break;

                case 'ATTACK':
                    $('.ability-rail-wrapper .rail-item').eq(0).remove(); // 공격은 실행즉시 레일에서 삭제
                    setTimeout(() => requestTurnProgress(), Constants.Delay.globalMoveDelay);
                    isExecuted = true;
                    break;

                case 'POTION':
                    let potionType = $latestRailItem.attr('data-additional-type');
                    setTimeout(() => requestPotion(potionType, actorId), Constants.Delay.globalMoveDelay);
                    isExecuted = true;
                    break;

                default:
                    console.log('invalid railItemType ', railItemType);
                    break;
            }
        } else { // 아이템이 실행 없이 삭제됨
            isExecuted = false;
            if (railItemType === 'ATTACK') {
                onAttackButtonClicked();
            }
        }

        if (isExecuted) $latestRailItem.addClass('executed'); // 수행 여부 마킹 (remove 시, 다음 rail-item 자동 수행시 확인)
        return isExecuted;
    })
    abilityRailMutationObserver.observe(document.querySelector('#abilityRail'), {childList: true});

})