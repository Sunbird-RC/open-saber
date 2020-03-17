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
const dateFormat = require('dateformat');
const _ = require('lodash');

var cacheManager = new CacheManager();
var registryService = new RegistryService();
const keycloakHelper = new KeycloakHelper(vars.keycloak);
const entityType = 'Employee';

const classesMapping = {
    'EPRFunction': EPRUtilFunctions,
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
    createUser(req, false, function (err, data) {
        if (err) {
            res.statusCode = err.statusCode;
            return res.send(err.body)
        } else {
            return res.send(data);
        }
    });
});

// self registeration api
app.theApp.post("/register/users/self", (req, res, next) => {
    createUser(req, function (err, data) {
        if (err) {
            res.statusCode = err.statusCode;
            return res.send(err.body)
        } else {
            return res.send(data);
        }
    });
});

//used to onboard all users
app.theApp.post("/seed/users", (req, res, next) => {
    createUser(req, true, function (err, data) {
        if (err) {
            res.statusCode = err.statusCode;
            return res.send(err.body)
        } else {
            return res.send(data);
        }
    });
});

app.theApp.post("/offboard/user", (req, res, next) => {
    offBoardUser(req, function (err, data) {
        if (err) {
            return res.send({
                status: 'UNSUCCESSFUL',
                errMsg: err.message
            })
        } else {
            return res.send(data);
        }
    });
});
/**
 * deletes user in keycloak and update record as inactive to the registry
 * @param {*} req 
 * @param {*} callback 
 */
const offBoardUser = (req, callback) => {
    async.series([
        function (callback) {
            //if auth token is not given , this function is used get access token
            getTokenDetails(req, callback);
        },
        // To get the entity record from registry with osid
        function (token, callback) {
            req.headers['authorization'] = token;
            var readRequest = {
                body: JSON.parse(JSON.stringify(req.body)),
                headers: req.headers
            }
            readRequest.body["id"] = "open-saber.registry.read";
            registryService.readRecord(readRequest, (err, data) => {
                if (data && data.params.status == 'SUCCESSFUL') {
                    callback(null, token, data.result)
                } else if (data && data.params.status == 'UNSUCCESSFUL') {
                    callback(new Error(data.params.errmsg))
                } else if (err) {
                    callback(new Error(err.message))
                }
            })
        },
        // To update 'x-owner' role to the user
        function (token, callback2) {
            updateKeycloakUserRoles(token, req, (err, data) => {
                if (data) {
                    callback2(null, data)
                } else if (err) {
                    callback(new Error("Unable to update Keycloak User Roles"))
                }
            })
        },
        //To inactivate user in registry
        function (res, callback3) {
            offBoardUserFromRegistry(req, res, callback3)
        }
    ], function (err, result) {
        logger.info('Main Callback --> ' + result);
        if (err) {
            callback(err)
        } else {
            callback(null, result);
        }
    });
}
/**
 * creates user in keycloak and add record to the registry
 * first gets the bearer token needed to create user in keycloak and registry
 * @param {*} req 
 * @param {*} callback 
 */
const createUser = (req, seedMode, callback) => {
    var tasks = [function (callback) {
        //if auth token is not given , this function is used get access token
        getTokenDetails(req, callback);
    }]

    tasks.push(function (token, callback) {
        req.headers['Authorization'] = token;
        req.body.request[entityType]['emailVerified'] = seedMode

        //Add to keycloak if user is active
        if (req.body.request[entityType].isActive) {
            var keycloakUserReq = {
                body: {
                    request: req.body.request[entityType]
                },
                headers: req.headers
            }
            logger.info("Adding user to KeyCloak. Email verified = " + seedMode)
            keycloakHelper.registerUserToKeycloak(keycloakUserReq, callback)
        } else {
            logger.info("User is not active. Not registering to keycloak")
            callback(null, undefined)
        }
    })

    //Add to registry
    tasks.push(function (res, callback2) {
        logger.info("Got this response from KC registration " + res)
        addRecordToRegistry(req, res, callback2)
    })


    async.waterfall(tasks, function (err, result) {
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
    // If active, KC registration must be successful.
    let isActive = req.body.request[entityType].isActive
    if ((isActive && (res.statusCode == 201 || res.statusCode == 200)) ||
        !isActive) {
        let kcid = ""
        if (isActive) {
            kcid = res.body.id
        }
        
        req.body.request[entityType]['kcid'] = kcid
        req.body.request[entityType]['isOnboarded'] = req.body.request[entityType].isActive;
        registryService.addRecord(req, function (err, res) {
            if (res.statusCode == 200 && res.params.status == 'SUCCESSFUL') {
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
/**
 * update record to the registry
 * @param {objecr} req 
 * @param {*} res 
 * @param {*} callback 
 */
const offBoardUserFromRegistry = (req, res, callback) => {
    if (res.statusCode == 204) {
        req.body.request[entityType]['isActive'] = false;
        req.body.request[entityType]['endDate'] = dateFormat(new Date(), "yyyy-mm-dd")
        registryService.updateRecord(req, function (err, res) {
            if (res) {
                logger.info("record successfully update to registry")
                callback(null, res)

            } else {
                callback(err)
            }
        })
    }
}

/**
 * get roles from keycloak 
 * add x-owner role to keycloak user
 * delete other roles from keycloak user except default roles
 * @param {objecr} req 
 * @param {*} res 
 * @param {*} callback 
 */
const updateKeycloakUserRoles = (token, req, callback) => {
    async.waterfall([
        //To get all roles with ids from keycloak
        function (callback) {
            keycloakHelper.getRolesByRealm(token, (err, roles) => {
                if (roles) {
                    callback(null, token, roles)
                } else {
                    callback(new Error("Unable to get Keycloak Roles fom realm"))
                }
            })
        },
        //To add 'x-owner' role to keycloak user
        function (token, roles, callback2) {
            var addRoleDetails = _.filter(roles, { name: 'x-owner' })
            req.headers['authorization'] = token;
            var keycloakUserReq = {
                headers: req.headers,
                body: addRoleDetails
            }
            keycloakHelper.addUserRoleById(req.body.kcid, keycloakUserReq, (err, res) => {
                if (res && res.statusCode == 204) {
                    callback2(null, roles)
                } else {
                    callback(new Error("Unable to add 'x-owner' to keycloak Id"), null)
                }
            })
        },
        //To remove all roles from keycloak user except default roles
        function (roles, callback) {
            var deleteRoleDetails = _.pullAllBy(roles, [{ 'name': 'x-owner' }, { 'name': 'uma_authorization' }, { 'name': 'offline_access' }], 'name');
            var keycloakUserReq = {
                headers: req.headers,
                body: deleteRoleDetails
            }
            keycloakHelper.deleteUserRoleById(req.params.keyCloakId, keycloakUserReq, (err, res) => {
                if (res && res.statusCode == 204) {
                    callback(null, res)
                } else {
                    callback(new Error("Unable to delete roles from keycloak Id"))
                }
            })
        }
    ], function (err, result) {
        logger.info('updateKeycloakUserRoles callback --> ' + result);
        if (err) {
            callback(err)
        } else {
            callback(null, result);
        }
    });
}
// Init the workflow engine with your own custom functions.
const wfEngine = WFEngineFactory.getEngine(engineConfig, classesMapping['EPRFunction'])
wfEngine.init()

app.startServer(wfEngine);
