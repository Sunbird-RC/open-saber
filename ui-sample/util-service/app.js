const express = require("express");
const http = require("http");
const app = express();
var bodyParser = require("body-parser");
var cors = require("cors")
const morgan = require("morgan");
const server = http.createServer(app);
const realmName = process.env.keycloak_realmName || "PartnerRegistry"
const keyCloakHost = process.env.keycloak_url || "http://localhost:8080/auth/admin/realms/" + realmName + "/users";
const request = require('request')
const _ = require('lodash')
const jwt = require('jsonwebtoken');
const fs = require('fs');
var async = require('async');
const templates = require('./templates/template.config.json');
let ApiInterceptor = require('./keycloakHelper')
let ruleSet = require('./notifyRuleSet.json')
let notification = require('./notification.js')
const registryService = require('./registryService.js')
const keycloakHelper = require('./keycloakHelper.js')
app.use(cors())
app.use(morgan('dev'));
app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json());

const port = process.env.PORT || 8090;


//need to fix this middleware not 
// app.use("/register/users", (req, res, next) => {
//     res.on("end", function () {
//         console.log(`${req.method} ${req.originalUrl}`)
//         if (req.originalUrl === ruleSet.registerUsers.url) {
//             getAdminUserEmailId();
//         }
//     })
// })


const getAdminUserEmailId = async () => {
    let tokenDetails = await getTokenDetails();
    if (tokenDetails) {
        keycloakHelper.getUserByRole('admin', tokenDetails.access_token.token, function (err, data) {
            notify(data[0].email);
        });
    }
}


const notify = (email) => {
    notification(email);
}


const getTokenDetails = () => {
    return new Promise((resolve, reject) => {
        var apiInterceptor = new ApiInterceptor()
        apiInterceptor.getToken(function (err, token) {
            if (token) {
                resolve(token)
            } else {
                reject(err)
            }
        })
    })
}


app.post("/register/users", (req, res) => {
    createUser(req.body, req.headers, function (err, data) {
        if (err) {
            res.statusCode = err.statusCode;
            return res.send(err.body)
        } else {
            return res.send(data);
        }
    });
});

const createUser = (value, header, callback) => {
    async.waterfall([
        function (callback) {
            keycloakHelper.registerUserToKeycloak(value.request, header, callback)
        },
        function (value, header, res, callback2) {
            if (res.statusCode == 201) {
            }
            console.log("Employee successfully added to registry")
            addEmployeeToRegistry(value, header, res, callback2)
        }
    ], function (err, result) {
        console.log('Main Callback --> ' + result);
        if (err) {
            callback(err, null)
        } else
            callback(null, result)
    });
}


app.post("/registry/add", (req, res, next) => {
    registryService.addEmployee(req.body, function (err, data) {
        return res.send(data);
    })
});

app.post("/registry/search", (req, res, next) => {
    registryService.searchEmployee(req.body, function (err, data) {
        return res.send(data);
    })
});

app.post("/registry/read", (req, res, next) => {
    registryService.readEmployee(req.body, function (err, data) {
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
    registryService.updateEmployee(req.body, function (err, data) {
        return res.send(data);
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


const addEmployeeToRegistry = (value, header, res, callback) => {
    if (res.statusCode == 201) {
        console.log("value", value)
        registryService.addEmployee(value, function (err, res) {
            if (res.statusCode == 200) {
                console.log("Employee successfully added to registry")
                callback(null, res.body)
            } else {
                console.log("Employee could not be added to registry" + res.statusCode)
                callback(res.statusCode, res.errorMessage)
            }
        })
    } else {
        callback(res, null)
    }
}

const addUserToKeycloak = (value, headers, callback) => {
    const options = {
        method: 'POST',
        url: keyCloakHost,
        json: true,
        headers: {
            'content-type': 'application/json',
            'accept': 'application/json',
            'Authorization': headers.authorization
        },
        body: {
            username: value.email,
            enabled: true,
            emailVerified: false,
            firstName: value.name,
            email: value.email,
            requiredActions: [
                "UPDATE_PASSWORD"
            ],
            credentials: [
                {
                    "value": "password",
                    "type": "password"
                }
            ]
        },
        json: true
    }
    request(options, function (err, res, body) {
        callback(null, value, headers, res)
    })
}



startServer = () => {
    server.listen(port, function () {
        console.log("util service listening on port " + port);
    })
};

startServer();