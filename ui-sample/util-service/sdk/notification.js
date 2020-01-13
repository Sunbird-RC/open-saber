const httpUtil = require('./httpUtils.js');
const notificationHost = process.env.notificationUrl || "http://localhost:9012" + "/v1/notification/send/sync";
const _ = require('lodash')
const logger = require('./log4j.js');

class Notification {
    constructor(mode, deliveryType, placeholder) {
        this.deliveryType = deliveryType ? deliveryType : 'message';
        this.mode = mode ? mode : 'email';
        this.placeholder = placeholder;
    }

    sendNotifications(callback) {
        logger.info("email ids to send notifications", this.placeholder);
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
                        mode: this.mode,
                        deliveryType: this.deliveryType,
                        config: { subject: this.placeholder.subject },
                        ids: this.placeholder.emailIds,
                        template: {
                            id: this.placeholder.templateId,
                            params: this.placeholder.templateParams
                        },
                    }
                ]
            }
        }
        logger.info("request body of notification request", JSON.stringify(reqBody));
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
                logger.info("notification has been sucessfully sent", res.body)
                callback(null, res.body)
            } else {
                logger.error("sending notification is unsuccessfull", err)
                callback(err)
            }
        });
    }
}


module.exports = Notification;
