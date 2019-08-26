import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { SignupComponent } from './components/signup/signup.component';
import { LandingPageComponent } from './components/landingpage/landingpage.component';
import { AdminPageComponent } from './components/admin-page/admin-page.component';
import { ProfileComponent } from './components/profile/profile.component';
import { LoginComponent } from './components/login/login.component';
import { CreateComponent } from './components/create/create.component';

const routes: Routes = [
  {
    path: '', 
    component: LandingPageComponent,
  },
  {
    path: 'signUp', component: SignupComponent,
  },
  {
    path: 'admin', component: AdminPageComponent
  },
  {
    path: 'profile/:id', component: ProfileComponent
  },
  {
    path: 'logIn', component: LoginComponent
  },
  {
    path:'create', component: CreateComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
