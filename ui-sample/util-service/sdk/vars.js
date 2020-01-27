const config = {
    "dev": {
        "keycloak": {
            "url": process.env.keycloak_url || "http://localhost:8443", 
            "realmName": process.env.keycloak_realmName,
            "clientId": "utils",
            "clientSecret": process.env.keycloak_clientSecret || "f6ce7466-b04f-4ccf-b986-e9c61e5fb26b"
        },
        "keycloak_epr": {
            "url": process.env.keycloak_epr_url || "http://localhost:8443",
            "realmName": process.env.keycloak_epr_realmName,
            "clientId": "utils",
            "clientSecret": process.env.keycloak_epr_clientSecret || "9ebc2fc1-ced9-4774-a661-7e2c59991cfe"
        },
        "notificationUrl": process.env.notificationUrl || "http://localhost:9012",
        "registryUrl": process.env.registry_url || "http://localhost:9180",
        "eprUtilServiceUrl": process.env.epr_utilservice_url || "http://localhost:9081"
    },
    "prod": {
        "keycloak": {
            "url": process.env.keycloak_url,
            "realmName": process.env.keycloak_realmName,
            "clientId": "utils",
            "clientSecret": process.env.keycloak_clientSecret
        },
        "keycloak_epr": {
            "url": process.env.keycloak_epr_url,
            "realmName": process.env.keycloak_epr_realmName,
            "clientId": "utils",
            "clientSecret": process.env.keycloak_epr_clientSecret
        },
        "notificationUrl": process.env.notificationUrl,
        "registryUrl": process.env.registry_url,
        "eprUtilServiceUrl": process.env.epr_utilservice_url
    }
}

const logger = require('./log4j')

module.exports.getAllVars = function (envName) {
    var environment = envName
    if (envName === undefined) {
        environment = 'dev'
    }
    logger.info("Service running in mode = " + environment)
    return config[environment]
}