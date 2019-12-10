const httpUtil = require('./httpUtils.js');
const notificationHost = process.env.notificationUrl || "http://localhost:9000/v1/notification/send/sync"
const keycloakHelper = require('./keycloakHelper.js');
const _ = require('lodash')

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
            console.log("notification has been sucessfully sent", res.body)
            // callback(null, res.body)
        } else {
            // callback(err)
        }
    });
}

const getUserMailId = async (roles) => {
    let tokenDetails;
    let emailIds = [];
    getTokenDetails(function (err, token) {
        if (token) {
            tokenDetails = token;
            for (let i = 0; i < roles.length; i++) {
                getUser(roles[i], tokenDetails, function (err, data) {
                    if (data.length > 0) {
                        emailIds.push(...data)
                        if (i == roles.length - 1) {
                            let unique = _.uniq(emailIds);
                            sendNotifications(unique);
                        }
                    }
                });
            }

        }
    });
}

function getUser(value, tokenDetails, callback) {
    let emailIds = [];
    keycloakHelper.getUserByRole(value, tokenDetails.access_token.token, function (err, data) {
        if (data) {
            _.forEach(data, function (value) {
                emailIds.push(value.email);
            });
            callback(null, emailIds)
        }
        else {
            callback(err)
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
