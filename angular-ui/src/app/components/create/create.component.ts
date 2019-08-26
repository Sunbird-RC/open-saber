import { Component, OnInit, ViewChild } from '@angular/core';
import { ResourceService } from '../../services/resource/resource.service';
import { FormService } from '../../services/forms/form.service'
import { from } from 'rxjs';
import { DefaultTemplateComponent } from '../default-template/default-template.component';
import urlConfig from '../../services/urlConfig.json';
import { DataService } from '../../services/data/data.service';
import { Router, ActivatedRoute } from '@angular/router'

@Component({
  selector: 'app-create',
  templateUrl: './create.component.html',
  styleUrls: ['./create.component.scss']
})
export class CreateComponent implements OnInit {

  @ViewChild('formData') formData: DefaultTemplateComponent;

  resourceService: ResourceService;
  formService: FormService;
  public formFieldProperties: any;
  dataService: DataService;
  router: Router;

  constructor(resourceService: ResourceService, formService: FormService, dataService: DataService, route: Router) {
    this.resourceService = resourceService;
    this.formService = formService;
    this.dataService = dataService;
    this.router = route;
  }

  ngOnInit() {
    console.log(this.formService.getPersonForm())
    console.log(this.resourceService)
    this.formFieldProperties = this.formService.getPersonForm().fields;
  }

  createUser() {
    console.log(this.formData.formInputData);
    const requestData = {
      data: {
        "id": "open-saber.registry.create",
        "ver": "1.0",
        "ets": "11234",
        "params": {
          "did": "",
          "key": "",
          "msgid": ""
        },
        "request": {
          "Person": this.formData.formInputData
        }
      },
      url: urlConfig.URLS.ADD
    };
    this.dataService.post(requestData).subscribe(response => {
      this.navigateToProfilePage(response.result.Person.osid);
    }, err => {
      // this.toasterService.error(this.resourceService.messages.fmsg.m0078);
    });
  }
  navigateToProfilePage(id: String) {
      this.router.navigate(['/profile', id])
  }
}
