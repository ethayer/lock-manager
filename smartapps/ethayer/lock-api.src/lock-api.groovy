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
  path("/locks/:command") {
    action: [
      PUT: "updatelocks"
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
      input(name: 'enableAPI', title: 'Enabled?', type: 'bool', required: true, defaultValue: true, description: 'Enable or disable API access')
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


def codeUsed(lockApp, action, slot) {
  def params = [
    uri: 'https://www.lockmanager.io/',
    path: '/events/code-used',
    body: [
      token: settings.accountToken,
      lock: lockApp.lock.id,
      action: action,
      slot: slot
    ]
  ]
  asynchttp_v1.post(processResponse, params)
}

def processResponse(response, data) {
  log.debug(data)
}

def getAccountToken(params) {
  debugger('API account token set!')
  settings.accountToken = request.JSON?.token
}

def debugger(message) {
  def doDebugger = parent.debuggerOn()
  if (doDebugger) {
    log.debug(message)
  }
}
