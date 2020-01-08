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
const engineConfig = require('./engineConfig.json')
const registryService = require('./sdk/registryService')
const keycloakHelper = require('./sdk/keycloakHelper');
const WfEngineFactory = require('./workflow/EngineFactory');
const logger = require('./sdk/log4j');
const port = process.env.PORT || 9181;
const httpUtils = require('./sdk/httpUtils.js');
const partnerRegistryHost = process.env.partnerRegistryHost || "http://localhost:9081"


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
            keycloakHelper.registerUserToKeycloak(req, callback)
        },
        function (req, res, callback2) {
            addEmployeeToRegistry(req, res, callback2)
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


const addEmployeeToRegistry = (req, res, callback) => {
    var eprReq = Object.assign({}, req);
    if (res.statusCode == 201) {
        let reqParam = req.body.request;
        reqParam['isActive'] = false;
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
        registryService.addEmployee(req, function (err, res) {
            if (res.statusCode == 200) {
                logger.info("Employee successfully added to registry")
                pushToEPR(eprReq);
                callback(null, res.body)
            } else {
                logger.debug("Employee could not be added to registry" + res.statusCode)
                callback(res.statusCode, res.errorMessage)
            }
        })
    } else {
        callback(res, null)
    }
}

const pushToEPR = (req) => {
    _.omit(req.body.request, ['clientInfo', 'role'])
    req.body.request.orgName = "ILIMI";
    delete req.headers.authorization;
    const options = {
        url: partnerRegistryHost + "/register/users",
        headers: {
            'content-type': 'application/json',
            'accept': 'application/json',
        },
        body: req.body
    }
    httpUtils.post(options, function (err, res) {
        if (res.statusCode == 200) {
            logger.info("Employee successfully added to Partner Registry")
        } else {
            logger.debug("Employee could not be added to Partner registry" + res.body + res.statusCode)
        }
    });
};

app.post("/registry/add", (req, res, next) => {
    registryService.addEmployee(req, function (err, data) {
        return res.send(data.body);
    })
});

app.post("/registry/search", (req, res, next) => {
    if (!_.isEmpty(req.headers.authorization)) {
        req.body.request.viewTemplateId = getViewtemplate(req.headers.authorization);
    }
    registryService.searchEmployee(req, function (err, data) {
        return res.send(data);
    })
});

app.post("/registry/read", (req, res, next) => {
    registryService.readEmployee(req, function (err, data) {
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
    registryService.updateEmployee(req, function (err, data) {
        if (data) {
            return res.send(data);
        } else {
            return res.send(err);
        }
    })
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

startServer = () => {
    server.listen(port, function () {
        logger.info("util service listening on port " + port);
    })
};


// Init the workflow engine.
const wfEngine = WfEngineFactory.getEngine(engineConfig)
wfEngine.init()

startServer();