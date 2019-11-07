import { Component, OnInit } from '@angular/core';
import { PermissionService } from './services/permission/permission.service';
import { UserService } from './services/user/user.service';


@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  title = 'open-saber-ui';
  constructor(private permissionService: PermissionService, public userService: UserService) {
  }
  
  ngOnInit() {
    if (this.userService.loggedIn) {
      this.permissionService.initialize();
    }
  }
}


