import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';
import { ICard } from '../../services/interfaces/Card';

@Component({
  selector: 'app-card',
  templateUrl: './card.component.html',
  styleUrls: ['./card.component.scss']
})
export class CardComponent implements OnInit {

  @Input() data: ICard;
  @Output() clickEvent = new EventEmitter<any>();
  constructor() { }

  ngOnInit() {
  }
  public onAction(data, event) {
    console.log(data)
    console.log(event)
    this.clickEvent.emit({ 'action': event, 'data': data });
  }
}
