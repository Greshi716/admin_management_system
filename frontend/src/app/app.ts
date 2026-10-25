import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';

@Component({
  imports: [RouterOutlet, ToastModule],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {}
