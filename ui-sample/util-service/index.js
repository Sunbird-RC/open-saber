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

const entityType = 'Employee';
const classesMapping = {
    'NERFunction': NERUtilFunctions,
    'Functions': baseFunctions
};

app.theApp.get("/keycloak/users/:userId", (req, res, next) => {
    getTokenDetails(req, (err, token) => {
        keycloakHelper.getUserById(req.params.userId, token, (error, data) => {
            if (data) {
                res.statusCode = data.statusCode
                res.send(data.body);
            } else {
                res.send(error)
            }
        })
    })
})

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
            getTokenDetails(req, keycloakHelper, 'userToken', callback);
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
        },
    ], function (err, result) {
        logger.info('Main Callback --> ' + result);
        if (err) {
            logger.info("Some errors encountered" + JSON.stringify(err))
            callback(err)
        } else {
            callback(null, result);
        }
    });
}

const pushToEPR = (userReq) => {
    if (userReq.body.request[entityType].clientInfo && userReq.body.request[entityType].clientInfo.name === 'Ekstep') {
        //adding externalRole and externalId to EPR User re
        userReq.body.request[entityType].externalRole = userReq.body.request[entityType].role
        userReq.body.request[entityType].externalId = userReq.body.request[entityType].code
        userReq.body.request[entityType] = _.omit(userReq.body.request[entityType], ['clientInfo', 'role', 'isActive', 'code']) // removes the properties present in the array from req 
        userReq.body.request[entityType].orgName = "ILIMI";
        delete userReq.headers.authorization;
        getTokenDetails(userReq, eprKeycloakHelper, 'eprUserToken', (err, token) => {
            if (token) {
                const options = {
                    url: vars.eprUtilServiceUrl + "/register/users",
                    headers: {
                        'content-type': 'application/json',
                        'accept': 'application/json',
                        'authorization': token
                    },
                    body: userReq.body
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
        req.body.request[entityType]['isActive'] = true;
        registryService.addRecord(req, function (err, res) {
            if (res.statusCode == 200) {
                logger.info("record successfully added to registry", res.body)
                pushToEPR(eprReq);
                callback(null, res.body);
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
const wfEngine = WFEngineFactory.getEngine(engineConfig, classesMapping['NERFunction'])
wfEngine.init()

app.startServer(wfEngine);
