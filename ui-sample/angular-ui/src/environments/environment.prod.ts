import { KeycloakConfig } from 'keycloak-angular';

let keycloakConfiguration: KeycloakConfig = {
  url: 'http://localhost:8443/auth',
  realm: 'NIITRegistry',
  clientId: 'portal',
  "credentials": {
    "secret": "" 
  }  
};

export const environment = {
  production: true,
  keycloakConfig: keycloakConfiguration
};
