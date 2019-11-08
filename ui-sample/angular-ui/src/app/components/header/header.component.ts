import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { ResourceService } from '../../services/resource/resource.service'
import { UserService } from 'src/app/services/user/user.service';
import { PermissionService } from 'src/app/services/permission/permission.service';
import rolesConfig from '../../services/rolesConfig.json'
import { KeycloakService } from 'keycloak-angular';
import { CacheService } from 'ng2-cache-service';
import { DataService } from 'src/app/services/data/data.service';
import urlConfig from '../../services/urlConfig.json';



declare var jQuery: any;


@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
  userLogin: any;
  resourceService: ResourceService;
  avtarMobileStyle = {
    backgroundColor: 'transparent',
    color: '#AAAAAA',
    fontFamily: 'inherit',
    fontSize: '17px',
    lineHeight: '38px',
    border: '1px solid #e8e8e8',
    borderRadius: '50%',
    height: '38px',
    width: '38px'
  };
  avtarDesktopStyle = {
    backgroundColor: 'transparent',
    color: '#AAAAAA',
    fontFamily: 'inherit',
    fontSize: '17px',
    lineHeight: '38px',
    border: '1px solid #e8e8e8',
    borderRadius: '50%',
    height: '38px',
    width: '38px'
  };
  public userService: UserService;
  public userName: any;
  public permissionService: PermissionService;
  adminConsoleRole: Array<string>;
  public keycloakAngular: KeycloakService;
  public dataService: DataService;
  public keyCloakUserDetails: any;
  private userId: string;
  private userAuthenticated: any;
  constructor(public router: Router, public activatedRoute: ActivatedRoute, resourceService: ResourceService, userService: UserService
    , permissionService: PermissionService, keycloakAngular: KeycloakService, private cacheService: CacheService, private _cacheService: CacheService,
    dataService: DataService) {
    this.resourceService = resourceService;
    this.userService = userService;
    this.permissionService = permissionService;
    this.keycloakAngular = keycloakAngular;
    this.dataService = dataService;
  }

  ngOnInit() {
    this.adminConsoleRole = rolesConfig.ROLES.adminRole;
    this.resourceService.getResource();
    this.userAuthenticated = this.cacheService.get(rolesConfig.cacheServiceConfig.cacheVariables.UserAuthenticated);
    if (this.userAuthenticated) {
      this.userLogin = this.userAuthenticated.status;
      this.userName = this.cacheService.get(rolesConfig.cacheServiceConfig.cacheVariables.UserKeyCloakData).given_name;
    } else {
      if (this.userService.loggedIn) {
        this.userLogin = this.userService.loggedIn;
        this.userName = this.userService.getUserName;
        this.cacheData()
      }
    }
  }
  showSideBar() {
    jQuery('.ui.sidebar').sidebar('setting', 'transition', 'overlay').sidebar('toggle');
  }

  logout() {
    this.keycloakAngular.logout("http://localhost:4200");
    window.localStorage.clear();
    this.cacheService.removeAll();
  }
  logIn() {
    if (!this.userService.loggedIn) {
      let userDetails = this.keycloakAngular.login();
    } else {
      this.router.navigate(['profile', this.userId])
    }
  }

  cacheData() {
    let userDetails = this.keycloakAngular.getKeycloakInstance().tokenParsed;
    this.cacheService.set(rolesConfig.cacheServiceConfig.cacheVariables.UserKeyCloakData, userDetails, { maxAge: rolesConfig.cacheServiceConfig.setTimeInMinutes * rolesConfig.cacheServiceConfig.setTimeInSeconds });
    this.cacheService.set(rolesConfig.cacheServiceConfig.cacheVariables.UserAuthenticated, { status: true }, { maxAge: rolesConfig.cacheServiceConfig.setTimeInMinutes * rolesConfig.cacheServiceConfig.setTimeInSeconds });
    if (this.userLogin) {
      this.readUserDetails(this.keycloakAngular.getKeycloakInstance().profile.email)
    }

  }
  navigateToAdminConsole() {
    const authroles = this.permissionService.getAdminAuthRoles()
    if (authroles) {
      return authroles.url;
    }
  }
  navigateToProfilePage() {
    this.userId = this.cacheService.get(rolesConfig.cacheServiceConfig.cacheVariables.EmployeeDetails).osid;
    this.router.navigate(['/profile', this.userId])
  }

  readUserDetails(data: String) {
    const requestData = {
      data: {
        id: "open-saber.registry.search",
        request: {
          entityType: ["Employee"],
          filters: {
            email: { eq: data }
          }
        }
      },
      url: urlConfig.URLS.SEARCH,
    }
    this.dataService.post(requestData).subscribe(response => {
      this.cacheService.set(rolesConfig.cacheServiceConfig.cacheVariables.EmployeeDetails, response.result.Employee[0], { maxAge: rolesConfig.cacheServiceConfig.setTimeInMinutes * rolesConfig.cacheServiceConfig.setTimeInSeconds });
      this.userId = response.result.Employee[0].osid;
      this.router.navigate(['/profile', this.userId])
    }, (err => {
      console.log(err)
    }))
  }

}
