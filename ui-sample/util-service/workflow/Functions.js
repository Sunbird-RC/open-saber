const _ = require('lodash')
const async = require('async');

const keycloakHelper = require('../sdk/keycloakHelper.js');
const Notification = require('../sdk/notification.js')
const registryService = require('../sdk/registryService.js');
const logger = require('../sdk/log4j.js')
var CacheManager = require('../sdk/cacheManager.js');
var cache_config = {
    store: 'memory',
    ttl: 1800
}
var cacheManager = new CacheManager(cache_config);

class Functions {
    constructor() {
        // Provide a property bag for any data exchange between workflow functions.
        this._placeholders = {};

        this.attributes = ["macAddress", "githubId", "isOnboarded"]
    }

    setRequest(request) {
        this.request = request
    }

    addToPlaceholders(anyKey, anyValue) {
        this._placeholders[anyKey] = anyValue
    }

    getPlaceholders(anyKey) {
        return this._placeholders[anyKey]
    }

    /**
     * Returns users in role 
     * @param {String} roleName 
     * @param {fun(err, data)} callback 
     */
    getUsersByRole(roleName, callback) {
        let tokenDetails;
        this.getTokenDetails((err, token) => {
            if (token) {
                tokenDetails = token;
                keycloakHelper.getUserByRole(roleName, tokenDetails.access_token.token, function (err, data) {
                    if (data) {
                        callback(null, data)
                    }
                    else {
                        callback(err)
                    }
                });
            } else {
                callback(err);
            }
        });
    }


    /**
     * used to get registry user
     * @param {*} callback 
     */
    getUserByid(callback) {
        logger.info("get user by id method invoked ", this.request.body)
        let req = {
            headers: this.request.headers,
            body: this.request.body
        };
        req.body.id = "open-saber.registry.read",
        req.body.request.Employee.osid = this.request.body.request.Employee.osid;
        registryService.readEmployee(req, (err, data) => {
            if (data) {
                this.userData = data.result.Employee;
                callback(null, data.result.Employee)
            } else {
                callback(err);
            }
        });
    }

    /**
     * calls notification send api 
     * @param {*} callback 
     */
    sendNotifications(callback) {
        logger.info("send notifications", this._placeholders);
        const notification = new Notification(null, null, this._placeholders);
        notification.sendNotifications((err, data) => {
            if (data) {
                callback(null, data);
            }
        });
    }

    getTemplateparams(callback) {
        logger.info("get template params");
        let params = {};
        params = this.userData;
        this._placeholders.templateParams = params;
        this._placeholders.templateParams.paramName = this._placeholders.paramName;
        this._placeholders.templateParams.paramValue = this._placeholders.paramValue;
    }

    onBoardNewUserTemplate(callback) {
        logger.info("get onBoardNewUserTemplate template params");
        this._placeholders.templateParams = this.request.body.request;
        this._placeholders.templateId = "newUserOnboard";
    }

    /**
     * to get user token and caching
     * @param {*} callback 
     */
    getTokenDetails(callback) {
        cacheManager.get('usertoken', function (err, tokenData) {
            if (err || !tokenData) {
                keycloakHelper.getToken(function (err, token) {
                    if (token) {
                        cacheManager.set({ key: 'usertoken', value: { authToken: token } }, function (err, res) { });
                        callback(null, token);
                    } else {
                        callback(err);
                    }
                });
            } else {
                callback(null, tokenData.authToken);
            }
        });
    }

    check123() {
        console.log("Check 123 function invoked - this means we are all set")
    }
}

module.exports = Functions;