var config = null; // contains the loaded config
var Game = null; // contains various url, follow the in-game format
// var player = null; // the player instance
var loader = null; // the loaded instance
var _ = null; // contains underscore 3rd party library
var cjsStage = null;

// start load Actor
function loadActor(animation, override_config = null) {
    // if a config is provided
    if (override_config != null) {
        // process directly
        processConfig(override_config, animation);
    } else { // not used
        // else load the configuration from the json folder
        fetchJSON("json/config.json?" + Date.now()).then((value) => {
            processConfig(value, animation);
        });
    }

    playerStartFire(animation);
}

// next step after loading the config
function processConfig(configParam, animation) {
    config = configParam;
    if (config == null)
        throw new Error("No configuration loaded");
    try {
        // intend to replace Game
        let new_game = {};
        // read the cors proxy, if set
        // config.use_game_config points to which url config we use
        let cors_proxy = config.game[config.use_game_config]["corsProxy"] ?? "";
        // for each key in
        for (const [key, val] of Object.entries(config.game[config.use_game_config])) {
            if (key != "corsProxy") {
                if (val != null)
                    new_game[key] = val.replace("CORS/", cors_proxy); // we set the cors proxy in the url
                else
                    new_game[key] = null;
            }
        }
        Game = new_game; // set to Game
    } catch (err) {
        new Error("[processConfig] error", err)
    }
}

var require_is_configured = false;

function playerStartFire(animation) {
    // setup requirejs paths
    if (!require_is_configured) {
        throw new Error("require is not loaded");
    }
    // load createjs
    require(["createjs"
    ], function ($, underscore, backbone) {
        // Note: GBF width is around 640 px, so this is ideal to avoid scaling
        // If you want to go higher than 900px, you must increase the canvas size
        // set_size can take an extra parameter to set the global scaling but it's untested
        // layout mode 삭제

        initPlayer(); // 한번만 실행됨

        let actorId = animation.name; // animations 는 버전별이고 우리는 1개만 사용, 이름 'actor0', 'actor1', ...
        let actor = new Actor(actorId);
        player.actors.set(actorId, actor);
        actor.setAnimation(animation);
    });
}


// MonkeyPatch createJs ===================================================================================
// apply various patches to modern createjs for compatibility with gbf
var _createjs_overloaded_func_ = {}; // store the original functions
var create_js_monkeypatch_applied = false;

function monkeypatch_createjs() {
    if (create_js_monkeypatch_applied) {
        return;
    }
    // new bitmap initialize
    _createjs_overloaded_func_["bitmap_init"] = window.createjs.Bitmap.prototype.initialize;
    window.createjs.Bitmap.prototype.initialize = function (image) {
        let tmp = this.sourceRect; // store the source rect
        // call the original function
        _createjs_overloaded_func_["bitmap_init"].call(this, image);
        if (tmp) this.sourceRect = tmp; // now set the source rect AFTER (to avoid a bug)
        // add bouding box logic
        add_bounding_box(this);
    };

    // add getStage method to DisplayObject
    window.createjs.DisplayObject.prototype.getStage = function () {
        return player.m_stage;
    }
    create_js_monkeypatch_applied = true;
}

// Code to add bounding boxes to the animations
var bounding_box_state = false;

// Function to add a bounding box to a Bitmap
function add_bounding_box(displayObject) {
    if (!displayObject || displayObject._bounding_box)
        return;
    // add a Shape in a custom parameter
    displayObject._bounding_box = new createjs.Shape();
    displayObject._bounding_box.mouseEnabled = false; // not sure if needed
    displayObject._bounding_box.visible = bounding_box_state; // set visibility according to global variable
    // on each tick, draw our box (see below)
    displayObject.on("tick", draw_object_bounding_box);
}

// Function to draw the bounding box
function draw_object_bounding_box() {
    // update visibility if needed
    if (this._bounding_box.visible != bounding_box_state) {
        this._bounding_box.visible = bounding_box_state;
    }
    // if visible
    if (this._bounding_box.visible) {
        // update parent if needed
        if (this._bounding_box.parent != this.parent) {
            // remove from box parent (if it exists)
            if (this._bounding_box.parent != null)
                this._bounding_box.parent.removeChild(this._bounding_box);
            // move to object parent (if it exists)
            if (this.parent != null)
                this.parent.addChild(this._bounding_box);
        }
        if (this._bounding_box.parent != null) // if parent exists (aka it's displayed)
        {
            const bounds = this.getBounds?.(); // get object bound
            if (bounds) {
                // draw a green rectangle copying those bounds
                // don't worry about transformations, they are propagated from parents
                this._bounding_box.graphics
                    .clear()
                    .setStrokeStyle(1)
                    .beginStroke("green")
                    .drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
                    .endStroke();
            }
        }
    }
}

// MonkeyPatch createJs ===================================================================================
