import { Component, Input, OnInit, AfterViewInit, Output, EventEmitter } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router'
import { DataService } from '../../services/data/data.service';
import urlConfig from '../../services/urlConfig.json';
import * as _ from 'lodash-es';


@Component({
  selector: 'app-default-template',
  templateUrl: './default-template.component.html',
  styleUrls: ['./default-template.component.scss']
})
export class DefaultTemplateComponent implements OnInit {
  @Input() formFieldProperties: any;
  public formInputData = {};
  router: Router;
  activatedRoute: ActivatedRoute;
  userId: String;
  dataService: DataService;
  constructor(activatedRoute: ActivatedRoute, dataService: DataService) {
    this.activatedRoute = activatedRoute;
    this.dataService = dataService;
  }

  ngOnInit() {
    this.userId = this.activatedRoute.snapshot.params.id;
    if (this.userId) {
      this.getUserDetails();
    }
  }

  getUserDetails() {
    const requestData = {
      data: {
        "id": "open-saber.registry.read",
        'request': {
          "Person": {
            "osid": this.userId
          },
          "includeSignatures": true,
        }
      },
      url: urlConfig.URLS.READ,
    }
    this.dataService.post(requestData).subscribe(response => {
      this.formInputData = response.result.Person;
    },(err =>{
      console.log(err)
    }))
  }

  createUserDetails() {
    const requestData = {
      data: {
        id:"open-saber.registry.create",
        request:{
          Employee:{
            osid:this.userId
          },
          includeSignatures: false
        }
      },
      url: urlConfig.URLS.ADD
    }
    this.dataService.post(requestData).subscribe(res =>{
      this.formInputData = res.result.osid;
    })
  }

}
