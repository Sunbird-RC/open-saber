const workFlowJson = require('./workflow.json');
const WorkFlowFunctions = require('./workflowFunctions.js');
const _ = require('lodash')
var async = require('async');

class WorkFlowFactory {

    constructor() {
    }

    invoke(request) {
        let config = workFlowJson.config[request.url];
        let workflow = new WorkFlowFunctions(request);
        async.forEachSeries(config.actions, function (value, callback) {
            workflow[value]((err, data) => {
                if (data) {
                    callback()
                }
            })
        })
    }
}

const workFlowFactory = new WorkFlowFactory();

module.exports = workFlowFactory;