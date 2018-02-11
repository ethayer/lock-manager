mappings {
  path("/locks") {
    action: [
      GET: "listLocks"
    ]
  }
  path("/token") {
    action: [
      POST: "gotAccountToken"
    ]
  }
  path("/update-slot") {
    action: [
      POST: "updateSlot"
    ]
  }

  // Big Mirror
  path("/switches") {
    action: [
      GET: "listSwitches"
    ]
  }
  path("/update-switch") {
    action: [
      POST: "updateSwitch"
    ]
  }
}

def apiSetupPage() {
  dynamicPage(name: 'apiSetupPage', title: 'Setup API', uninstall: true, install: true) {
    section('API service') {
      input(name: 'enableAPI', title: 'Enabled?', type: 'bool', required: true, defaultValue: true, description: 'Enable API integration?')
      if (state.accountToken) {
        paragraph 'Token: ' + state.accountToken
      }
    }
    section('Entanglements') {
      paragraph 'Switches:'
      input(name: 'theSwitches', title: 'Which Switches?', type: 'capability.switch', multiple: true, required: true)
    }
  }
}

def lockObject(lockApp) {
  def usage = lockApp.totalUsage()
  def pinLength = lockApp.pinLength()
  def slotCount = lockApp.lockCodeSlots()
  def slotData = lockApp.codeData();
  def lockState = lockApp.lockState();
  return [
    name: lockApp.lock.displayName,
    value: lockApp.lock.id,
    usage_count: usage,
    pin_length: pinLength,
    slot_count: slotCount,
    lock_state: lockState,
    slot_data: slotData
  ]
}

def listLocks() {
  def locks = []
  def lockApps = getLockApps()

  lockApps.each { app ->
    locks << lockObject(app)
  }
  debugger(locks)
  return locks
}

def listSlots() {
  def slots = []
  def lockApps = parent.getLockApps()

  lockApps.each { app ->
    locks << lockObject(app)
  }
  return locks
}

def switchObject(theSwitch) {
  return [
    name: theSwitch.displayName,
    key: theSwitch.id,
    state: theSwitch.currentValue("switch")
  ]
}

def listSwitches() {
  def list = []
  parent.theSwitches.each { theSwitch ->
    list << switchObject(theSwitch)
  }
  return list
}

def codeUsed(lockApp, action, slot) {
  def params = [
    uri: 'https://api.lockmanager.io/',
    path: 'v1/events/code-used',
    body: [
      token: state.accountToken,
      lock: lockObject(lockApp),
      action_event: action,
      slot: slot
    ]
  ]
  debugger('send switcheroo!')
  asynchttp_v1.post(processResponse, params)
}

def processResponse(response, data) {
  log.debug(data)
}

def updateSlot() {
  def slot = request.JSON?.slot
  def control = request.JSON?.control
  def code = request.JSON?.code
  def lock_id = request.JSON?.lock_key
  def lockApp = parent.getLockAppById(lock_id)
  // slot, code, control
  lockApp.apiCodeUpdate(slot, code, control)
}

def gotAccountToken() {
  log.debug('got called! ' + request.JSON?.token + ' ' + parent?.id)
  state.accountToken = request.JSON?.token
  setAccountToken(request.JSON?.token)
  return [data: "OK"]
}

def updateSwitch() {
  def action = request.JSON?.state
  def switchID = request.JSON?.key
  log.debug "got update! ${action} ${switchID}"
  parent.theSwitches.each { theSwitch ->
    if (theSwitch.id == switchID) {
      if (action == 'on') {
        theSwitch.on()
      }
      if (action == 'off') {
        theSwitch.off()
      }
    }
  }
}

def switchOnHandler(evt) {
  def params = [
    uri: 'https://api.lockmanager.io/',
    path: '/events/switch-change',
    body: [
      token: state.accountToken,
      key: evt.deviceId,
      state: 'on'
    ]
  ]
  asynchttp_v1.post(processResponse, params)
}

def switchOffHandler(evt) {
  def params = [
    uri: 'https://api.lockmanager.io/',
    path: '/events/switch-change',
    body: [
      token: state.accountToken,
      key: evt.deviceId,
      state: 'off'
    ]
  ]
  asynchttp_v1.post(processResponse, params)
}
