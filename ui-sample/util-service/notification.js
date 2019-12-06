const httpUtil = require('./httpUtils.js');
const notificationHost = process.env.notificationUrl || "http://localhost:9000/v1/notification/send/sync"
const keycloakHelper = require('./keycloakHelper.js');

const sendNotifications = (mailIds) => {
    const reqBody = {
        id: "notification.message.send",
        ver: "1.0",
        ets: "11234",
        params: {
            "did": "",
            "key": "",
            "msgid": ""
        },
        request: {
            notifications: [
                {
                    mode: "email",
                    deliveryType: "message",
                    config: { "subject": "Welcome to Ekstep" },
                    ids: mailIds,
                    template: {
                        data: "Hello, thanks for completing",
                    }
                }
            ]
        }
    }
    const option = {
        method: 'POST',
        url: notificationHost,
        headers: {
            'content-type': 'application/json',
            'accept': 'application/json'
        },
        body: reqBody,
    }
    httpUtil.post(option, function (err, res) {
        if (res) {
            callback(null, res.body)
        } else {
            callback(err)
        }
    });
}

const getUserMailId = (roles) => {
    let tokenDetails;
    getTokenDetails(function (err, token) {
        let emailIds = []
        if (token) {
            tokenDetails = token;
            _.forEach(roles, function (value) {
                keycloakHelper.getUserByRole(value, tokenDetails.access_token.token, function (err, data) {
                    if (data) {
                        _.forEach(data, function (value) {
                            emailIds.push(value);
                        })
                    }
                });
                sendNotifications(emailIds);
            });
        }
    });
}


const getTokenDetails = (callback) => {
    keycloakHelper.getToken(function (err, token) {
        if (token) {
            callback(null, token);
        } else {
            callback(err)
        }
    });
}

const notify = (roles) => {
    getUserMailId(roles);
}

module.exports = notify;
