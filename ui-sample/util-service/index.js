let app = require('./app')

var async = require('async');
const _ = require('lodash')
const WFEngineFactory = require('./workflow/EngineFactory');
const baseFunctions = require('./workflow/Functions')
const engineConfig = require('./engineConfig.json')
const NERUtilFunctions = require('./NERFunctions')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const RegistryService = require('./sdk/RegistryService')
const CacheManager = require('./sdk/CacheManager.js');
const httpUtils = require('./sdk/httpUtils');
const logger = require('./sdk/log4j');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);

var cacheManager = new CacheManager();
var registryService = new RegistryService();
const keycloakHelper = new KeycloakHelper(vars.keycloak);
const eprKeycloakHelper = new KeycloakHelper(vars.keycloak_epr)

const classesMapping = {
    'NERFunction': NERUtilFunctions,
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

const createUser = (req, callback) => {
    async.waterfall([
        function (callback) {
            //if auth token is not given , this function is used get access token
            getTokenDetails(req, keycloakHelper, 'userToken', callback);
        },
        function (token, callback) {
            req.headers['authorization'] = token;
            keycloakHelper.registerUserToKeycloak(req, callback)
        },
        function (req, res, callback2) {
            addRecordToRegistry(req, res, callback2)
        },
    ], function (err, result) {
        logger.info('Main Callback --> ' + result);
        if (err) {
            callback(err, null)
        } else {
            callback(null, result);
        }
    });
}

const pushToEPR = (req) => {
    if (req.body.request.clientInfo.name === 'Ekstep') {
        req.body.request = _.omit(req.body.request, ['clientInfo', 'role'])
        req.body.request.orgName = "ILIMI";
        delete req.headers.authorization;
        getTokenDetails(req, eprKeycloakHelper, 'eprUserToken', (err, token) => {
            if (token) {
                const options = {
                    url: vars.eprUtilServiceUrl + "/register/users",
                    headers: {
                        'content-type': 'application/json',
                        'accept': 'application/json',
                        'authorization': token
                    },
                    body: req.body
                }
                httpUtils.post(options, function (err, res) {
                    if (res.statusCode == 200) {
                        logger.info("Employee successfully added to Partner Registry", res.body)
                    } else {
                        logger.debug("Employee could not be added to Partner registry" + res.body + res.statusCode)
                    }
                });
            } else if (err) {
                logger.debug("Employee could not be added to Partner registry", err)
            }
        });
    }
}

const getTokenDetails = (req, keycloakHelper, cacheTokenName, callback) => {
    if (!req.headers.authorization) {
        cacheManager.get(cacheTokenName, function (err, tokenData) {
            if (err || !tokenData) {
                keycloakHelper.getToken(function (err, token) {
                    if (token) {
                        cacheManager.set({ key: cacheTokenName, value: { authToken: token } }, function (err, res) { });
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

const addRecordToRegistry = (req, res, callback) => {
    var eprReq = Object.assign({}, req);
    if (res.statusCode == 201) {
        let reqParam = req.body.request;
        reqParam['isActive'] = true;
        let reqBody = {
            "id": "open-saber.registry.create",
            "ver": "1.0",
            "ets": "11234",
            "params": {
                "did": "",
                "key": "",
                "msgid": ""
            },
            "request": {
                "Employee": reqParam
            }
        }
        req.body = reqBody;
        registryService.addRecord(req, function (err, res) {
            if (res.statusCode == 200) {
                logger.info("Employee successfully added to registry", res.body)
                callback(null, res.body);
                pushToEPR(eprReq);
            } else {
                logger.debug("Employee could not be added to registry" + res.statusCode)
                callback(res.statusCode, res.errorMessage)
            }
        })
    } else {
        callback(res, null)
    }
}

// Init the workflow engine with your own custom functions.
const wfEngine = WFEngineFactory.getEngine(engineConfig, classesMapping['NERFunction'])
wfEngine.init()

app.startServer(wfEngine);
