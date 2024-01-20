import './style.css'
import '@riotjs/hot-reload'
import 'bulma/css/bulma.min.css'
import { mount } from 'riot'
import registerGlobalComponents from './register-global-components'
import logo from './assets/logo.png'


// register
registerGlobalComponents()

// mount all the global components found in this page
mount('[data-riot-component]');
