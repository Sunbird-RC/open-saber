const httpUtil = require('./httpUtils.js');
const notificationHost = process.env.notificationUrl || "http://localhost:9012/v1/notification/send/sync"
const _ = require('lodash')
const logger = require('./log4j.js');

/**
 * Param 1) mode : mode in which notification sent (for example email, phone or device).
 * 2) deliveryType: can be message, otp, whatsapp or call.
 * 3) placeHolder:  Provide a property bag for any data exchange between workflow functions(placeHoder object should contain
 * subject of email, array of emailIds to which notification is to be sent, template ID and TemplateParams).
 */
class Notification {
    constructor(mode, deliveryType, placeholder) {
        this.deliveryType = deliveryType ? deliveryType : 'message';
        this.mode = mode ? mode : 'email';
        this.placeholder = placeholder;
    }

    sendNotifications(callback) {
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
