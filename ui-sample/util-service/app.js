const express = require("express");
const http = require("http");
const app = express();
var bodyParser = require("body-parser");
var cors = require("cors")
const morgan = require("morgan");
const server = http.createServer(app);
const _ = require('lodash')
const jwt = require('jsonwebtoken');
const fs = require('fs');
var async = require('async');
const templateConfig = require('./templates/template.config.json');
const RegistryService = require('./sdk/registryService')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const logger = require('./sdk/log4j');
const httpUtils = require('./sdk/httpUtils.js');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);
const appConfig = require('./sdk/appConfig');
const port = process.env.PORT || 9081;
let wfEngine = undefined
var CacheManager = require('./sdk/CacheManager.js');
var cacheManager = new CacheManager();
const registryService = new RegistryService();
const keycloakHelper = new KeycloakHelper(vars.keycloak);
const nerKeycloakHelper = new KeycloakHelper(vars.keycloak_ner);
const entityType = 'Employee';

app.use(cors())
app.use(morgan('dev'));
app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json());

const workFlowFunctionPre = (req) => {
    wfEngine.preInvoke(req);
}

const workFlowFunctionPost = (req) => {
    wfEngine.postInvoke(req);
}

app.use((req, res, next) => {
    logger.info('pre api request interceptor');
    workFlowFunctionPre(req);
    next();
    logger.info("post api request interceptor");
    workFlowFunctionPost(req);
});

