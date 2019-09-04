import { Component, OnInit, ViewChild } from '@angular/core';
import { DataService } from '../../services/data/data.service';
import { ResourceService } from '../../services/resource/resource.service';
import { FormService } from '../../services/forms/form.service';
import { DefaultTemplateComponent } from '../default-template/default-template.component';
import * as _ from 'lodash-es';
import urlConfig from '../../services/urlConfig.json';
import {CertReq} from '../../services/interfaces/certificate'



@Component({
  selector: 'app-create-certificate',
  templateUrl: './create-certificate.component.html',
  styleUrls: ['./create-certificate.component.scss']
})
export class CreateCertificateComponent implements OnInit {

  @ViewChild('formData') formData: DefaultTemplateComponent;
  dataService: DataService;
  formService: FormService;
  resourceService: ResourceService;
  public formFieldProperties: any;
  public req: CertReq;
  constructor(dataService: DataService, formService: FormService, resourceService: ResourceService) {
    this.dataService = dataService;
    this.resourceService = resourceService;
    this.formService = formService;
  }

  ngOnInit() {
    this.formService.getFormConfig("certificate").subscribe(res => {
      this.formFieldProperties = res.fields;
    });
  }
  createCertificate() {
    const  certificateData = this.generateData(_.pickBy(this.formData.formInputData));
    const requestData = {
      data: {
        params: {},
        request: {
          certificate: certificateData
        }
      },
      url : urlConfig.URLS.GENERTATE_CERT
    }
      this.dataService.post(requestData).subscribe(res => {
        console.log('certificate generated successfully', res)
      })
   

  }
  generateData(request: any) {
    
    const requestData = _.cloneDeep(request);
    var certificate = {
      data: [],
      issuer: {},
      signatoryList: [],
      htmlTemplate:'',
      courseName: '',
      name: '',
      description: '',
    } ;
    const data = [{
      recipientName: requestData.recipientName,
      recipientEmail: requestData.recipientEmail,
      recipientPhone: requestData.recipientPhone,
    }];
    const issuer = {
      name: requestData.issuerName,
      url: requestData.issuerURL,
    }
    const signatoryList = [{
      name: requestData.signatoryName,
      id: requestData.signatoryId,
      designation: requestData.signatoryDesignation,
      image: requestData.signatoryImage
    }]
    certificate.data = data;
    certificate.issuer = issuer;
    certificate.signatoryList = signatoryList;
    certificate.htmlTemplate =requestData.htmlTemplateUrl;
    certificate.courseName = requestData.courseName;
    certificate.name = requestData.certificateName;
    certificate.description = requestData.certificateDescription;
      return certificate;
  }
}
