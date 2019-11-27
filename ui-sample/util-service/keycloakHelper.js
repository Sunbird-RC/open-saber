
const Keycloak = require('keycloak-connect')
const session = require('express-session')
const async = require('async')
let memoryStore = new session.MemoryStore()


const getKeyCloakClient = (config, store) => {
    console.log("config", config)
    const keycloak = new Keycloak({ store: store || memoryStore }, config);
    keycloak.authenticated = authenticated;
    return keycloak
}
const deauthenticated = function (request) {
    delete request.session.userId
    if (request.session) {
        request.session.sessionEvents = request.session.sessionEvents || []
    }
}
const authenticated = function (request) {
    
    console.log("request",request)
    try {
        grant = keycloak.grantManager.obtainDirectly("sysadmin@ekstep.org", 'utils', undefined, 'openid')
        console.log("grant", grant);
        var userId = request.kauth.grant.access_token.content.sub.split(':')
        request.session.userId = userId[userId.length - 1];
        request.session.save();
    } catch (err) {
        console.log('userId conversation error', request.kauth.grant.access_token.content.sub, err);
    }
    async.series({
    
    }, function (err, results) {
        if (err) {
            console.log('err', err)
        }
    })
}
module.exports = {
    getKeyCloakClient,
    memoryStore
}
