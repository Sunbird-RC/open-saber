import { Injectable } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';


@Injectable({
  providedIn: 'root'
})
export class UserService {

  public logIn: boolean;
  public keycloakAngular: KeycloakService;
  public userInfo: any = {};
  constructor(keycloakAngular: KeycloakService) {
    this.keycloakAngular = keycloakAngular;
  }


  /**
 * returns login status.
 */
  async loggedIn() {
    await this.keycloakAngular.isLoggedIn().then(login => {
      this.logIn = login;
    });
    return this.logIn;
  }

  async getUserInfo() {
    await this.keycloakAngular.loadUserProfile().then(userInfo => {
      this.userInfo = userInfo;
    })
    return this.userInfo;
  }

  get getUserRoles(): string[] {
    return this.keycloakAngular.getUserRoles();
  }

  get getUserName(): string {
    return this.keycloakAngular.getUsername()
  }
}
