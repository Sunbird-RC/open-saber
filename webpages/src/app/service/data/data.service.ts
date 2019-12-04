import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http'
import { mergeMap } from 'rxjs/operators';
import { of as observableOf, throwError as observableThrowError, Observable } from 'rxjs';




@Injectable({
  providedIn: 'root'
})
export class DataService {

  http: HttpClient;
  baseUrl: string;
  portNumber = {
    TrainingCenter: 8080,
    Course:8082,
    Teacher:8083,
    Student: 8084
    
  }

  constructor(http: HttpClient) {
    this.http = http;
    this.baseUrl = "http://localhost:";
  }

  post(requestParam: any, entityType: string): Observable<any> {
    const httpOptions = {
      headers: requestParam.header ? this.getHeader(requestParam.header) : this.getHeader(),
      params: requestParam.param
    }
    return this.http.post(this.baseUrl + this.portNumber[entityType] + requestParam.url, requestParam.data, httpOptions).pipe(
      mergeMap((data: any) => {
        console.log("body", data)
        if (data.responseCode !== 'OK') {
          return observableThrowError(data);
        }
        return observableOf(data);
      }));
  }

  /**
     * for making get api calls
     *
     * @param requestParam interface
     */
  get(requestParam: any): Observable<any> {
    const httpOptions = {
      headers: requestParam.header ? this.getHeader(requestParam.header) : this.getHeader(),
      params: requestParam.param
    };
    return this.http.get(this.baseUrl + requestParam.url, httpOptions).pipe(
      mergeMap((data: any) => {
        if (data.responseCode !== 'OK') {
          return observableThrowError(data);
        }
        return observableOf(data);
      }));
  }

  private getHeader(headers?: any) {
    const default_headers = {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    };
    return { ...default_headers };
  }
}


