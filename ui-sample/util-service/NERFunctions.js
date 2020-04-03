let Functions = require("./workflow/Functions");
const _ = require('lodash')
const async = require('async');
const entityType = 'Employee';
const appConfig = require('./sdk/appConfig');
const RegistryService = require('./sdk/RegistryService')
const KeycloakHelper = require('./sdk/KeycloakHelper');
const httpUtils = require('./sdk/httpUtils.js');
const logger = require('./sdk/log4j');
const vars = require('./sdk/vars').getAllVars(process.env.NODE_ENV);
const eprKeycloakHelper = new KeycloakHelper(vars.keycloak_epr);
var registryService = new RegistryService();


class NERFunctions extends Functions {
    NERFunctions() {
        setRequest(undefined)
    }

    /**
     * gets user mail ids from keycloak where user role = admin
     * @param {*} callback 
     */
    getAdminUsers(callback) {
        this.getUsersByRole('admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    /**
    * gets user email ids from keycloak where user role = admin
    * @param {*} callback
    */
    getPartnerAdminUsers(callback) {
        this.getUsersByRole('partner-admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        })
    }

    /**
     * gets user mail ids from keycloak where user role = admin
     * @param {*} callback 
     */
    getFinAdminUsers(callback) {
        this.getUsersByRole('fin-admin', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    /**
     * gets user mail ids from keycloak where user role = admin
     * @param {*} callback 
     */
    getReporterUsers(callback) {
        this.getUsersByRole('reporter', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    /**
     * gets user mail ids from keycloak where user role = admin
     * @param {*} callback 
     */
    getOwnerUsers(callback) {
        this.getUsersByRole('owner', (err, data) => {
            this.addEmailToPlaceHolder(data, callback);
        });
    }

    /**
     * to get the Employee's email id,
     * @param {} callback 
     */
    getRegistryUsersMailId(callback) {
        this.getUserByid((err, data) => {
            if (data) {
                this.addEmailToPlaceHolder([data.result[entityType]], callback);
            }
        })
    }



    /**
     * to get Employee deatils(from registry)
     * @param {*} callback 
     */
    getRegistryUsersInfo(callback) {
        let tempParams = {}
        this.getUserByid((err, data) => {
            if (data && data.result) {
                tempParams = data.result[entityType];
                tempParams['employeeName'] = data.result[entityType].name
                tempParams['niitURL'] = vars.appUrl
                this.addToPlaceholders('templateParams', tempParams)
                callback()
            } else {
                callback(data, null)
            }
        })
    }

    /**
     * gets email from the object and adds to the placeholder
     * @param {object} data 
     * @param {*} callback 
     */
    addEmailToPlaceHolder(data, callback) {
        this.addToPlaceholders('emailIds', _.map(data, 'email'));
        callback();
    }

    /**
     * send notification to the Employee who onboarded to the NIIT registry successfully
     * @param {*} callback 
     */
    sendOnboardSuccessNotificationToEmployee(callback) {
        logger.info("send notification for onboarding succcessfully to the Employee")
        let tempParams = {}
        tempParams.employeeName = this.request.body.request[entityType].name;
        tempParams.niitURL = vars.appUrl
        this.addToPlaceholders('emailIds', [this.request.body.request[entityType].email]);
        this.addToPlaceholders('subject', "Successfully Onboarded to NIIT")
        this.addToPlaceholders('templateId', "nerOnboardSuccessTemplate");
        this.addToPlaceholders('templateParams', tempParams)
        let actions = ['sendNotifications'];
        this.invoke(actions, (err, data) => {
            callback(null, data)
        });
    }

    /**
     * send notification to the admin whenever new employee is onboarded to the NIIT successfully
     * @param {*} callback 
     */
    sendNewEmployeeOnboardSuccessNoteToAdmin(callback) {
        let tempParams = {}
        tempParams.employeeName = this.request.body.request[entityType].name;
        tempParams.niitURL = vars.appUrl
        this.addToPlaceholders('subject', "New Employee Onboarded")
        this.addToPlaceholders('templateId', "nerNewEmployeeTemplate");
        let actions = ['getAdminUsers', 'sendNotifications'];
        this.invoke(actions, (err, data) => {
            callback(null, data)
        });
    }


    /**
     * notify the user who are offboarded form the Ekstep 
     * notify to admins, reporters, 
     * @param {*} callback 
     */
    sendOffboardSuccessNotification(callback) {
        this.addToPlaceholders('subject', "Successfully Offboarded")
        this.addToPlaceholders('templateId', "NEROffboardingSuccessEmployeeTemplate");
        let actions = ['getRegistryUsersInfo',
            'getAdminUsers', 'sendNotifications',
            'getReporterUsers', 'sendNotifications'];
        this.invoke(actions, (err, data) => {
            callback(null, data)
        });
    }

    /**
     * This method is inovoked to send notiifcations whenever Employee record is updated(for eg attributes like macAddress ,
     * githubId and isOnBoarded are updated)
     * @param {*} callback 
     */
    notifyUsersBasedOnAttributes(callback) {
        let attributesUpdated = _.keys(this.request.body.request[entityType]);//get the list of updated attributes from the req
        let count = 0
        async.forEachSeries(this.notifyAttributes, (param, callback2) => {
            count++
            if (_.includes(attributesUpdated, param)) {
                let params = {
                    paramName: param,
                    [param]: this.request.body.request[entityType][param]
                }
                this.addToPlaceholders('templateParams', params)
                this.getActions(param, (err, data) => {
                    if (data) {
                        callback2();
                    }
                });
            } else {
                callback2();
            }
            if (count === this.notifyAttributes.length) {
                callback(null, "success")
            }
        });
    }

    /**
     * Invoked by notifyUsersBasedOnAttributes function to get the actions to be done(to send notification) on the attribute updated
     * each case contains name of the functions( in the array ), to be invoked for sending notification.
     * @param {*} attribute  param that is updated 
     * @param {*} callback 
     */
    getActions(attribute, callback) {
        let actions = []
        switch (attribute) {
            case 'githubId':
                actions = ['getRegistryUsersInfo', 'getFinAdminUsers', 'sendNotifications'];
                this.addToPlaceholders('subject', "GitHub Id updation");
                this.addToPlaceholders('templateId', "nerUpdateParamTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'macAddress':
                actions = ['getRegistryUsersInfo', 'getReporterUsers', 'sendNotifications'];
                this.addToPlaceholders('subject', "MacAdress updation");
                this.addToPlaceholders('templateId', "nerUpdateParamTemplate");
                this.invoke(actions, (err, data) => {
                    callback(null, data)
                });
                break;
            case 'isActive':
                if (this.request.body.request[entityType][attribute]) {
                    actions = ['getRegistryUsersMailId', 'sendNotifications'];
                    this.addToPlaceholders('subject', "Successfully Onboarded to NIIT");
                    this.addToPlaceholders('templateId', "onboardSuccessTemplate");
                    this.invoke(actions, (err, data) => {
                        callback(null, data)
                    });
                } else {
                    callback(null, "employee deboarded")
                }
                break;
        }
    }

    /**
     * Checks wheather User's organisation is Ekstep 
     * @param {*} callback 
     */
    isEkstepUser(callback) {
        let req = _.cloneDeep(this.request);
        req.body.id = appConfig.APP_ID.READ;
        registryService.readRecord(req, function (err, data) {
            if (data && data.params.status === appConfig.STATUS.SUCCESSFULL) {
                if (data.result[entityType].clientInfo.name === 'Ekstep') {
                    logger.info("EkStep is the client. Going to update.")
                    callback(null, data);
                }
                else {
                    logger.info("Nothing to do.")
                    callback(new Error("record does not belongs to the Ekstep org"))
                }
            } else
                callback(err)
        });
    }

    /**
     * geta admin user token from the Partner Registry 
     * @param {*} readResponse 
     * @param {*} callback 
     */
    getEPRToken(readResponse, callback) {
        eprKeycloakHelper.getToken((err, token) => {
            if (token) callback(null, readResponse, token.access_token.token);
            else callback(err)
        });
    }

    /**
     * Get osid of Employee from Partner Registry.
     * @param {*} readResponse 
     * @param {*} token 
     * @param {*} callback 
     */
    getEPRid(readResponse, token, callback) {
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
            url: vars.eprUtilServiceUrl + appConfig.UTILS_URL_CONFIG.SEARCH
        }
        httpUtils.post(searchReq, (err, res) => {
            if (res.body.params.status === appConfig.STATUS.SUCCESSFULL && res.body.result[entityType].length > 0) {
                logger.info("Got id of the user from EPR)")
                callback(null, res.body, headers);
            } else if (res.body.result[entityType].length <= 0) {
                logger.info("Cannot get user from EPR)")
                callback("error: record is not present in the client regitry");
            }
        });
    }

    /**
     * Update attribute in the client registry
     * @param {*} searchRes 
     * @param {*} headers 
     * @param {*} callback 
     */
    notifyEPR(searchRes, headers, callback) {
        let updateReq = _.cloneDeep(this.request);
        delete updateReq.body.request[entityType]["clientInfo"]

        updateReq.body.request[entityType].osid = searchRes.result[entityType][0].osid;

        let option = {
            body: updateReq.body,
            headers: headers,
            url: vars.eprUtilServiceUrl + appConfig.UTILS_URL_CONFIG.NOTIFICATIONS
        }
        httpUtils.post(option, (err, res) => {
            if (res) {
                logger.info("Send successfully to EPR ")
                logger.debug(res)
                callback(null, res.body);
            }
            else {
                callback(err)
            }
        });
    }

    /**
     * Updates record in client registry
     * If Employee record is updated( for example if macAddress, gitHubId is updated ) then this data should be updated in client
     * registry (iff Employee client name is Ekstep)
     * @param {*} req 
     */
    updateRecordOfClientRegistry() {
        if (JSON.parse(this.response).params.status === appConfig.STATUS.SUCCESSFULL) {
            logger.debug("updating record in client registry started", this.request.body)
            async.waterfall([
                this.isEkstepUser.bind(this),
                this.getEPRToken.bind(this),
                this.getEPRid.bind(this),
                this.notifyEPR.bind(this)
            ], (err, result) => {
                if (result)
                    logger.debug("Updating record in client registry is completed", result);
                else
                    logger.debug(err)
            });
        }
    }

    /**
     * 
     * @param {*} actions array of functions to be called
     * @param {*} callback 
     */
    invoke(actions, callback) {
        if (actions.length > 0) {
            let count = 0;
            async.forEachSeries(actions, (value, callback2) => {
                count++;
                this[value]((err, data) => {
                    callback2()
                });
                if (count == actions.length) {
                    callback(null, actions);
                }
            });
        }
    }

    searchCheck(callback) {
        console.log("search is hit")
        callback(null)
    }

}

module.exports = NERFunctions