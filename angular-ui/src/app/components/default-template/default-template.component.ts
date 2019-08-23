import { Component, Input, OnInit, AfterViewInit, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-default-template',
  templateUrl: './default-template.component.html',
  styleUrls: ['./default-template.component.scss']
})
export class DefaultTemplateComponent implements OnInit {
  @Input() formFieldProperties: any;
  public formInputData = {};

  constructor() { }

  ngOnInit() {
  }

}
