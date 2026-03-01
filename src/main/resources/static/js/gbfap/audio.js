define([], (function () {
    if (!window.audio) {
        window.audio = {

            disabled: false, // if true, audios won't play
            audio_cache: {}, // cache audio files, for reuse
            instances: [], // currently playing files
            loopingInstances: [],
            volume: 1.0,

            bgmSrc: null, // for bgm play only
            FADE_TIME: 1.0, // (s)

            // the following functions are needed for the animation playback
            // they are called from inside GBF files
            play: function (file, {isLocal = false} = {}) {
                if (!file || this.disabled || file.includes('voice'))
                    return;
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

                        this.audio_cache[file] = audio; // add to cache
                        audio.addEventListener('ended', this.audio_ended); // add listener
                    }

                    audio.volume = this.volume; // set volume to master
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

            audio_ended: function () { // automatic cleanup called by the event listener
                let audioObj = window.audio;
                let i = audioObj.instances.indexOf(this);
                // console.log("audio ended audio.src = ", this.src, " window.audio.instances = ", [...audioObj.instances], ' window.audio.loopingInstances = ', [...audioObj.loopingInstances], ' this.localSrc = ', this.localSrc ?? '');
                if (i !== -1) {
                    let isLooping = audioObj.loopingInstances.includes(this.src); // loop from gbf
                    let isBgm = audioObj.bgmSrc === this.localSrc;
                    if (isLooping || isBgm) {
                        this.currentTime = 0;
                        this.play();
                    } else {
                        this.pause();

                        // 페이드 아웃 관련 처리 삭제
                        this.fadeTime = null;
                        this.removeEventListener('timeupdate', this._bgmFadeOutHandler);

                        window.audio.instances.splice(i, 1);
                    }
                }
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
            update_mute: function (isMute = true) { // update unmute status
                for (let i = 0; i < this.instances.length; ++i)
                    this.instances[i].muted = isMute;
                // this.instances[i].muted = !player.is_audio_enabled();
            },
            updateDisabled: function (disabled = true) {
                this.disabled = disabled;
                if (this.disabled) {
                    this.stop_all();
                } else if (!!this.bgmSrc) {
                    let bgmSrc = this.bgmSrc;
                    this.bgmSrc = null;
                    this.playBgm(bgmSrc);
                }
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

            /**
             * play audio for bgm only
             * @param filename local src
             */
            playBgm(filename) {
                if (this.bgmSrc === filename) return; // deny already playing same file
                this.removeBgm(this.bgmSrc); // remove playing bgm
                this.bgmSrc = filename;
                setTimeout(() =>  {
                    let bgmAudio = this.play(filename, {isLocal: true});
                    if (!!bgmAudio) bgmAudio.addEventListener('timeupdate', this._bgmFadeOutHandler);
                }, this.FADE_TIME * 1000 / 2); // delay for fade out
            },
            removeBgm(filename) {
                this.bgmSrc = null;
                let currentBgm = this.instances.find(audio => audio.localSrc === filename);
                if (currentBgm) {
                    // set fadeTime attribute to force fadeout
                    currentBgm.fadeTime = currentBgm.currentTime + this.FADE_TIME;
                }
            },
            _bgmFadeOutHandler(event) { // fadeOut handler
                if (window.audio.volume <= 0) return; // CHECK cause problem with isFadingOut flag while set volume 0 when fading out
                let audio = event.target;
                let masterVolume = window.audio.volume;
                let timeLeft = !!audio.fadeTime ? audio.fadeTime - audio.currentTime : audio.duration - audio.currentTime; // has fadeTime, fadeTime as endTIme(duration)

                if (timeLeft <= window.audio.FADE_TIME) {
                    // start fadeOut
                    let timeRatio = timeLeft / window.audio.FADE_TIME;
                    let volumeScale = 0.2 + (0.8 * timeRatio); // (1.0 ~ 0.2)
                    let newVol = Math.max(0, volumeScale * masterVolume);

                    audio.isFadingOut = true;
                    audio.volume = Math.max(0, newVol);

                } else if (audio.isFadingOut) {
                    // set to masterVolume when fadeOut end
                    audio.isFadingOut = false;
                    audio.volume = masterVolume;
                }

                if (!!audio.fadeTime && audio.currentTime >= audio.fadeTime) {
                    // if audio has fadeTime, dispatch 'ended' when fadeout finished
                    audio.dispatchEvent(new Event('ended'));
                }
            },
        }
    }
    return window.audio;
}));