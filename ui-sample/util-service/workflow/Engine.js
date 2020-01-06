var async = require('async');
const logger = require('../sdk/log4j');
const WorkFlowFunctions = require('./Functions.js');

class Engine {

    /**
     * Constructs an Engine and setup pathActions
     *  {
     *      "post/users/path": {
     *          "preActions": [],
     *          "postActions": [],
     *      }
     *  }
     * @param {engineConfig} config 
     */
    constructor(config) {
        this.pathActions = {}
        if ("1.0.0" === config.version) {
            var rules = config.rules
            for (var itr = 0, len = rules.length; itr < len; itr++) {
                var rule = rules[itr]
                var methodPath = rule.method + rule.url
                this.pathActions[methodPath] = {}
                this.pathActions[methodPath].preActions = rule.preActions
                this.pathActions[methodPath].postActions = rule.postActions
            }

            logger.info("Workflow rules configured for the following paths")
            Object.keys(this.pathActions).forEach(function(key) {
                logger.info(key) 
            })
            logger.info("End workflow rules")
        }
    }

    init() {
        logger.info("Engine inited.")
    }

    _getConfig(method, url) {
        var key = method.toLowerCase() + url
        var pathAction = this.pathActions[key]
    
        if (pathAction) {
            logger.debug("Found a rule set")
        } else {
            logger.debug(method + " " + url + " does not have a rule set")
        }

        return pathAction;
    }

    preInvoke(request) {
        logger.debug("Calling preInvoke functions for " + request.method + " " + request.url)
        let config = this._getConfig(request.method, request.url);
        if (config) {
            let workflow = new WorkFlowFunctions(request);
            async.forEachSeries(config.preActions, function (value, callback) {
                workflow[value]((err, data) => {
                    callback()
                });
            });
        }
        logger.debug("End calling preInvoke functions for " + request.method + " " + request.url)
    }

    postInvoke(request) {
        logger.debug("Calling postInvoke functions for " + request.method + " " + request.url)
        let config = this._getConfig(request.method, request.url);
        if (config) {
            let workflow = new WorkFlowFunctions(request);
            async.forEachSeries(config.postActions, function (value, callback) {
                workflow[value]((err, data) => {
                    callback()
                });
            });
        }
        logger.debug("End calling postInvoke functions for " + request.method + " " + request.url)
    }
}

module.exports = Engine;