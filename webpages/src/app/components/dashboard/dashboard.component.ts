import { Component, OnInit } from '@angular/core';
import dashboard from '../../service/dashbord.json';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {

  dashBoarddata: any;
  constructor() { }

  ngOnInit() {
    this.dashBoarddata = dashboard.data
  }

}
