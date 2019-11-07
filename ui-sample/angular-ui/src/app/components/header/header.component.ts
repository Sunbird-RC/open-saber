import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { ResourceService } from '../../services/resource/resource.service'
import { UserService } from 'src/app/services/user/user.service';
import { PermissionService } from 'src/app/services/permission/permission.service';
import rolesConfig from '../../services/rolesConfig.json'

declare var jQuery: any;


@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
  userProfile = false
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

  constructor(public router: Router, public activatedRoute: ActivatedRoute, resourceService: ResourceService, userService: UserService
    ,permissionService: PermissionService) {
    this.resourceService = resourceService;
    this.userService = userService;
    this.permissionService = permissionService;
  }

  ngOnInit() {
    this.adminConsoleRole = rolesConfig.ROLES.adminRole;
    this.resourceService.getResource();
    this.userLogin = this.userService.loggedIn;
    this.userName = this.userService.getUserName;
  }
  showSideBar() {
    jQuery('.ui.sidebar').sidebar('setting', 'transition', 'overlay').sidebar('toggle');
  }

  logout()  {
    window.location.replace('/logoff');
  }
  navigateToAdminConsole() {
    const authroles = this.permissionService.getAdminAuthRoles()
    if (authroles) {
      return authroles.url;
    } 
  }
}
