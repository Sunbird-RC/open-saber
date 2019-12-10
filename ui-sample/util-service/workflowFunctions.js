const keycloakHelper = require('./keycloakHelper.js');
const _ = require('lodash')
const notificationRules = require('./notifyRulesSet.json')
const notify = require('./notification.js')


class WorkFlowFunctions {

    constructor(request) {
        // Provide access to the request object to all actions.
        this.request = request;

        // Provide a property bag for any data exchange between workflow functions.
        this.placeholders = {};

        this.emailIds = [];
    }

    /**
     * get users by role , where role = 'admin'
     * 
     * @param {*} callback 
     */
    getAdminUsers(callback) {
        this.getUserMailId('admin', (err, data) => {
            if (data) {
                if (data.length > 0) {
                    _.forEach(data, (value) => {
                        this.emailIds.push(value.email);
                    });
                    callback(null, data)
                }
            } else {
                callback(err)
            }
        });
    }

    getPartnerAdminUsers(callback) {
        this.getUserMailId('partner-admin', (err, data) => {
            if (data) {
                if (data.length > 0) {
                    _.forEach(data, (value) => {
                        this.emailIds.push(value.email);
                    });
                    callback(null, data)
                }
            } else {
                callback(err)
            }
        });
    }

    getFinAdminUsers(callback) {
        this.getUserMailId('fin-admin', (err, data) => {
            if (data) {
                callback(null, data)
            } else {
                callback(err)
            }
        });
    }
    getReporterUsers(callback) {
        this.getUserMailId('reporter', (err, data) => {
            if (data) {
                callback(null, data)
            } else {
                callback(err)
            }
        });
    }
    getOwnerUsers(callback) {
        this.getUserMailId('owner', (err, data) => {
            if (data) {
                callback(null, data)
            } else {
                callback(err)
            }
        });
    }

    /**
     * calls notification send api 
     * @param {*} callback 
     */
    sendNotifications(callback) {
        notify(this.emailIds);
    }


    notifyUsersBasedOnAttributes(callback) {
        let params = _.keys(this.request.body.request.Employee);
        _.forEach(notificationRules.update.attributes, (value) => {

        });
    }

    notifyUsers(attribute, callback) {
        switch (attribute) {
            case 'githubId':
                callback(null, 'getFinAdminUsers');
                break;
            case 'macAddress':
                callback(null, 'fin-admin')
                break;
            default:
                callback('no attribute found')
        }
    }

    getUserMailId(role, callback) {
        let tokenDetails;
        this.getTokenDetails((err, token) => {
            if (token) {
                tokenDetails = token;
                keycloakHelper.getUserByRole(role, tokenDetails.access_token.token, function (err, data) {
                    if (data) {
                        callback(null, data)
                    }
                    else {
                        callback(err)
                    }
                });
            } else {
                callback(err);
            }
        });

    }

    getTokenDetails(callback) {
        keycloakHelper.getToken(function (err, token) {
            if (token) {
                callback(null, token);
            } else {
                callback(err)
            }
        });
    }

}

module.exports = WorkFlowFunctions;
