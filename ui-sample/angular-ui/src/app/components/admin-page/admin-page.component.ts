import { Component, OnInit, EventEmitter, OnDestroy } from '@angular/core';
import { DataService } from '../../services/data/data.service'
import * as _ from 'lodash-es';
import { ResourceService } from '../../services/resource/resource.service'
import { Router, ActivatedRoute } from '@angular/router'
import { ICard } from '../../services/interfaces/Card';
import { takeUntil, map, first, debounceTime, delay } from 'rxjs/operators';
import { combineLatest, Subject } from 'rxjs';
import appConfig from '../../services/app.config.json';
import { UserService } from 'src/app/services/user/user.service';
import { CacheService } from 'ng2-cache-service';

@Component({
  selector: 'app-admin-page',
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss']
})
export class AdminPageComponent implements OnInit, OnDestroy {

  dataService: DataService;
  userService: UserService;
  public showLoader = true;
  resourceService: ResourceService;
  router: Router;
  activatedRoute: ActivatedRoute;
  pageLimit: any
  public dataDrivenFilterEvent = new EventEmitter();
  private listOfEmployees: ICard[] = [];
  public initFilters = false;
  public dataDrivenFilters: any = {};
  public queryParams: any;
  public unsubscribe$ = new Subject<void>();
  public key: string;
  public buttonIcon: string = 'list';
  public buttonText: string = 'list view'
  result: { "headers": string; "row": {}; };
  totalItems: any;
  config = {
    itemsPerPage: 20,
    currentPage: 1,
    totalItems: 1
  };
  constructor(dataService: DataService, resourceService: ResourceService, route: Router, activatedRoute: ActivatedRoute, userService: UserService, public cacheService: CacheService) {
    this.dataService = dataService;
    this.userService = userService
    this.resourceService = resourceService;
    this.router = route;
    this.activatedRoute = activatedRoute;
    this.pageLimit = appConfig.PAGE_LIMIT
  }

  ngOnInit() {
    this.result = {
      "headers": '',
      "row": ''
    }
    this.initFilters = true;
    this.dataDrivenFilterEvent.pipe(first()).
      subscribe((filters: any) => {
        this.dataDrivenFilters = filters;
        this.fetchDataOnParamChange();
        // this.setNoResultMessage();
      });
    this.activatedRoute.queryParams.subscribe(queryParams => {
      this.queryParams = { ...queryParams };
      this.key = this.queryParams['key'];
    });
  }

  getDataForCard(data) {
    const list: Array<ICard> = [];
    _.forEach(data, (item, key) => {
      const card = this.processContent(item);
      list.push(card);
    });
    return <ICard[]>list;
  }


  processContent(data) {
    const content: any = {
      name: data.name,
      subProjectName: data.Team,
      role: data.Role,
      isApproved: data.isActive,
      startDate: data.StartDate,
      identifier: data.identifier
    };
    return content;
  }

  navigateToProfilePage(user: any) {
    this.router.navigate(['/profile', user.data.identifier]);
  }

  changeView() {
    if (this.buttonIcon === 'list') {
      this.buttonIcon = 'block layout';
      this.buttonText = 'grid view'
    } else {
      this.buttonIcon = 'list'
      this.buttonText = 'list view'
    }
  }


  onEnter(key) {
    this.key = key;
    this.queryParams = {};
    this.queryParams['key'] = this.key;
    if (this.key && this.key.length > 0) {
      this.queryParams['key'] = this.key;
    } else {
      delete this.queryParams['key'];
    }
    this.router.navigate(["search", this.activatedRoute.snapshot.params.pageNumber], {
      queryParams: this.queryParams
    });
  }

  public getFilters(filters) {
    const defaultFilters = _.reduce(filters, (collector: any, element) => {
      return collector;
    }, {});
    this.dataDrivenFilterEvent.emit(defaultFilters);
  }

  private fetchDataOnParamChange() {
    combineLatest(this.activatedRoute.params, this.activatedRoute.queryParams)
      .pipe(debounceTime(5), // wait for both params and queryParams event to change
        delay(10),
        map(result => ({ params: { pageNumber: Number(result[0].pageNumber) }, queryParams: result[1] })),
        takeUntil(this.unsubscribe$)
      ).subscribe(({ params, queryParams }) => {
        this.showLoader = true;
        this.queryParams = { ...queryParams };
        this.listOfEmployees = [];

        this.fetchEmployees();
      });
  }

  private fetchEmployees() {
    let token = this.cacheService.get(appConfig.cacheServiceConfig.cacheVariables.UserToken);
    if (_.isEmpty(token)) {
      token = this.userService.getUserToken;
    }
    const option = {
      url: appConfig.URLS.SEARCH,
      header: { Authorization: token },
      data: {
        id: "open-saber.registry.search",
        request: {
          entityType: ["Employee"],
          filters: {
          },
          viewTemplateId: "Employee_SearchResult.json"
        }
      }
    }
    let filters = _.pickBy(this.queryParams, (value: Array<string> | string) => value && value.length);
    filters = _.omit(filters, ['key', 'sort_by', 'sortType', 'appliedFilters']);
    option.data.request.filters = this.getFilterObject(filters);

    this.dataService.post(option)
      .subscribe(data => {
        this.showLoader = false;
        this.listOfEmployees = this.getDataForCard(data.result.Employee);
        this.config = {
          itemsPerPage: this.pageLimit,
          currentPage: 1,
          totalItems: this.listOfEmployees.length
        };
        this.result = {
          "headers": _.keys(this.listOfEmployees[0]),
          "row": this.listOfEmployees
        }
      }, err => {
        this.showLoader = false;
        this.listOfEmployees = [];
        this.config = { itemsPerPage: this.pageLimit, currentPage: 1, totalItems: 0 };
      });
  }
  getFilterObject(filter) {
    let option = {}
    if (filter) {
      _.forEach(filter, (elem, key) => {
        let filterType = {}
        if (_.isArray(elem)) {
          filterType['or'] = elem;
        } else {
          filterType['contains'] = elem;
        }
        option[key] = filterType;
      });
    }
    //search by name
    if (this.queryParams.key) {
      let filterTypes = {}
      filterTypes["startsWith"] = this.queryParams.key;
      option["name"] = filterTypes
    }
    return option;
  }

  pageChanged(event: number) {
    this.config.currentPage = event;
  }
  
  clearQuery() {
    let redirectUrl = this.router.url.split('?')[0];
    redirectUrl = decodeURI(redirectUrl);
    this.router.navigate([redirectUrl]);
  }

  ngOnDestroy() {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }
}
