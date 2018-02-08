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
  if (mainApp) {
    page name: 'mainPage', title: 'Installed', install: true, uninstall: true, submitOnChange: true
    // page name: 'infoRefreshPage'
    // page name: 'notificationPage'
    // page name: 'helloHomePage'
    // page name: 'lockInfoPage'
    // page name: 'keypadPage'
    // page name: 'askAlexaPage'
  } else {

  }

}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'locks', appName: 'Lock', namespace: 'ethayer', title: 'New Lock', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/new-lock.png')
      // app(name: 'lockUsers', appName: 'Lock User', namespace: 'ethayer', title: 'New User', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/user-plus.png')
      // app(name: 'keypads', appName: 'Keypad', namespace: 'ethayer', title: 'New Keypad', multiple: true, image: 'https://images.lockmanager.io/app/v1/images/keypad-plus.png')
      // app(name: 'lockAPI', appName: 'Lock API', namespace: 'ethayer', title: 'Api Access', multiple: false, image: 'https://images.lockmanager.io/app/v1/images/keypad-plus.png')
    }
    section('Locks') {
      def lockApps = getLockApps()
      lockApps = lockApps.sort{ it.lock.id }
      if (lockApps) {
        def i = 0
        lockApps.each { lockApp ->
          i++
          href(name: "toLockInfoPage${i}", page: 'lockInfoPage', params: [id: lockApp.lock.id], required: false, title: lockApp.label, image: 'https://images.lockmanager.io/app/v1/images/lock.png' )
        }
      }
    }
    section('Global Settings') {
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')

      def actions = location.helloHome?.getPhrases()*.label
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://images.lockmanager.io/app/v1/images/home.png')
      }

      def keypadApps = getKeypadApps()
      if (keypadApps) {
        href(name: 'toKeypadPage', page: 'keypadPage', title: 'Keypad Routines (optional)', image: 'https://images.lockmanager.io/app/v1/images/keypad.png')
      }
    }

    section('Advanced', hideable: true, hidden: true) {
      input(name: 'overwriteMode', title: 'Overwrite?', type: 'bool', required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      input(name: 'enableDebug', title: 'Enable IDE debug messages?', type: 'bool', required: true, defaultValue: false, description: 'Show activity from Lock Manger in logs for debugging.')
      label(title: 'Label this SmartApp', required: false, defaultValue: 'Lock Manager')
      paragraph 'Lock Manager © 2017 v1.4'
    }
  }
}

def mainApp {
  return true
}

def lockPage() {
  dynamicPage(name: "mainPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      setAsLockPage
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://images.lockmanager.io/app/v1/images/home.png')
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label title: 'Label', defaultValue: "Lock: ${lock.label}", required: false, description: 'recommended to start with Lock:'
      input(name: 'lock', title: 'Which Lock?', type: 'capability.lock', multiple: false, required: true)
      input(name: 'contactSensor', title: 'Which contact sensor?', type: "capability.contactSensor", multiple: false, required: false)
      input(name: 'slotCount', title: 'How many slots?', type: 'number', multiple: false, required: false, description: 'Overwrite number of slots supported.')
      paragraph 'Lock Manager © 2017 v1.4'
    }
  }
}

def setAsLockPage
  state.appType = 'lock'
end
