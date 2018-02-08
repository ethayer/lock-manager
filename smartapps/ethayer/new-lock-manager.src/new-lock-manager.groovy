definition(
  name: 'Lock Manager',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'Manage locks and users',
  category: 'Safety & Security',
  singleInstance: true,
  iconUrl: 'https://images.lockmanager.io/app/v1/images/lm.jpg',
  iconX2Url: 'https://images.lockmanager.io/app/v1/images/lm2x.jpg',
  iconX3Url: 'https://images.lockmanager.io/app/v1/images/lm3x.jpg'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
include 'asynchttp_v1'

preferences {
  page name: 'mainPage', title: 'Installed', install: true, uninstall: true, submitOnChange: true
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    setAsManager()
    section('Create') {
      app(name: 'locks', appName: 'New Lock Manager', namespace: 'ethayer', title: 'New Lock', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/new-lock.png')
      // app(name: 'lockUsers', appName: 'Lock User', namespace: 'ethayer', title: 'New User', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/user-plus.png')
      // app(name: 'keypads', appName: 'Keypad', namespace: 'ethayer', title: 'New Keypad', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/keypad-plus.png')
      // app(name: 'lockAPI', appName: 'Lock API', namespace: 'ethayer', title: 'Api Access', multiple: false, image: 'https://images.lockmanager.io/app/v1/images/keypad-plus.png')
    }
  }
}

def setAsManager {
  state.pageType = 'manager'
}

def mainApp {
  if (state.pageType = null || 'manager') {
    return true
  } else {
    return false
  }

}

def lockPage() {
  dynamicPage(name: "lockPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      setAsLockPage
    }
  }
}

def setAsLockPage
  state.appType = 'lock'
end
