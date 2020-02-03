let app = require('./app')

var async = require('async');
const WFEngineFactory = require('./workflow/EngineFactory');
const baseFunctions = require('./workflow/Functions')
const engineConfig = require('./engineConfig.json')
const EPRUtilFunctions = require('./EPRFunctions')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const RegistryService = require('./sdk/RegistryService')
const CacheManager = require('./sdk/CacheManager.js');
const logger = require('./sdk/log4j');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);

var cacheManager = new CacheManager();
var registryService = new RegistryService();
const keycloakHelper = new KeycloakHelper(vars.keycloak);
const entityType = 'Employee';

const classesMapping = {
    'EPRFunction': EPRUtilFunctions,
    'Functions': baseFunctions
};

// Add any new APIs here.
app.theApp.post("/register/users", (req, res, next) => {
    createUser(req, function (err, data) {
        if (err) {
            res.statusCode = err.statusCode;
            return res.send(err.body)
        } else {
            return res.send(data);
        }
    });
});

/**
 * creates user in keycloak and add record to the registry
 * first gets the bearer token needed to create user in keycloak and registry
 * @param {*} req 
 * @param {*} callback 
 */
const createUser = (req, callback) => {
    async.waterfall([
        function (callback) {
            //if auth token is not given , this function is used get access token
            getTokenDetails(req, callback);
        },
        function (token, callback) {
            req.headers['authorization'] = token;
            var keycloakUserReq = {
                body: {
                    request: req.body.request[entityType]
                },
                headers: req.headers
            }
            keycloakHelper.registerUserToKeycloak(keycloakUserReq, callback)
        },
        function (res, callback2) {
            addRecordToRegistry(req, res, callback2)
        }
    ], function (err, result) {
        logger.info('Main Callback --> ' + result);
        if (err) {
            callback(err, null)
        } else {
            callback(null, result);
        }
    });
}

/**
 * returns user token and caches if token is not cached
 * @param {*} req 
 * @param {*} callback 
 */
const getTokenDetails = (req, callback) => {
    if (!req.headers.authorization) {
        cacheManager.get('usertoken', function (err, tokenData) {
            if (err || !tokenData) {
                keycloakHelper.getToken(function (err, token) {
                    if (token) {
                        cacheManager.set({ key: 'usertoken', value: { authToken: token } }, function (err, res) { });
                        callback(null, 'Bearer ' + token.access_token.token);
                    } else {
                        callback(err);
                    }
                });
            } else {
                callback(null, 'Bearer ' + tokenData.authToken.access_token.token);
            }
        });
    } else {
        callback(null, req.headers.authorization);
    }
}

/**
 * adds record to the registry
 * @param {objecr} req 
 * @param {*} res 
 * @param {*} callback 
 */
const addRecordToRegistry = (req, res, callback) => {
    if (res.statusCode == 201) {
        //intially isOnBoarded flag is set false
        req.body.request[entityType]['isOnboarded'] = false;
        registryService.addRecord(req, function (err, res) {
            if (res.statusCode == 200) {
                logger.info("record successfully added to registry")
                callback(null, res.body)
            } else {
                logger.debug("record could not be added to registry" + res.statusCode)
                callback(res.statusCode, res.errorMessage)
            }
        })
    } else {
        callback(res, null)
    }
}

// Init the workflow engine with your own custom functions.
const wfEngine = WFEngineFactory.getEngine(engineConfig, classesMapping['EPRFunction'])
wfEngine.init()

app.startServer(wfEngine);
