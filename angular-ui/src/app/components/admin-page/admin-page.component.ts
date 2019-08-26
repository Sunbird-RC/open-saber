import { Component, OnInit, AfterViewInit } from '@angular/core';
import { DataService } from '../../services/data/data.service'
import urlConfig from '../../services/urlConfig.json'
import * as _ from 'lodash-es';
import {ResourceService} from '../../services/resource/resource.service'
import {Router} from '@angular/router'


@Component({
  selector: 'app-admin-page',
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss']
})
export class AdminPageComponent implements OnInit , AfterViewInit{

  dataService: DataService;
  resourceService: ResourceService;
  router: Router;
  users: Array<Object>;
  result: any;
  constructor(dataService: DataService, resourceService: ResourceService, route: Router) {
    this.dataService = dataService;
    this.resourceService = resourceService;
    this.router = route;
    console.log('resource service ', this.resourceService)
  }

  ngOnInit() {
    this.result = {
      "headers":'',
      "row":''
    }
    this.getUsers();
  }

  ngAfterViewInit() {}

  getUsers() {
    const option = {
      url: urlConfig.URLS.SEARCH,
      data: {
          "id": "open-saber.registry.search",
          "ver": "1.0",
          "ets": "11234",
          "params": {
            "did": "",
            "key": "",
            "msgid": ""
          },
          'request': {
            "entityType": ["Person"],
            "filters": {
            },
            "viewTemplateId": "Person_SearchResult.json"
          }
        }
      }
    this.dataService.post(option).subscribe(data => {
      this.result = {
        "headers": _.keys(data.result.Person[0]),
        "row": data.result.Person
      }
        console.log('response', this.result, _.keys(data.result.Person[0]))
    })
  }

  navigateToProfilePage(user: any) {
    console.log('user profile', user)
      this.router.navigate(['/profile', user.osid]);
  }
}
