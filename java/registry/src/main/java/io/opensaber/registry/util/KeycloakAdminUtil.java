package io.opensaber.registry.util;

import io.opensaber.registry.exception.DuplicateRecordException;
import io.opensaber.registry.exception.EntityCreationException;
import org.apache.catalina.User;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import javax.swing.text.html.Option;
import javax.ws.rs.core.Response;


@Component
public class KeycloakAdminUtil {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakAdminUtil.class);


    private String realm;
    private String adminClientSecret;
    private String adminClientId;
    private String authURL;
    private final Keycloak keycloak;

    @Autowired
    public KeycloakAdminUtil(
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak-admin.client-secret}") String adminClientSecret,
            @Value("${keycloak-admin.client-id}") String adminClientId,
            @Value("${keycloak.auth-server-url}") String authURL) {
        this.realm = realm;
        this.adminClientSecret = adminClientSecret;
        this.adminClientId = adminClientId;
        this.authURL = authURL;
        this.keycloak = buildKeycloak();
    }

    private Keycloak buildKeycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(authURL)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(adminClientId)
                .clientSecret(adminClientSecret)
                .build();
    }

    public String createUser(String userName, String entityName) throws DuplicateRecordException, EntityCreationException {
        logger.info("Creating user with mobile_number : " + userName);
        UserRepresentation newUser = new UserRepresentation();
        newUser.setEnabled(true);
        newUser.setUsername(userName);
        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setValue("password");
        credentialRepresentation.setType("password");
        newUser.setCredentials(Collections.singletonList(credentialRepresentation));
        newUser.singleAttribute("mobile_number", userName);
        newUser.singleAttribute("entity", entityName);
        UsersResource usersResource = keycloak.realm(realm).users();
        Response response = usersResource.create(newUser);
        if (response.getStatus() == 201) {
            logger.info("Response |  Status: {} | Status Info: {}", response.getStatus(), response.getStatusInfo());
            logger.info("User ID path" + response.getLocation().getPath());
            String userID = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            logger.info("User ID : " + userID);
            return userID;
        } else if (response.getStatus() == 409) {
            logger.info("UserID: {} exists", userName);
            Optional<UserRepresentation> userRepresentationOptional = getUserByUsername(userName);
            if (userRepresentationOptional.isPresent()) {
                return userRepresentationOptional.get().getId();
            } else {
                logger.error("Failed fetching user by username: {}", userName);
                throw new EntityCreationException("Creating user failed");
            }
        } else {
            throw new EntityCreationException("Username already invited / registered");
        }
    }

    private Optional<UserRepresentation> getUserByUsername(String username) {
        List<UserRepresentation> users = keycloak.realm(realm).users().search(username);
        if (users.size() > 0) {
            return Optional.of(users.get(0));
        }
        return Optional.empty();
    }

    private void addUserToGroup(String groupName, UserRepresentation user) {
        keycloak.realm(realm).groups().groups().stream()
                .filter(g -> g.getName().equals(groupName)).findFirst()
                .ifPresent(g -> keycloak.realm(realm).users().get(user.getId()).joinGroup(g.getId()));
    }
}
