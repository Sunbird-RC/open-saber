package io.opensaber.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewTransformer {
    
    private static Logger logger = LoggerFactory.getLogger(ViewTransformer.class);

    /**
     * transforms a given JsonNode to representation of view templates
     * view template indicates any new field or mask fields for transformation
     * 
     * @param viewTemplate
     * @param node
     * @return
     */
    public JsonNode transform(ViewTemplate viewTemplate, JsonNode node) {
        logger.debug("transformation on input node " + node);
        System.out.println("transformation on input node " + node);
        JsonNode resultNode = JsonNodeFactory.instance.objectNode();
        if (node.isArray()) {
            System.out.println("transformation on input node is array " + node);

            for (int i = 0; i < node.size(); i++) {
                JsonNode tNode = tranformNode(viewTemplate, node.get(i));
                ArrayNode resultArray = JsonNodeFactory.instance.arrayNode();

                resultArray = resultArray.add(tNode);

            }
        } else if (node.isObject()) {
            resultNode = tranformNode(viewTemplate, node);

        } else {
            logger.error("Not a valid node for transformation, must be a object node or array node");
        }
        return resultNode;
    }
    /**
     * Transforms a single node for given view template
     * 
     * @param viewTemplate
     * @param node
     * @return
     */
    private JsonNode tranformNode(ViewTemplate viewTemplate, JsonNode node){
        System.out.println("print single node "+node);
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        String subjectType = node.fieldNames().next();
        ObjectNode nodeAttrs = (ObjectNode) node.get(subjectType);

        for (Field field : viewTemplate.getFields()) {

            String functionStr = field.getFunction();
            if (functionStr != null) {

                String fdName = field.getFunctioName();
                FunctionDefinition funcDef = viewTemplate.getFunctionDefinition(fdName);            
                                
                List<Object> actualValues = new ArrayList<>();
                for (String oneArg : field.getArgNames()) {
                    // Cut off the $
                    actualValues.add(ValueType.getValue(nodeAttrs.get(oneArg.substring(1))));
                }
                
                IEvaluator<Object> evaluator = EvaluatorFactory.getInstance(funcDef, actualValues);
                if (field.getDisplay()) {
                    Object evaluatedValue = evaluator.evaluate();
                    if(evaluatedValue instanceof String){
                        result.put(field.getTitle(), evaluatedValue.toString());
                    } else {
                        result.set(field.getTitle(), JsonNodeFactory.instance.pojoNode(evaluatedValue));
                    }
                }
            } else if (field.getDisplay()) {
                result.set(field.getTitle(), nodeAttrs.get(field.getName()));
            }

        }
        logger.debug("Node transformation result: " + result);
        return JsonNodeFactory.instance.objectNode().set(subjectType, result);

    }
    

}
