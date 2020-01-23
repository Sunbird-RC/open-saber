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
const registryService = new RegistryService();
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
    // workFlowFunctionPre(req);
    next();
    logger.info("post api request interceptor");
    // workFlowFunctionPost(req);
});

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
            updateRecordOfClientRegistry(req, data);
            return res.send(data);
        } else {
            return res.send(err);
        }
    })
});

/**
 * 
 * @param {*} req 
 */
const updateRecordOfClientRegistry = (req, res) => {
    if (res.params.status === appConfig.STATUS.SUCCESSFULL) {
        logger.debug("updating record in client registry started")
        let updateReq = _.cloneDeep(req);
        async.waterfall([
            function (callback) {
                req.body.id = appConfig.APP_ID.READ;
                registryService.readRecord(req, function (err, data) {
                    if (data && data.params.status === appConfig.STATUS.SUCCESSFULL) {
                        if (data.result[entityType].orgName === 'ILIMI')
                            callback(null, data);
                        else
                            callback("record does not belongs to the ILIMI org")
                    } else
                        callback(err)
                });
            },
            function (readResponse, callback) {
                nerKeycloakHelper.getToken((err, token) => {
                    if (token) callback(null, readResponse, token.access_token.token);
                    else callback(err)
                });
            },
            function (readResponse, token, callback) {
                const headers = {
                    'content-type': 'application/json',
                    authorization: "Bearer " + token
                }
                const searchReq = {
                    body: {
                        id: appConfig.APP_ID.SEARCH,
                        request: {
                            entityType: [entityType],
                            filters: { email: { eq: readResponse.result[entityType].email } }
                        }
                    },
                    headers: headers,
                    url: vars.nerUtilServiceUrl + appConfig.UTILS_URL_CONFIG.SEARCH
                }
                httpUtils.post(searchReq, (err, res) => {
                    if (res.body.params.status === appConfig.STATUS.SUCCESSFULL && res.body.result[entityType].length > 0) {
                        callback(null, res.body, headers);
                    } else if (res.body.result[entityType].length <= 0) {
                        callback("error: record is not present in the client regitry");
                    }
                });
            },
            function (searchRes, headers, callback) {
                updateReq.body.request[entityType].osid = searchRes.result[entityType][0].osid;
                let option = {
                    body: updateReq.body,
                    headers: headers,
                    url: vars.nerUtilServiceUrl + appConfig.UTILS_URL_CONFIG.NOTIFICATIONS
                }
                httpUtils.post(option, (err, res) => {
                    if (res)
                        callback(null, res.body);
                    else
                        callback(err)
                });
            }
        ], function (err, result) {
            if (result)
                logger.debug("Updating record in client registry is completed", result);
            else
                logger.debug(err)
        });
    }
}

app.post("/notifications", (req, res, next) => {
    registryService.updateRecord(req, function (err, data) {
        if (data) {
            return res.send(data);
        } else {
            return res.send(err);
        }
    });
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

// Expose the app object for adopters to add new endpoints.
module.exports.theApp = app
