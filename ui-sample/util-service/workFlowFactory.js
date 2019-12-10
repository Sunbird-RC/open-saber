const workFlowJson = require('./workflow.json');
const Workflow = require('./workflowFunctions.js');
const _ = require('lodash')
var async = require('async');

class WorkFlowFactory {

    constructor() {
    }

    invoke(request) {
        let config = workFlowJson.config[request.url];
        let workflow = new Workflow(request);
        async.forEachSeries(config.actions, (value) => {
            console.log("val", value)
            workflow[value]((err, data) => {
                console.log("data", data)
            })
        })
    }
}

const workFlowFactory = new WorkFlowFactory();

module.exports = workFlowFactory;