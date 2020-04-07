let app = require('./app')

var async = require('async');
const _ = require('lodash')
const WFEngineFactory = require('./workflow/EngineFactory');
const baseFunctions = require('./workflow/Functions')
const engineConfig = require('./engineConfig.json')
const NERUtilFunctions = require('./NERFunctions')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const RegistryService = require('./sdk/RegistryService')
const httpUtils = require('./sdk/httpUtils');
const logger = require('./sdk/log4j');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);
const QRCode = require('qrcode');
const Jimp = require('jimp');
const dateFormat = require('dateformat');

var registryService = new RegistryService();
const keycloakHelper = new KeycloakHelper(vars.keycloak);
const eprKeycloakHelper = new KeycloakHelper(vars.keycloak_epr)

const entityType = 'Employee';
const classesMapping = {
    'NERFunction': NERUtilFunctions,
    'Functions': baseFunctions
};

app.theApp.get("/keycloak/users/:userId", (req, res, next) => {
    getTokenDetails(req, keycloakHelper, (err, token) => {
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
app.theApp.post("/profile/qrImage", (req, res, next) => {
    var opts = {
        errorCorrectionLevel: 'M',
        type: 'image/jpeg',
        quality: 0.3,
        margin: 6,
        color: {
            dark: "#000000",
            light: "#ffffff"
        }
    }
    QRCode.toDataURL(JSON.stringify(req.body.request), opts, function (err, url) {
        if (err) throw err
        if (url.indexOf('base64') != -1) {
            var buffer = Buffer.from(url.replace(/^data:image\/png;base64,/, ""), 'base64');
            Jimp.read(buffer, (err, image) => {
                if (err) throw err;
                else {
                    Jimp.loadFont(Jimp.FONT_SANS_16_BLACK).then(function (font) {
                        image.print(font, 95, 230, req.body.request.empCode);
                        image.getBase64(Jimp.MIME_PNG, (err, img) => {
                            res.statusCode = 200;
                            res.setHeader('Content-Type', 'img/png');
                            return res.end(img);
                        });
                    });
                }
            });
        } else {
            // handle as Buffer, etc..
        }

    });
});

/**
 * deletes user in keycloak and update record as inactive to the registry
 * @param {*} req 
 * @param {*} callback 
 */
const offBoardUser = (req, callback) => {
    async.waterfall([
        function (callback) {
            //if auth token is not given , this function is used get access token
            getTokenDetails(req, keycloakHelper, callback);
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
                    callback(null, token, data.result[entityType].kcid)
                } else if (data && data.params.status == 'UNSUCCESSFUL') {
                    callback(new Error(data.params.errmsg))
                } else if (err) {
                    callback(new Error(err.message))
                }
            })
        },
        // To update 'x-owner' role to the user
        function (token, kcid, callback2) {
            updateKeycloakUserRoles(token, req, kcid, (err, data) => {
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
 * creates user in keycloak and add record to the registry
 * first gets the bearer token needed to create user in keycloak and registry
 * @param {*} req 
 * @param {*} callback 
 */
const createUser = (req, seedMode, callback) => {
    async.waterfall([
        function (callback) {
            //if auth token is not given , this function is used get access token
            getTokenDetails(req, keycloakHelper, callback);
        },
        function (token, callback) {
            req.headers['authorization'] = token;
            req.body.request[entityType]['emailVerified'] = seedMode
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
        },
        function (keycloakRes, callback2) {
            //if keycloak registration is successfull then add record to the registry
            logger.info("Got this response from KC registration " + JSON.stringify(keycloakRes))
            let isActive = req.body.request[entityType].isActive
            if ((isActive && keycloakRes.statusCode == 200) || !isActive) {
                if (isActive) {
                    req.body.request[entityType]['kcid'] = keycloakRes.body.id
                }

                addRecordToRegistry(req, callback2)
            } else {
                callback(keycloakRes, null)
            }
        },
    ], function (err, result) {
        logger.info('Main Callback --> ' + result);
        if (err) {
            logger.info("Some errors encountered" + JSON.stringify(err))
            callback(err, null)
        } else {
            callback(null, result);
        }
    });
}

const pushToEPR = (userReq) => {
    let reqBody = userReq.body.request[entityType]
    if (reqBody.clientInfo && reqBody.clientInfo.name === 'Ekstep') {
        //adding externalRole and externalId to EPR User re
        reqBody.externalRole = reqBody.role
        reqBody.externalId = reqBody.code
        reqBody.isActive = true
        reqBody.orgName = "ILIMI";
        reqBody.isOnboarded = false
        reqBody = _.omit(reqBody, ['clientInfo', 'role', 'code']) // removes the properties present in the array from req 
        delete userReq.headers.authorization;
        userReq.body.request[entityType] = reqBody
        getTokenDetails(userReq, eprKeycloakHelper, (err, token) => {
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

const getTokenDetails = (req, keycloakHelper, callback) => {
    if (!req.headers.authorization) {
        keycloakHelper.getToken(function (err, token) {
            if (token) {
                callback(null, 'Bearer ' + token.access_token.token);
            } else {
                callback(err);
            }
        });
    } else {
        callback(null, req.headers.authorization);
    }
}

const addRecordToRegistry = (req, callback) => {
    var eprReq = Object.assign({}, req);
    registryService.addRecord(req, function (err, res) {
        if (res.statusCode == 200 && res.body.params.status == 'SUCCESSFUL') {
            pushToEPR(eprReq);
            logger.info("record successfully added to registry", res.body);
            callback(null, res.body)
        } else {
            logger.debug("record could not be added to registry" + res.statusCode)
            callback(res)
        }
    })
}

/**
 * get roles from keycloak 
 * add x-owner role to keycloak user
 * delete other roles from keycloak user except default roles
 * @param {objecr} req 
 * @param {*} res 
 * @param {*} callback 
 */
const updateKeycloakUserRoles = (token, req, kcid, callback) => {
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
            keycloakHelper.addUserRoleById(kcid, keycloakUserReq, (err, res) => {
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
            keycloakHelper.deleteUserRoleById(kcid, keycloakUserReq, (err, res) => {
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
const wfEngine = WFEngineFactory.getEngine(engineConfig, classesMapping['NERFunction'])
wfEngine.init()

app.startServer(wfEngine);
