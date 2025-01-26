function processAbility(charOrder, abilityOrder) {

    // let globalAbilityAudioPlayer = new Audio();
    // let abilityAudioPlayers = [new Audio(), new Audio()]; // 최대 두개

    console.log('[processAbility] start process charOrder = ' + charOrder + 'abilityOrder = ' + abilityOrder);

    // 어빌리티 누름
    let partySelector = '.party-' + charOrder;
    let motionAbilityVideo = $(partySelector + '.motion-ability-' + abilityOrder);
    let idleMotionVideo = $(partySelector + '.motion-idle');

    // 오디오 생성
    // 이거 캐싱하도록 했는데 나중에 통신이랑 맞춰서 다시 테스트하고 최종결정.
    let audioPlayers = new Map();
    let audioInfos = $(partySelector + '-audio.ability-' + abilityOrder).toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    audioPlayers.set('char', new AudioPlayer(audioInfos));
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());
    let charAudioPlayer = audioPlayers.get('char');

    // 오디오 플레이어에 SE src 로드 (속도를 위해 통신전에 미리 로드)

    charAudioPlayer.loadSounds(audioInfos).then(() => {

        // TODO 통신
        //...
        let data = null; // 받아온 데이터
        let hasDamage = true;
        let charIndex = charOrder - 1; // order 와 달리 서버에서 받은거 접근할 index
        let abilityIndex = abilityOrder - 1;
        let responseAbilityData = responseData.chara[charIndex].ability[abilityIndex];
        let abilityHitCount = responseAbilityData.abilityHitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
        let abilityEffectCount = responseAbilityData.abilityEffectCount; // 어빌리티 이펙트수 (SE 재생 관련)
        let abilityDamages = responseAbilityData.damages;
        let abilityPlaybackSpeed = responseAbilityData.abilityPlaybackSpeed;
        let buffs = responseAbilityData.buffs;
        let debuffs = responseAbilityData.deBuffs;
        let hasBuff = buffs.length > 0;
        let hasDebuff = debuffs.length > 0;
        let hasMotion = responseAbilityData.hasMotion; // 캐릭터 모션 유무
        let isMotionFullSize = responseAbilityData.isMotionFullSize; // 캐릭터 모션이 풀사이즈로 출력되야하는지 여부

        // 어빌리티 후 행동
        let postAction = null;
        $.each(buffs, function (index, buff) {
            postAction = buff.postAction ? buff.postAction : null;
            if (postAction) return false; // postAction 찾으면 바로중지
        });

// 종료처리 이벤트 추가
        let abilityEndValue = null;
        if (postAction) {
            abilityEndValue = 'postAction';
        } else if (hasDebuff) {
            abilityEndValue = 'debuffs';
        } else if (hasBuff) {
            abilityEndValue = 'buffs';
        } else {
            abilityEndValue = 'motion';
        }
        $("input[name='abilityProcessingTask']").one('change', function () {
            if ($("input[name='abilityProcessingTask']:checked").val() === abilityEndValue) {
                console.log('[processingAbility] ENDED, ablityEndValue = ' + abilityEndValue);

                $('.ability-rail-wrapper .rail-ability').get(0).remove();
                let $processedAbility = $('.ability-panel.character-' + charOrder + ' .ability-' + abilityOrder);
                $processedAbility.find('.ability-overlay').show();
            }
        })


// 데미지 채우기
        abilityDamages.forEach(function (item, index) {
            let $abilityDamageElement = $('<div class="ability-damage' + ' ability-damage-' + index + '"' + '>' + item + '</div>')
            $('.ability-damage-wrapper').prepend($abilityDamageElement);
        })

// 어빌리티 모션 길이로 이펙트 길이 및 타수당 길이 구하기
        let abilityDuration = motionAbilityVideo.data('duration'); // 어빌리티 모션 재생 길이
        // console.log('abilityDuration = ' + abilityDuration);
        abilityDuration = abilityPlaybackSpeed > 0 ? abilityDuration / abilityPlaybackSpeed : abilityDuration; // 배속 걸려있을경우 적용
        abilityDuration = abilityDuration * 1000 - 300; //  ms 로 변환 (및 딜레이 보정)
        let abilityEffectDuration = abilityEffectCount === 1 ? 0 : abilityDuration / abilityEffectCount; // 어빌리티 이펙트 당 재생길이
        let abilityHitDuration = abilityHitCount === 1 ? 0 : abilityDuration / abilityHitCount; // 어빌리티 히트 당 재생길이
        abilityHitDuration = responseAbilityData.abilityHitDuration > 0 ? responseAbilityData.abilityHitDuration / abilityHitCount : abilityHitDuration; // 서버에서 보낸 값이 있으면 대체 (히트 속도가 이펙트와 다르거나 딜레이가 있는 캐릭터용)

// abilityEffect 이펙트 반복작업 : 사운드 재생
        let abilitySePlayCount = 0;
        let abilitySePlayInterval = setInterval(function () {

            // SE 재생
            charAudioPlayer.playAllSounds();

            //인터벌 클리어
            if (++abilitySePlayCount >= abilityEffectCount) {
                clearInterval(abilitySePlayInterval);
            }
        }, abilityEffectDuration);

// abilityHit 히트수 반복작업 : 데미지 표시, 적 피격모션
        let abilityHitPlayCount = 0;
        if (abilityHitCount > 0) {
            let abilityHitProcessInterval = setInterval(function () {
                // 적 모션 재생
                playEnemyDamagedMotion();

                // 데미지 표시
                $('.ability-damage-wrapper .ability-damage-' + abilityHitPlayCount).fadeTo(10, 0.8).delay(600).fadeTo(400, 0);

                //인터벌 클리어
                if (++abilityHitPlayCount >= abilityHitCount) {
                    // 어빌리티 데미지모두 제거
                    setTimeout(function () {
                        $('.ability-damage-wrapper').children().remove();
                    }, 1000);

                    clearInterval(abilityHitProcessInterval);
                }
            }, abilityHitDuration);
        }

// 아군 어빌리티 모션 재생 (오디오 속도가 느리므로, 딜레이 걸고 재생
        setTimeout(function () {
            if (abilityPlaybackSpeed > 0) {
                motionAbilityVideo.get(0).playbackRate = 1.5;
            }
            if (hasMotion) {
                idleMotionVideo.addClass('hidden'); // 캐릭터 모션 있으면 idle 모션 숨김
            }
            motionAbilityVideo.removeClass('hidden').get(0).play();

            // 끝나고 이펙트 숨김 처리
            $(motionAbilityVideo).one('ended', function () {
                $(this).addClass('hidden');
                $('#abilityProcessingTask #playingMotion').click();
                if (hasMotion) {
                    idleMotionVideo.removeClass('hidden');
                }
            })

// 버프 이펙트 처리
            if (buffs && buffs.length > 0) {
                // 버프 요소 내용 채우기
                buffs.forEach(function (buff, index) {
                    let buffTargets = buff.targets;
                    buffTargets.forEach(function (buffTarget, index) {
                        let $effectContainer = $('.status-effect-container-' + buffTarget + ' .status-effect-wrapper');
                        let $statusEffect = $('' +
                            '<div class="status-effect status-effect-' + index + '">\n' +
                            '  <img src="' + buff.iconSrc + '">\n' +
                            '  <span class="status-effect-text">' + buff.effectText + '</span>\n' +
                            '</div>')
                        if ($statusEffect.find('img').attr('src').length < 1) {
                            $statusEffect.find('.status-effect-text').addClass('none-icon'); // 스킬아이콘 없으면 강조
                        }
                        $effectContainer.prepend($statusEffect);
                    })
                });
                // 버프 이펙트 등록 (딜레이를 거꾸로 줌)
                $(motionAbilityVideo).one('ended', function () {
                    [1, 2, 3, 4].forEach(function (charOrder, index) {
                        $($('.status-effect-container-' + charOrder + ' .status-effect')).each(function (index, item) {
                            // 페이드 길이 1100 + index * 50 , 3번째 길이 1250
                            let additionalStartDelay = index / 3 >= 1 ? 1250 + 100 : 0; // 3개 이상일경우 딜레이 추가 (3번째가 사라지는 시간 + 안전마진)
                            let removeDelay = (1100 * (Math.floor(index / 3) + 1)) + (50 * index) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
                            setTimeout(() => {
                                $(this).fadeTo(100, 0.9).delay(600).fadeTo(400, 0);
                                setTimeout(() => {
                                    $(this).remove();
                                }, removeDelay);
                            }, additionalStartDelay + (index * 50))
                        })
                    })
                    let buffEffectEndTimeout = (1100 * (Math.floor(buffs.length / 3) + 1)) + (50 * buffs.length) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
                    setTimeout(() => {
                        $('#abilityProcessingTask #playingBuffs').click();
                    }, buffEffectEndTimeout);
                })
            }// 버프 이펙트 처리 끝

// 디버프 이펙트 처리
            let buffEffectEndTimeout = (1100 * (Math.floor(buffs.length / 3) + 1)) + (50 * buffs.length) - 300 // buffEffectEndTimeout 과 동일하나 빠른진행을 위해 -300
            let debuffStartDelay = buffs.length > 0 ? 1000 : 0; // 버프 없으면 즉시시작
            if (debuffs && debuffs.length > 0) {
                // 디버프 채우기
                let $effectContainer = $('#enemyStatusEffectContainer .status-effect-wrapper');
                debuffs.forEach(function (debuff, index) {
                    $effectContainer.prepend($('' +
                        '<div class="enemy-status status-effect status-effect-' + index + '">\n' +
                        '  <img src="' + debuff.iconSrc + '">\n' +
                        '  <span class="status-effect-text">' + debuff.effectText + '</span>\n' +
                        '</div>')
                    )
                });
                $(motionAbilityVideo).one('ended', function () {
                    $($('#enemyStatusEffectContainer' + ' .status-effect').get().reverse()).each(function (index, item) {
                        setTimeout(() => {
                            $(this).fadeTo(100, 1).delay(600).fadeTo(400, 0);
                        }, debuffStartDelay + (index * 50))
                        setTimeout(() => {
                            $(this).remove();
                            $('#abilityProcessingTask #playingDebuffs').click();
                        }, debuffStartDelay + 1500);
                    })
                })
            } // 디버프 이펙트 처리 끝

// 어빌리티 후 행동 실행
            if (postAction) {
                let postActionDelay = 0;
                postActionDelay += buffs.length > 0 ? 1250 : 0;
                postActionDelay += debuffs.length > 0 ? 1250 : 0;

                // 통상공격 시
                if (postAction === 'ATTACK') {
                    // 오디오 미리로드
                    let thisCharAudioPlayer = new AudioPlayer();
                    thisCharAudioPlayer.loadSound($('.party-' + charOrder + '-audio').attr('src'))

                    // 통신을 여기서 안하면 processCharacterAttack 내부에서 하도록 아예 수정해됨
                    // TODO 통신
                    //...
                    let hitCount = 3;

                    // 모션 끝나면 예약
                    $(motionAbilityVideo).one('ended', function () {
                        setTimeout(() => {
                            audioPlayers.delete('char');
                            audioPlayers.set(charOrder, thisCharAudioPlayer);
                            processCharacterAttack(charOrder, hitCount, 1, audioPlayers);
                            let $attackMotionVideo = $(partySelector + '.motion-attack-' + hitCount);

                            //TODO 공격 후행동 끝나고로 변경해야함
                            $attackMotionVideo.one('ended', function () {
                                $('#abilityProcessingTask #playingPostAction').click();
                            })
                        }, postActionDelay)
                    });
                }
            } // 어빌리티 후 행동 끝

        }, 100); // 아군 어빌리티 모션 끝 및 버프, 디버프, 어빌리티 후행동 종료

    })

    // 필요 메서드

    // 적 피격 모션 재생
    function playEnemyDamagedMotion() {
        $('.motion-enemy-idle').addClass('hidden');
        let motionDamagedElement = $('.motion-enemy-damaged').removeClass('hidden').get(0);
        motionDamagedElement.currentTime = 0; // 빼면 부자연스러워짐
        motionDamagedElement.play();
        $(motionDamagedElement).one('ended', function () {
            $(this).addClass('hidden')
            $('.motion-enemy-idle').removeClass('hidden');
        })
        return true;
    }

    // 필요 데이터

    let responseData = {
        chara: [
            {
                // 주인공
                ability: [
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/mc/paladin/status/status-paladin-ability-1.png',
                                effectText: '데미지 컷(80%)',
                                infoText: '피격 데미지를 80% 컷 하는 상태'
                            },
                        ],
                        deBuffs: []
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/mc/paladin/status/status-paladin-ability-2.png',
                                effectText: '피격 데미지 감소',
                                infoText: '피격 데미지가 감소한 상태'
                            },

                        ],
                        deBuffs: []
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1],
                                iconSrc: '/static/assets/img/mc/paladin/status/status-paladin-ability-3-1.png',
                                effectText: '감싸기',
                                infoText: '적의 공격을 아군 대신 받는 상태'
                            },
                            {
                                targets: [1],
                                iconSrc: '/static/assets/img/mc/paladin/status/status-paladin-ability-3-2.png',
                                effectText: '베리어',
                                infoText: '피격 데미지를 베리어 수치만큼 무시하는 상태'
                            }
                        ],
                        deBuffs: []
                    },
                ]
            },
            // 야치마
            {
                ability: [
                    {
                        abilityHitCount: 6,
                        abilityPlaybackSpeed: 1.5,
                        abilityEffectCount: 6,
                        damages: [1000, 2000, 3000, 4000, 5000, 6000],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/yachima/status/status-yachima-ability-1.png',
                                effectText: '추가데미지 A',
                                infoText: '일반 공격시 추가데미지가 발생하는 상태 (A)'
                            }
                        ],
                        deBuffs: [
                            {
                                iconSrc: '/static/assets/img/ch/yachima/status/status-de-yachima-ability-1-5.png',
                                effectText: '디그레이드 스피넬',
                                infoText: '방어력이 감소하며, 받는 데미지가 증가한 상태 ( 디그레이드 스피넬을 발동할때마다 Lv 상승 / 최대 Lv5 / 소거불가 )'
                            }
                        ]
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/yachima/status/status-yachima-ability-2-1.png',
                                effectText: '데미지 경감',
                                infoText: '피격 데미지가 30% 감소한 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/yachima/status/status-yachima-ability-2-2.png',
                                effectText: '디스펠 가드',
                                infoText: '강화효과 무효화(디스펠) 을 1회 무시하는 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/yachima/status/status-yachima-ability-2-3.png',
                                effectText: '방어력 증가',
                                infoText: '방어력이 증가한 상태'
                            }

                        ],
                        deBuffs: []
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [2],
                                iconSrc: '/static/assets/img/ch/yachima/status/status-yachima-ability-3.png',
                                effectText: '가하는 데미지 상승',
                                infoText: '가하는 데미지가 상승한 상태'
                            },
                            {
                                targets: [2],
                                iconSrc: '',
                                effectText: '통상공격 즉시실행',
                                infoText: '',
                                postAction: 'ATTACK'
                            }
                        ],
                        deBuffs: []
                    },
                ]
            },
            // 인다라
            {
                ability: [
                    {
                        abilityHitCount: 3,
                        abilityEffectCount: 3,
                        damages: [1000, 2000, 3000],
                        buffs: [],
                        deBuffs: [
                            {
                                iconSrc: '/static/assets/img/ch/indara/status/status-de-indara-ability-1-1-4.png',
                                effectText: '흉역',
                                infoText: '인다라가 배틀멤버일 시 Lv에 비례해 방어력이 감소하고, 어빌리티 피격 데미지가 상승/Lv10일 시, 소거 가능한 강화효과의 유지 턴을 감소시키는 상태 (최대 Lv10, 회복불가)'
                            },
                            {
                                iconSrc: '/static/assets/img/ch/indara/status/status-de-indara-ability-1-2.png',
                                effectText: '더블어택 감소',
                                infoText: '더블어택이 발생할 확률이 감소한 상태 (누적)'
                            },
                            {
                                iconSrc: '/static/assets/img/ch/indara/status/status-de-indara-ability-1-3.png',
                                effectText: '트리플어택 감소',
                                infoText: '트리플어택이 발생할 확률이 감소한 상태 (누적)'
                            },
                            {
                                iconSrc: '/static/assets/img/ch/indara/status/status-de-indara-ability-1-4-8.png',
                                effectText: '극독',
                                infoText: '매 턴마다 체력이 감소하는 상태 (최대 Lv10, 회복불가)'
                            }
                        ]
                    },
                    {
                        abilityHitCount: 4,
                        abilityEffectCount: 1,
                        abilityHitDuration: 500,
                        damages: [1000, 2000, 3000, 4000],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '',
                                effectText: '오의 게이지 상승',
                                infoText: ''
                            },
                        ],
                        deBuffs: []
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/indara/status/status-indara-ability-3.png',
                                effectText: '어빌리티 가하는 데미지 상승',
                                infoText: '어빌리티가 가하는 데미지가 상승한 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/indara/status/status-indara-ability-3.png',
                                effectText: '어빌리티 데미지 상한 상승',
                                infoText: '어빌리티의 데미지 상한이 상승한 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/indara/status/status-indara-ability-3.png',
                                effectText: '어빌리티 데미지 상승',
                                infoText: '어빌리티의 데미지가 상승한 상태'
                            },
                            {
                                targets: [3],
                                iconSrc: '',
                                effectText: '오의 즉시 사용 가능',
                                infoText: ''
                            }
                        ],
                        deBuffs: []
                    },
                ]
            },
            // 하이라
            {
                ability: [
                    {
                        hasMotion: true,
                        isMotionFullSize: true,
                        abilityHitCount: 10,
                        abilityEffectCount: 1,
                        damages: [1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000],
                        buffs: [
                            {
                                targets: [4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-1.png',
                                effectText: '재공격',
                                infoText: '턴 진행시 공격행동을 2회 진행하는 상태'
                            }
                        ],
                        deBuffs: []
                    },
                    {
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-2.png',
                                effectText: '일반공격 주는 데미지 상승',
                                infoText: '일반공격이 주는 데미지가 상승한 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-2.png',
                                effectText: '흡수',
                                infoText: '일반공격이 주는 데미지의 일부로 자신의 체력을 회복하는 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-2.png',
                                effectText: '받는 데미지 경감',
                                infoText: '받는 데미지가 경감되는 상태'
                            },
                            {
                                targets: [1, 2, 3, 4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-2.png',
                                effectText: '디스펠 가드',
                                infoText: '강화효과 무효화(디스펠) 을 1회 무시하는 상태'
                            }
                        ],
                        deBuffs: []
                    },
                    {
                        hasMotion: true,
                        isMotionFullSize: false,
                        abilityHitCount: 0,
                        abilityEffectCount: 1,
                        damages: [],
                        buffs: [
                            {
                                targets: [1, 2, 3],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-1.png',
                                effectText: '재공격',
                                infoText: '턴 진행시 공격행동을 2회 진행하는 상태'
                            },
                            {
                                targets: [4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-2.png',
                                effectText: '감싸기',
                                infoText: '적의 공격을 아군 대신 받는 상태'
                            },
                            {
                                targets: [4],
                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-3.png',
                                effectText: '피해 무시',
                                infoText: '적의 공격 데미지와 약체효과를 무시하는 상태'
                            },
                        ],
                        deBuffs: []
                    },
                ]
            }
        ]


    }


}