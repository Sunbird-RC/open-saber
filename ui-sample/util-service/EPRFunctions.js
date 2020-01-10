let Functions = require("./workflow/Functions");
const _ = require('lodash')
const async = require('async');

class EPRFunctions extends Functions {
    EPRFunctions() {
        setRequest(undefined)
    }

    getAdminUsers(callback) {
        this.getUsersByRole('admin', (err, data) => {
            this.addToPlaceholders('emailIds', _.map(data, 'email'))
            callback();
        });
    }

    getPartnerAdminUsers(callback) {
        this.getUsersByRole('partner-admin', (err, data) => {
            this.addToPlaceholders('emailIds', _.map(data, 'email'))
            callback();
        })
    }

    getFinAdminUsers(callback) {
        this.getUsersByRole('fin-admin', (err, data) => {
            this.addToPlaceholders('emailIds', _.map(data, 'email'))
            callback();
        });
    }

    getReporterUsers(callback) {
        this.getUsersByRole('reporter', (err, data) => {
            this.addToPlaceholders('emailIds', _.map(data, 'email'))
            callback();
        });
    }

    getOwnerUsers(callback) {
        this.getUsersByRole('owner', (err, data) => {
            this.addToPlaceholders('emailIds', _.map(data, 'email'))
            callback();
        });
    }

    getRegistryUsersMailId(callback) {
        this.getUserByid((err, data) => {
            if (data) {
                this.addToPlaceholders('emailIds', [data.email]);
                callback();
            }
        })
    }

    notifyUsersBasedOnAttributes(callback) {
        let params = _.keys(this.request.body.request.Employee);
        async.forEachSeries(this.attributes, (value, callback) => {
            if (_.includes(params, value)) {
                let params = {
                    paramName: value,
                    paramValue: this.request.body.request.Employee[value]
                }
                this.addToPlaceholders('templateParams', params)
                this.getActions(value, (err, data) => {
                    if (data) {
                        callback();
                    }
                });
            } else {
                callback();
            }
        });
    }

    getActions(attribute, callback) {
        let actions = []
        switch (attribute) {
            case 'githubId':
                actions = ['getFinAdminUsers', 'sendNotifications'];
                this.addToPlaceholders('templateId', "updateParamTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'macAddress':
                actions = ['getReporterUsers', 'sendNotifications'];
                this.addToPlaceholders('templateId', "updateParamTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'isOnboarded':
                actions = ['getRegistryUsersMailId', 'sendNotifications'];
                this.addToPlaceholders('templateId', "onboardSuccesstemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
        }
    }

    invoke(actions, callback2) {
        if (actions.length > 0) {
            let count = 0;
            async.forEachSeries(actions, (value, callback) => {
                count++;
                this[value]((err, data) => {
                    callback()
                });
                if (count == actions.length) {
                    callback2(null, actions);
                }
            });
        }
    }

    searchCheck(callback) {
        console.log("search is hit")
        callback(null)
    }

}

module.exports = EPRFunctions