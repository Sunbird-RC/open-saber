import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { SignupComponent } from './components/signup/signup.component';
import { LandingPageComponent } from './components/landingpage/landingpage.component';
import { AdminPageComponent } from './components/admin-page/admin-page.component';
import { from } from 'rxjs';

const routes: Routes = [
  {
    path: '', // root path '/' for the app
    component: LandingPageComponent,
  },
  {
    path: 'signUp', component: SignupComponent,

  },
  {
    path: 'admin', component: AdminPageComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
