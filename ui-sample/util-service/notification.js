const request = require('request')
const notificationHost = "http://localhost:9000/v1/notification/send/sync"

const notify = (email) => {
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
                    ids: [email],
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
        json: true,
        headers: {
            'content-type': 'application/json',
            'accept': 'application/json'
        },
        body: reqBody,
        json: true
    }
    request(option, function (err, res) {
        if (res) {
            console.log("res", res.body)
        }
    })
}

module.exports = notify;
