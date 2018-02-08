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
