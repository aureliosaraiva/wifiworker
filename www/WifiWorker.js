var cordova = require('cordova');

var className = 'WifiWorker';

function isEnabled(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, className, 'isWifiEnabled', []);
}

function isDisabled(successCallback, errorCallback) {
    return isEnabled(function (enabled) {
        successCallback(!enabled);
    }, errorCallback);
}

function enableWifi(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, className, 'enableWifi', []);
}

function getConnectedNetwork(successCallback, errorCallback) {
    cordova.exec(function (network) {
        successCallback(network.bssid ? createWifiFromResult(network) : null);
    }, errorCallback, className, 'getConnectedNetwork', []);
}

function getConfiguredNetworks(successCallback, errorCallback) {
    cordova.exec(function (res) {
        successCallback(res.map(createWifiFromResult));
    }, errorCallback, className, 'getConfiguredNetworks', []);
}

function getScanResult(successCallback, errorCallback) {
    cordova.exec(function (res) {
        successCallback(res.map(createWifiFromResult).filter(function (wifi) {
            return !wifi.capabilities.isP2P();
        }));
    }, errorCallback, className, 'getScanResult', []);
}

function getNearbyNetworks(latitude, longitude, maxDistance, successCallback, errorCallback) {
    cordova.exec(function (res) {
        successCallback(res.map(createWifiFromResult));
    }, errorCallback, className, 'getNearbyNetworks', [latitude, longitude, maxDistance]);
}

function startScan(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, className, 'startScan', []);
}

/**
 * @param {Wifi} wifi
 * @param {Function} successCallback
 * @param {Function} errorCallback
 */
function connectToNetwork(wifi, successCallback, errorCallback) {

    errorCallback = (function (err, r) {
        return function (data) {
            if (!r) {
                r = true;
                return err(data);
            }
        };
    })(errorCallback);

    cordova.exec(function () {

        listenToWifiChange([
            Wifi.CHANGE_STATE.COMPLETED,
            Wifi.CHANGE_STATE.ERROR_AUTHENTICATING
        ], function (type, data) {

            switch (type) {
                case Wifi.CHANGE_STATE.COMPLETED:
                    successCallback(data);
                    break;
                case Wifi.CHANGE_STATE.ERROR_AUTHENTICATING:
                    errorCallback(data);
                    break;
            }

        }, true, errorCallback);

    }, errorCallback, className, 'connectToNetwork', [wifi.ssid]);

}

/**
 * @param {Wifi} wifi
 * @param {Function} successCallback
 * @param {Function} errorCallback
 */
function addNetwork(wifi, successCallback, errorCallback) {

    var data = [];

    if (wifi.bssid && wifi.ssid) {
        //data.push(wifi.bssid);
        data.push(wifi.ssid);
    } else {
        return false;
    }

    if (wifi.capabilities.getAuthType()) {

        //data.push(wifi.capabilities.getAuthType());

        if (wifi.capabilities.getAuthType() != Wifi.AUTH_TYPE.NONE) {
            data.push(wifi.password);
        }

    } else {
        return false;
    }

    cordova.exec(successCallback, errorCallback, className, 'addNetwork', data);
}

/**
 * @param {Wifi} wifi
 * @param {Function} successCallback
 * @param {Function} errorCallback
 */
function removeNetwork(wifi, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, className, 'removeNetwork', [wifi.ssid]);
}

/**
 * @param {Wifi} wifi
 * @param {Function} successCallback
 * @param {Function} errorCallback
 */
function removeNetworkPassword(wifi, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, className, 'removeNetworkPassword', [wifi.ssid]);
}

function createWifiFromResult(result) {
    var wifi = new Wifi(result.ssid, result.bssid, result.capabilities);

    //Sometimes the level comes as 0 when 1 is shown at the system`s wifi manager
    if (result.level == 0) {
        result.level = 1;
    }

    for (var property in result) {
        if (property == 'capabilities' || property == 'ssid') continue;
        if (result.hasOwnProperty(property)) {
            wifi[property] = result[property];
        }
    }
    return wifi;
}

/**
 * @param {String|Object} ssid
 * @param {String=} bssid
 * @param {String|Wifi.NetworkCapabilities=} capabilities
 * @param {String=} password
 * @constructor
 */
function Wifi(ssid, bssid, capabilities, password) {

    if (!bssid) {
        bssid = '';
    }

    if (!ssid) {
        ssid = '';
    }

    if (typeof ssid == 'object') {
        capabilities = ssid.capabilities;
        bssid = ssid.bssid;
        password = ssid.password;
        ssid = ssid.ssid;
    }

    if (ssid.charAt(0) === '"' && ssid.charAt(ssid.length - 1) === '"') {
        ssid = ssid.substr(1, ssid.length - 2);
    }

    /**
     * @type {String}
     */
    this.ssid = ssid;

    /**
     * @type {String}
     */
    this.bssid = bssid;

    /**
     * @type {Wifi.NetworkCapabilities}
     */
    this.capabilities = capabilities ? (capabilities.constructor == Wifi.NetworkCapabilities ? capabilities
        : new Wifi.NetworkCapabilities(capabilities)) : new Wifi.NetworkCapabilities();

    /**
     * @type {String}
     */
    this.password = password ? password : password;

    /**
     * Distance from the device location to this hotspot
     * @type {number}
     */
    this.distance = 0;
}

