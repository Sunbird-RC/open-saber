import { Component, OnInit } from '@angular/core';
import { DataService } from 'src/app/services/data/data.service';

@Component({
  selector: 'app-action',
  templateUrl: './action.component.html',
  styleUrls: ['./action.component.scss']
})
export class ActionComponent implements OnInit {

  public dataService: DataService;
  
  constructor(dataService: DataService) { 
    this.dataService = dataService;
  }

  ngOnInit() {
    
  }


}