app.post("/register/users", (req, res, next) => {
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
            getTokenDetails(req, callback);
        },
        function (token, callback) {
            req.headers['authorization'] = token;
            keycloakHelper.registerUserToKeycloak(req, callback)
        },
        function (req, res, callback2) {
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

//ToDo this must move to workflow functions 
const addRecordToRegistry = (req, res, callback) => {
    if (res.statusCode == 201) {
        let reqParam = req.body.request;
        reqParam['isOnboarded'] = false;
        let reqBody = {
            "id": appConfig.APP_ID.CREATE,
            "ver": "1.0",
            "ets": "11234",
            "params": {
                "did": "",
                "key": "",
                "msgid": ""
            },
            "request": {
                [entityType]: reqParam
            }
        }
        req.body = reqBody;
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

app.post("/registry/add", (req, res, next) => {
    registryService.addRecord(req, function (err, data) {
        return res.send(data.body);
    })
});

app.post("/registry/search", (req, res, next) => {
    if (!_.isEmpty(req.headers.authorization)) {
        req.body.request.viewTemplateId = getViewtemplate(req.headers.authorization);
    }
    registryService.searchRecord(req, function (err, data) {
        return res.send(data);
    })
});

app.post("/registry/read", (req, res, next) => {
    registryService.readRecord(req, function (err, data) {
        return res.send(data);
    })
});

const getViewtemplate = (authToken) => {
    var roles = [];
    let token = authToken.replace('Bearer ', '');
    var decoded = jwt.decode(token);
    if (decoded != null && decoded.realm_access) {
        roles = decoded.realm_access.roles;
    }
    var searchTemplate = getTemplateName(roles, 'searchTemplates');
    return searchTemplate;
}

app.post("/registry/update", (req, res, next) => {
    registryService.updateRecord(req, function (err, data) {
        if (data) {
            updateRecordOfDifferentClient(req, data);
            return res.send(data);
        } else {
            return res.send(err);
        }
    })
});

/**
 * used to update record in the which exits client registry
 * @param {*} req 
 */
const updateRecordOfDifferentClient = (req, res) => {
    if (res.params.status === appConfig.STATUS.SUCCESSFULL) {
        logger.debug("updating record in client registry started")
        let updateReq = _.cloneDeep(req);
        async.waterfall([
            function (callback) {
                req.body.id = appConfig.APP_ID.READ;
                registryService.readRecord(req, function (err, data) {
                    if (data && data.params.status === appConfig.STATUS.SUCCESSFULL && data.result[entityType].orgName === 'ILIMI') {
                        callback(null, data);
                    } else {
                        callback(err)
                    }
                });
            },
            function (readRes, callback) {
                nerKeycloakHelper.getToken((err, token) => {
                    if (token)
                        callback(null, readRes, token.access_token.token);
                    else callback(err)
                });
            },
            function (readRes, token, callback) {
                const headers = {
                    'content-type': 'application/json',
                    authorization: "Bearer " + token
                }
                const searchReq = {
                    body: {
                        id: appConfig.APP_ID.SEARCH,
                        request: {
                            entityType: [entityType],
                            filters: { email: { eq: readRes.result[entityType].email } }
                        }
                    },
                    headers: headers,
                    url: vars.nerRegistryUrl + appConfig.UTILS_URL_CONFIG.SEARCH
                }
                httpUtils.post(searchReq, (err, data) => {
                    callback(null, data.body, headers);
                });
            },
            function (searchRes, headers, callback) {
                if (searchRes && searchRes.params.status === appConfig.STATUS.SUCCESSFULL && searchRes.result[entityType].length > 0) {
                    updateReq.body.request[entityType].osid = searchRes.result[entityType][0].osid;
                    let option = {
                        body: updateReq.body,
                        headers: headers,
                        url: vars.nerRegistryUrl + appConfig.UTILS_URL_CONFIG.UPDATE
                    }
                    httpUtils.post(option, (err, res) => {
                        if (res)
                            callback(null, res.body);
                        else callback(err)
                    });
                } else {
                    callback("error: record is not present in the client regitry")
                }
            }
        ], function (err, result) {
            if (result) logger.debug("Updating record in client registry is ended", result);
            else logger.debug(err)
        });
    }
}

app.post("/notifications", (req, res, next) => {
    if (req.url === "/registry/update") {
        registryService.updateRecord(req, function (err, data) {
            if (data) {
                return res.send(data);
            } else {
                return res.send(err);
            }
        });
    }
});

app.get("/formTemplates", (req, res, next) => {
    getFormTemplates(req.headers, function (err, data) {
        if (err) {
            res.statusCode = 404;
            return res.send(err);
        }
        else {
            const json = {
                result: { formTemplate: data },
                responseCode: 'OK'
            }
            return res.send(json)
        }
    })
});

app.get("/owner/formTemplate", (req, res, next) => {
    readFormTemplate(templateConfig.formTemplates.owner, function (err, data) {
        if (err) {
            res.statusCode = 404;
            return res.send(err);
        } else {
            const json = {
                result: { formTemplate: data },
                responseCode: 'OK'
            }
            return res.send(json)
        }
    });
})

const getFormTemplates = (header, callback) => {
    let roles = [];
    var token = header['authorization'].replace('Bearer ', '');
    var decoded = jwt.decode(token);
    if (header.role) {
        roles = [header.role]
    } else if (decoded.realm_access) {
        roles = decoded.realm_access.roles;
    }
    readFormTemplate(getTemplateName(roles, 'formTemplates'), function (err, data) {
        if (err) callback(err, null);
        else callback(null, data);
    });
}

/**
 * pick the template according to the role, preferences is ordered 
 * @param {*} roles 
 */
//todo get roles from config
const getTemplateName = (roles, templateName) => {
    if (_.includes(roles, templateConfig.roles.admin))
        return templateConfig[templateName][templateConfig.roles.admin];
    if (_.includes(roles, templateConfig.roles.partnerAdmin))
        return templateConfig[templateName][templateConfig.roles.partnerAdmin];
    if (_.includes(roles, templateConfig.roles.finAdmin))
        return templateConfig[templateName][templateConfig.roles.finAdmin];
    if (_.includes(roles, templateConfig.roles.owner))
        return templateConfig[templateName][templateConfig.roles.owner]
}

const readFormTemplate = (value, callback) => {
    fs.readFile(value, (err, data) => {
        if (err) callback(err, null);
        else {
            let jsonData = JSON.parse(data);
            callback(null, jsonData);
        }
    });
}

const setEngine = (engine) => {
    wfEngine = engine
}

module.exports.startServer = (engine) => {
    setEngine(engine)

    server.listen(port, function () {
        logger.info("util service listening on port " + port);
    })
};