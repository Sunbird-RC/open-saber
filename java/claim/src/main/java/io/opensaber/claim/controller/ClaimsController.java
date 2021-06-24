package io.opensaber.claim.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.opensaber.claim.entity.Claim;
import io.opensaber.claim.exception.InvalidInputException;
import io.opensaber.claim.model.AttestorActions;
import io.opensaber.claim.service.ClaimService;
import io.opensaber.pojos.dto.ClaimDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

import static io.opensaber.claim.contants.AttributeNames.*;
import static io.opensaber.claim.contants.ErrorMessages.ACCESS_TOKEN_IS_MISSING;

@Controller
public class ClaimsController {

    private final ClaimService claimService;
    private static final Logger logger = LoggerFactory.getLogger(ClaimsController.class);

    @Autowired
    public ClaimsController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @RequestMapping(value = "/api/v1/getClaims", method = RequestMethod.POST)
    public ResponseEntity<List<Claim>> getClaims(@RequestHeader HttpHeaders headers,
                                                 @RequestBody JsonNode requestBody) {
        String entity = requestBody.get(LOWERCASE_ENTITY).asText();
        JsonNode attestorNode = requestBody.get(ATTESTOR_INFO);
        List<Claim> claims = claimService.findClaimsForAttestor(entity, attestorNode);
        return new ResponseEntity<>(claims, HttpStatus.OK);
    }

    private List<String> getAccessToken(@RequestHeader HttpHeaders headers) throws IOException {
        String AUTHORIZATION = "AUTHORIZATION";
        if(!headers.containsKey(AUTHORIZATION)) {
            throw new InvalidInputException(ACCESS_TOKEN_IS_MISSING);
        }
        String accessToken = headers.get(AUTHORIZATION).get(0).split(" ")[1];
        Base64.Decoder decoder = Base64.getDecoder();
        String payloadPart = accessToken.split("\\.")[1];
        String payloadStr = new String(decoder.decode(payloadPart));
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode payload = objectMapper.readTree(payloadStr);
        ArrayNode node = (ArrayNode) payload.get(LOWERCASE_ENTITY);
        ObjectReader reader = objectMapper.readerFor(new TypeReference<List<String>>() {
        });
        return reader.readValue(node);
    }

    @RequestMapping(value = "/api/v1/claims", method = RequestMethod.POST)
    public ResponseEntity<Claim> save(@RequestBody ClaimDTO claimDTO) {
        logger.info("Adding new claimDTO {} ",  claimDTO.toString());
        Claim savedClaim = claimService.save(Claim.fromDTO(claimDTO));
        return new ResponseEntity<>(savedClaim, HttpStatus.OK);
    }

    @RequestMapping(value = "/api/v1/claims/{claimId}", method = RequestMethod.POST)
    public ResponseEntity<Object> attestClaims(@PathVariable String claimId,
                                               @RequestHeader HttpHeaders headers,
                                               @RequestBody JsonNode requestBody) throws IOException {
        List<String> entities = getAccessToken(headers);
        AttestorActions action = AttestorActions.valueOf(requestBody.get(ACTION).asText());
        switch (action) {
            case GRANTED:
                claimService.grantClaim(claimId, entities, headers);
                break;
            case DENIED:
                Optional<String> notes = Optional.empty();
                if(requestBody.has(NOTES)) {
                    notes = Optional.of(requestBody.get(NOTES).asText());
                }
                claimService.updateNotes(claimId, notes, headers, entities);
                break;
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
