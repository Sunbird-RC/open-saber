
var keyCloakAuthUtils = require('keycloak-auth-utils');
let request = require('request')
const realmName = process.env.keycloak_realmName || "PartnerRegistry"
const keyCloakHost = process.env.keycloak_url || "http://localhost:8080/auth/admin/realms/" + realmName;

let keyCloak_config = {
    "realm": "PartnerRegistry",
    "auth-server-url": "http://localhost:8080/auth",
    "resource": "utils",
    "credentials": {
        "secret": "9ebc2fc1-ced9-4774-a661-7e2c59991cfe"
    },
    "bearerOnly": true,
    "clientId": "utils"
};

function ApiInterceptor() {
    this.config = keyCloak_config;
    this.keyCloakConfig = new keyCloakAuthUtils.Config(this.config);
    this.grantManager = new keyCloakAuthUtils.GrantManager(this.keyCloakConfig);

}

ApiInterceptor.prototype.getToken = async function (callback) {
    var self = this;
    try {
        let grant = await self.grantManager.obtainDirectly("sysadmin@ekstep.org", 'password1', undefined, 'openid')
        return callback(null, grant);
    } catch (error) {
        console.log("error", error)
    }
}

ApiInterceptor.prototype.getUserByRole = async function (role, token, callback) {

    var headers = {
        'content-type': 'application/json',
        'authorization': 'Bearer ' + token
    }
    try {
        const options = {
            method: 'GET',
            url: keyCloakHost + '/roles/' + role + '/users',
            json: true,
            headers: headers
        }
        request(options, function (err, res, body) {
            if (res.body && res.statusCode == 200) {
                callback(null, res.body)
            } else {

            }
        });
    } catch (err) {

    }
}

module.exports = ApiInterceptor;