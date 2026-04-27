define([], (function () {
    if (!window.audio) {
        window.audio = {

            disabled: false, // if true, audios won't play
            audio_cache: {}, // cache audio files, for reuse
            instances: [], // currently playing files
            loopingInstances: [],
            volume: 1.0,

            masterVolume: 1.0, // volume 과 연동
            bgmVolume: 1.0,
            voiceVolume: 1.0,

            // BGM 재생용
            bgmSrc: null,
            _ac: null, // AudioContext
            _bgmSourceNode: null,
            _bgmGainNode: null,
            _bufferCache: {},

            // the following functions are needed for the animation playback
            // they are called from inside GBF files
            play: function (file, {isLocal = false, audioType = '', playType = ''} = {}) {
                // console.debug(`[audio.play] file = ${file}, isLocal = ${isLocal}, audioType = ${audioType}, master/bgm/voiceVolume = ${[this.masterVolume, this.bgmVolume, this.voiceVolume]}`);
                if (!file
                    || this.disabled
                    || (file.includes('voice') && (!file.includes('summon')))) return;
                if (audioType === 'voice' && this.voiceVolume * this.masterVolume === 0) return;
                if (audioType === 'bgm' && this.bgmVolume * this.masterVolume === 0) return;

                try {
                    let audio = null;
                    if (file in this.audio_cache) { // if cached
                        audio = this.audio_cache[file];
                        let i = this.instances.indexOf(audio);
                        if (i !== -1) this.instances.splice(i, 1); // if already playing, remove from playing
                        audio.currentTime = 0; // reset head position

                    } else {
                        let filename = isLocal ? file : Game.soundUri + "/" + file;
                        audio = new Audio(filename);
                        if (isLocal) audio.localSrc = filename; // added for local file playing

                        if (audioType === 'voice') {
                            audio.volume = this.voiceVolume * this.masterVolume;
                            audio.audioType = 'voice';
                        } else {
                            audio.volume = this.masterVolume;
                        } // bgm 은 별도로 gain 설정

                        this.audio_cache[file] = audio; // add to cache
                        audio.addEventListener('ended', this.audio_ended); // add listener
                    }

                    audio.muted = this.disabled; // set muted attribute
                    // audio.playbackRate = 1.0; // set speed (seems to work between 0.01 ~ 4.0)
                    // audio.muted = !player.is_audio_enabled(); // set muted attribute
                    // audio.playbackRate = player.get_speed(); // set speed (seems to work between 0.01 ~ 4.0)

                    audio.play(); // play the audio
                    this.instances.push(audio); // add to playing
                    return audio;

                } catch (err) {
                    console.error("Error attempting to play " + file, err);
                }
            },
            audio_ended: function () { // automatic cleanup called by the event listener
                let audioObj = window.audio;
                let i = audioObj.instances.indexOf(this);
                // console.log("audio ended audio.src = ", this.src, " window.audio.instances = ", [...audioObj.instances], ' window.audio.loopingInstances = ', [...audioObj.loopingInstances], ' this.localSrc = ', this.localSrc ?? '');
                if (i !== -1) {
                    let isLooping = audioObj.loopingInstances.includes(this.src); // loop from gbf
                    if (isLooping) {
                        this.currentTime = 0;
                        this.play();
                    } else {
                        this.pause();
                        window.audio.instances.splice(i, 1);
                    }
                }
            },

            stop: function () {
            }, // leave it unimplemented, we handle it on our own
            getLocalVolume: function (name) {
                return 1.0;
            }, // leave it unimplemented
            setLocalVolume: function (alias, value) {
            }, // leave it unimplemented
            // below are our custom functions implemented for the player purpose
            reset: function () {
                this.stop_all();
                this.instances = [];
                this.audio_cache = {};
            },
            stop_all: function () { // "stop" and clear all playing audios
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].pause();
                this.instances = [];
            },
            pause_all: function () { // pause all
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].pause();
            },
            resume_all: function () { // resume all
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].play();
            },
            set_playback_speed: function () { // set playback speed
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].playbackRate = player.playbackRate;
            },
            set_master_volume: function (value) {
                // check bounds and set volume
                if (value > 1.0)
                    this.volume = 1.0;
                else if (value < 0.0)
                    this.volume = 0.0;
                else
                    this.volume = value;
                // apply to playing instances
                for (let i = 0; i < this.instances.length; ++i) {
                    this.instances[i].volume = this.volume;
                }
            },
            update_mute: function (isMute = true) { // update unmute status
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].muted = isMute;
                // this.instances[i].muted = !player.is_audio_enabled();
            },

            // loop from gbf (quest_clear... )
            setAliasAndRepeat(filename) {
                this.loopingInstances.push(Game.soundUri + "/" + filename);
                this.play(filename);
            },
            removeRepeatAudio(filename) {
                let i = this.loopingInstances.indexOf(Game.soundUri + "/" + filename);
                if (i != -1) {
                    this.loopingInstances.splice(i, 1);
                }
            },

            // =========================================================================================================

            initVolume: function () {
                let $masterVolumeRange = $('.volume-range-container #masterVolumeRange');
                let $masterVolumeRangeValue = $('.volume-range-container #masterVolumeRangeValue');
                let $voiceVolumeRange = $('.volume-range-container #voiceVolumeRange');
                let $voiceVolumeRangeValue = $('.volume-range-container #voiceVolumeRangeValue');
                let $bgmVolumeRange = $('.volume-range-container #bgmVolumeRange');
                let $bgmVolumeRangeValue = $('.volume-range-container #bgmVolumeRangeValue');

                let storedMasterVolume = localStorage.getItem('masterVolume');
                if (storedMasterVolume) {
                    $masterVolumeRangeValue.text(storedMasterVolume +'%');
                    $masterVolumeRange.val(storedMasterVolume);
                    window.audio.setVolume('master', Number(storedMasterVolume) / 100);
                } else {
                    $masterVolumeRangeValue.text('50%');
                    $masterVolumeRange.val(50);
                    window.audio.setVolume('master', 0.5);
                }

                let storedVoiceVolume = localStorage.getItem('voiceVolume');
                if (storedVoiceVolume) {
                    $voiceVolumeRangeValue.text(storedVoiceVolume +'%');
                    $voiceVolumeRange.val(storedVoiceVolume);
                    window.audio.setVolume('voice', Number(storedVoiceVolume) / 100);
                } else {
                    $voiceVolumeRangeValue.text('85%');
                    $voiceVolumeRange.val(85);
                    window.audio.setVolume('voice', 0.85);
                }

                let storedBgmVolume = localStorage.getItem('bgmVolume');
                if (storedBgmVolume) {
                    $bgmVolumeRangeValue.text(storedBgmVolume + '%');
                    $bgmVolumeRange.val(storedBgmVolume);
                    window.audio.setVolume('bgm', Number(storedBgmVolume) / 100);
                } else {
                    $bgmVolumeRangeValue.text('70%');
                    $bgmVolumeRange.val(70);
                    window.audio.setVolume('bgm', 0.7);
                }
            },

            /**
             * set volume
             * @param type {String} master, bgm, voice
             * @param value {number} float 0.0 ~ 1.0
             */
            setVolume: function (type = 'master', value) {
                value = Math.max(0, Math.min(1, value));

                const prev = {bgm: this.bgmVolume, voice: this.voiceVolume, master: this.masterVolume};

                if (type === 'bgm') this.bgmVolume = value;
                else if (type === 'voice') this.voiceVolume = value;
                else this.masterVolume = value;

                if (this.disabled) return; // 사운드 비활성화시, 볼륨값만 변동

                // BGM
                if (this.bgmVolume === 0 || this.masterVolume === 0) {
                    if (this._bgmGainNode) this._bgmGainNode.gain.value = 0;
                } else if (type === 'bgm' && prev.bgm === 0 && this.bgmSrc) { // 볼륨 0 -> N
                    if (this._bgmGainNode) {
                        this._bgmGainNode.gain.value = this.masterVolume * this.bgmVolume; // gain 0 후 정지되기 전
                    } else {
                        // gain 0 후 정지됨 -> 새로재생
                        let src = this.bgmSrc;
                        this.bgmSrc = null;
                        this.playBgm(src);
                    }
                } else if (this._bgmGainNode) {
                    this._bgmGainNode.gain.value = this.masterVolume * this.bgmVolume;
                }

                // 일반 + voice
                this.instances.forEach(audio => {
                    audio.volume = audio.audioType === 'voice'
                        ? this.masterVolume * this.voiceVolume
                        : this.masterVolume;
                });
            },

            updateDisabled: function (disabled = true) {
                this.disabled = disabled; // 나머지 audio 는 play() 단에서 적용

                if (disabled) {
                    this._stopBgmNode();
                } else if (this.bgmSrc) {
                    // bgm 초기화 후 다시재생
                    let src = this.bgmSrc;
                    this.bgmSrc = null;
                    this.playBgm(src);
                }
            },

            /**
             * play bgm
             * @param filename
             * @param offset
             */
            playBgm(filename, offset = 0) {
                if (this.bgmSrc === filename) return;
                const prevSrc = this.bgmSrc;
                this.bgmSrc = filename;

                this._loadBuffer(filename).then(buffer => {
                    if (this.bgmSrc !== filename) return;
                    this._stopBgmNode();
                    this._startBgmNode(buffer, offset);
                    if (prevSrc && prevSrc !== filename) {
                        delete this._bufferCache[prevSrc]; // 새 bgm 로드 완료되면 이전 bgm 캐시 삭제
                    }
                }).catch(e => console.error('[audio] BGM load failed:', filename, e));
            },
            _loadBuffer(src) {
                if (!this._ac) this._ac = new (window.AudioContext || window.webkitAudioContext)();
                if (this._bufferCache[src]) return Promise.resolve(this._bufferCache[src]);
                return fetch(src)
                    .then(r => r.arrayBuffer())
                    .then(buf => this._ac.decodeAudioData(buf))
                    .then(decoded => (this._bufferCache[src] = decoded, decoded));
            },

            _startBgmNode(buffer, offset = 0) {
                const gain = this._ac.createGain();
                gain.gain.value = this.disabled ? 0 : (this.bgmVolume * this.masterVolume);
                gain.connect(this._ac.destination);

                const src = this._ac.createBufferSource();
                src.buffer = buffer;
                src.loop = true;
                src.connect(gain);
                src.start(this._ac.currentTime, offset % buffer.duration);

                this._bgmGainNode = gain;
                this._bgmSourceNode = src;
            },

            _stopBgmNode() {
                if (this._bgmSourceNode) {
                    try {
                        this._bgmSourceNode.stop(0);
                    } catch (e) {
                    }
                    this._bgmSourceNode.disconnect();
                    this._bgmSourceNode = null;
                }
                if (this._bgmGainNode) {
                    this._bgmGainNode.disconnect();
                    this._bgmGainNode = null;
                }
            },

            removeBgm() {
                this.bgmSrc = null;
                this._stopBgmNode();
            },

            /**
             * 유틸: bgm 이동
             * @param offset
             */
            seekBgm(offset) {
                let ac = this._ac;
                let old = this._bgmSourceNode;
                if (!old?.buffer || !this._bgmGainNode) return;

                try {
                    old.stop();
                } catch (e) {
                }
                old.disconnect();

                let src = ac.createBufferSource();
                src.buffer = old.buffer;
                src.loop = true;
                src.connect(this._bgmGainNode);
                src.start(0, offset % old.buffer.duration); // duration 넘으면 wrap
                this._bgmSourceNode = src;
            },

        }
    }
    // for test
    // window.audio.setVolume('master', 0.75);
    // window.audio.setVolume('bgm', 0.7);
    return window.audio;
}));