Wifi.prototype = {
    connect: function (successCallback, errorCallback) {
        connectToNetwork(this, successCallback, errorCallback);
        return this;
    },
    addToConfiguration: function (successCallback, errorCallback) {
        addNetwork(this, successCallback, errorCallback);
        return this;
    },
    removeFromConfiguration: function (successCallback, errorCallback) {
        WifiWorker.removeNetwork(this, successCallback, errorCallback);
        return this;
    },
    setPassword: function (password) {
        this.password = password;
        return this;
    }
};

Wifi.AUTH_TYPE = {
    NONE: 'NONE',
    WPA: 'WPA-PSK',
    WPA2: 'WPA2-PSK'
};

Wifi.CHANGE_STATE = {
    ENABLED: 'ENABLED',
    DISABLED: 'DISABLED',
    SCANNING: 'SCANNING',
    AUTHENTICATING: 'AUTHENTICATING',
    COMPLETED: 'COMPLETED',
    DISCONNECTED: 'DISCONNECTED',
    ERROR_AUTHENTICATING: 'ERROR_AUTHENTICATING'
};

/**
 * @param {String=} capabilities
 * @constructor
 */
Wifi.NetworkCapabilities = function (capabilities) {
    this.capabilities = capabilities ? String(capabilities).match(/\[(.*?)\]/g).map(function (v) {
        return v.substr(1, v.length - 2);
    }) : [];
};

Wifi.NetworkCapabilities.prototype = {

    /**
     * Gets the authentication type of this WiFi
     * @returns {string}
     */
    getAuthType: function () {
        var testType, type = null;
        for (testType in Wifi.AUTH_TYPE) {
            if (Wifi.AUTH_TYPE.hasOwnProperty(testType)) {
                this.capabilities.forEach(function (cap) {
                    if (!type && cap.indexOf(Wifi.AUTH_TYPE[testType]) != -1) {
                        type = Wifi.AUTH_TYPE[testType];
                    }
                });
                if (type) {
                    break;
                }
            }
        }

        return type || Wifi.AUTH_TYPE.NONE;
    },

    isP2P: function () {
        return this.capabilities.filter(function (capability) {
                return capability == 'P2P';
            }).length > 0;
    }
};

/**
 * Listens to lots of WiFi change events
 * @param {String|Array.<String>} type
 * @param {Function} callback
 * @param {Boolean=} once
 * @param {Function=} errorCallback
 */
function listenToWifiChange(type, callback, once, errorCallback) {

    listenToWifiChange.listeners.push([type, callback, once]);

    if (!listenToWifiChange.listening) {
        listenToWifiChange.listening = true;
        cordova.exec(function (data) {

            console.log('SUPPLICANT[' + data.type + ']', data);

            listenToWifiChange.listeners.slice(0).forEach(function (listener, index) {
                if (
                    (listener[0] instanceof Array && listener[0].indexOf(data.type) >= 0)
                    || listener[0] == data.type
                ) {

                    if (data.type == Wifi.CHANGE_STATE.COMPLETED) {
                        //Turns the partial wifi data to a Wifi object
                        data.data = createWifiFromResult(data.data);
                    }

                    listener[1](data.type, data.data);

                    if (listener[2]) {
                        listenToWifiChange.listeners.splice(listenToWifiChange.listeners.indexOf(listener), 1);
                    }
                }
            });

        }, function (err) {
            !errorCallback || errorCallback(err);
        }, className, 'listenToWifiChanges', []);
    }
}

listenToWifiChange.listeners = [];

function stopListeningToWifiChange(callback) {
    listenToWifiChange.listeners.forEach(function (listener) {
        if (listener[1] == callback) {
            listenToWifiChange.listeners.splice(listenToWifiChange.listeners.indexOf(listener), 1);
        }
    })
}

module.exports = {

    isEnabled: isEnabled,
    isDisabled: isDisabled,

    getConnectedNetwork: getConnectedNetwork,

    enableWifi: enableWifi,

    startScan: startScan,

    getScanResult: getScanResult,
    getNearbyNetworks: getNearbyNetworks,
    getConfiguredNetworks: getConfiguredNetworks,

    connectToNetwork: connectToNetwork,

    addNetwork: addNetwork,
    removeNetwork: removeNetwork,

    Wifi: Wifi,

    listenToWifiChange: listenToWifiChange,
    stopListeningToWifiChange: stopListeningToWifiChange,

    getConnectedWifiInfo: function(successFunc,errorFunc){
        cordova.exec(successFunc,errorFunc,"WifiWorker","getConnectedWifiInfo",[]);
    }
};