import { Component, OnInit, Input, EventEmitter, Output, ViewChild } from '@angular/core';
import { ICard } from '../../services/interfaces/Card';
import { ResourceService } from '../../services/resource/resource.service';
import appConfig from '../../services/app.config.json';
import { PermissionService } from 'src/app/services/permission/permission.service';
import { SuiModalService, TemplateModalConfig, ModalTemplate } from 'ng2-semantic-ui';
import { Router } from '@angular/router';
import { DataService } from 'src/app/services/data/data.service';


@Component({
  selector: 'app-card',
  templateUrl: './card.component.html',
  styleUrls: ['./card.component.scss']
})
export class CardComponent implements OnInit {

  @ViewChild('modalTemplate')
  public modalTemplate: ModalTemplate<{ data: string }, string, string>;
  @Input() data: ICard;
  @Output() clickEvent = new EventEmitter<any>();
  resourceService: ResourceService;
  color =this.getRandColor();
  public permissionService: PermissionService;
  public approveEmployee: Array<string>;
  public enableViewProfile = true;
  router: Router;
  public dataService: DataService;

  constructor(resourceService: ResourceService, permissionService: PermissionService, public modalService: SuiModalService, route: Router
    , dataService: DataService) {
    this.resourceService = resourceService;
    this.permissionService = permissionService;
    this.router = route;
    this.dataService = dataService;
  }

  ngOnInit() {
    this.approveEmployee = appConfig.rolesMapping.approveEmployee;
    if(this.permissionService.checkRolesPermissions(this.approveEmployee)) {
      this.enableViewProfile = false;
    }
  }

  getRandColor() {
    let colors = ["#DD4132", "#727289", "#642F7A", "#A34B25", "#872C6F", "#A34B25", "#8FB339", "#157A7F", "#51504E", "#334A66", "#F7786B","#CE3175","#5B5EA6","#B565A7","#66B7B0"]
    let randNum = Math.floor(Math.random() * 15);
    return colors[randNum];
  }
  public onAction(data, event) {
    this.clickEvent.emit({ 'action': event, 'data': data });
  }
  approveConfirmModal(userId) {
    const config = new TemplateModalConfig<{ data: string }, string, string>(this.modalTemplate);
    config.isClosable = true;
    config.size = 'mini';
    config.context = {
      data: 'Do you want to view the profile before approving?'
    };
    this.modalService
      .open(config)
      .onApprove(result => {
        this.router.navigate(['/profile', userId])
      })
      .onDeny(result => {
        this.approve(userId)
      });
  }
  approve(userId) {
    const requestData = {
      data: {
        "id": "open-saber.registry.update",
        "request": {
          "Employee": {
            osid: userId,
            isActive: true
          }
        }
      },
      url: appConfig.URLS.UPDATE
    };
    this.dataService.post(requestData).subscribe(response => {
      this.router.navigate(['/search/1'])
    }, err => {
    });
  }
}
