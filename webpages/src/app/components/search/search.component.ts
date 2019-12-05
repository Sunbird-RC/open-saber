import { Component, OnInit, OnDestroy } from '@angular/core';
import { DataService } from 'src/app/service/data/data.service';
import { Router, ActivatedRoute } from '@angular/router';
import * as _ from 'lodash-es';
import { Subject } from 'rxjs';
import appConfig from './EntityType.json';


@Component({
  selector: 'app-search',
  templateUrl: './search.component.html',
  styleUrls: ['./search.component.css']
})
export class SearchComponent implements OnInit, OnDestroy {

  dataService: DataService;
  router: Router;
  activatedRoute: ActivatedRoute;
  rowsData: Array<string[]>;
  config: any;
  entityType: string;
  result = { "headers": [], "row": [] };
  previousBtn = true;
  nextBtn = false;
  currentOffset: any;
  intialOffset = 0;
  limit = 50;
  offset = 50;
  public unsubscribe$ = new Subject<void>();
  public key: string;
  showResultNotFound = false;


  constructor(dataService: DataService, route: Router, activatedRoute: ActivatedRoute) {
    this.activatedRoute = activatedRoute;
    this.router = route;
    this.dataService = dataService;
  }

  ngOnInit() {
    this.config = appConfig.entityTypes;
  }

  onEnter(key) {
    this.key = key;
    if (this.key && this.key.length > 0) {
      this.resetResultVar();
      this.getRegistryData(this.entityType, this.intialOffset);
    }
    else if (_.isEmpty(this.key)) {
          //if key is empty fetch all the data for trainining anf courses
      if (this.entityType === this.config.trainingCenter || this.entityType === this.config.course) {
        this.resetResultVar();
        this.resetPaigination();
        this.getRegistryData(this.entityType, this.intialOffset);
      }
      //clear variable result 
      else if (this.entityType === this.config.teacher || this.entityType === this.config.student) {
        this.resetResultVar();
      }
    }
  }

  fetchContentOnParamChange(type: string) {
    this.showResultNotFound = false;
    if (this.entityType != type) {
      this.resetResultVar();
      this.resetPaigination();
    }
    this.entityType = type;
    if (type === this.config.trainingCenter || type === this.config.course) {
      this.getRegistryData(type, this.intialOffset)
    }
  }
  getRegistryData(registry: string, offset) {
    this.currentOffset = offset;
    this.entityType = registry;
    const option = {
      url: "/search",
      data: {
        id: "open-saber.registry.search",
        request: {
          entityType: [this.entityType],
          filters: this.getFilterObject(),
          viewTemplateId: this.entityType + "-default.json",
          offset: offset
        }
      }
    }
    if (_.isEmpty(this.key)) {
      option.data.request['limit'] = this.limit;
    }
    this.dataService.post(option, this.entityType).subscribe(data => {
      if (data.result[this.entityType] && data.result[this.entityType].length > 0) {
        this.resetResultVar();
        this.result = {
          headers: _.keys(data.result[this.entityType][0]),
          row: data.result[this.entityType]
        }
        if (data.result[this.entityType].length < option.data.request['limit']) {
          this.nextBtn = true;
        }
        this.unsubscribe$.next()
      }
      else {
        this.showResultNotFound = true;
        this.nextBtn = true
      }
    });
  }

  getFilterObject() {
    let option = {}
    //search by name
    if (this.key) {
      let filterTypes = {}
      filterTypes["startsWith"] = this.key;
      option[appConfig.searchParam[this.entityType]] = filterTypes
    }
    return option;
  }

  //to get next cuurentOffset + offset data
  next(nextOffset) {
    let total = this.currentOffset + nextOffset;
    this.previousBtn = false;
    this.getRegistryData(this.entityType, total)
  }

  previous(prevOffset = 10) {
    let total = this.currentOffset - prevOffset;
    if (total >= this.intialOffset) {
      this.getRegistryData(this.entityType, total)
    }
    if (total === this.intialOffset) {
      this.previousBtn = true
      this.nextBtn = false
    }
  }

  resetResultVar() {
    this.result = { "headers": [], "row": [] };
    this.showResultNotFound = false;
  }

  resetPaigination() {
    this.previousBtn = true;
    this.nextBtn = false;
  }

  ngOnDestroy() {
    this.unsubscribe$.unsubscribe();
  }



}
