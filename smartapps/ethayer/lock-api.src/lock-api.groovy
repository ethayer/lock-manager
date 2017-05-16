definition (
  name: 'Lock API',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage users on the web.',
  category: 'Safety & Security',

  parent: 'ethayer:Lock Manager',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
include 'asynchttp_v1'

mappings {
  path("/locks") {
    action: [
      GET: "listLocks"
    ]
  }
  path("/token") {
    action: [
      POST: "getAccountToken"
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

preferences {
  page name: 'setupPage'
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  initialize()
}

def initialize() {
  // reset listeners

  unsubscribe()
  unschedule()
}

def setupPage() {
  dynamicPage(name: 'setupPage', title: 'Setup API', uninstall: true, install: true) {
    section('API service') {
      paragraph 'Nothing to do.'
    }
  }
}

def lockObject(lockApp) {
  def usage = lockApp.totalUsage()
  def pinLength = lockApp.pinLength()
  def slotCount = lockApp.lockCodeSlots()
  def slotData = lockApp.codeData();
  return [
    name: lockApp.lock.displayName,
    value: lockApp.lock.id,
    usage_count: usage,
    pin_length: pinLength,
    slot_count: slotCount,
    slotData: slotData
  ]
}

def listLocks() {
  def locks = []
  def lockApps = parent.getLockApps()

  lockApps.each { app ->
    locks << lockObject(app)
  }
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
    uri: 'https://www.lockmanager.io/',
    path: '/events/code-used',
    body: [
      token: state.accountToken,
      lock: lockApp.lock.id,
      state: action,
      slot: slot
    ]
  ]
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

def getAccountToken() {
  state.accountToken = request.JSON?.token
  parent.setAccountToken(request.JSON?.token)
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

def debugger(message) {
  def doDebugger = parent.debuggerOn()
  if (doDebugger) {
    log.debug(message)
  }
}
