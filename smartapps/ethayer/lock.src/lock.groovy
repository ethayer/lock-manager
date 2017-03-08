definition (
  name: 'Lock',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage keypads. This is a child app.',
  category: 'Safety & Security',

  parent: 'ethayer:Lock Manager',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page(name: 'mainPage')
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
  subscribe(lock, 'codeReport', updateCode)
  setupLockData()
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      // TODO: put inputs here
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def setupLockData() {
  setupCodeValues()
}

def setupCodeValues() {
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    if (state.codes["slot${slot}"] == null) {
      log.debug 'slot is null!'
      def data = [:]
      data['code'] = null
      data['state'] = 'unknown'

      state.codes["slot${slot}"] = data
      state.initializeComplete = false
    }
  }
  startCodeDiscovery()
}

def startCodeDiscovery() {
  makeRequest()
}

def makeRequest() {
  def requestSlot = false
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    def state = state.codes["slot${slot}"]['state']
    if (state == 'unknown') {
      requestSlot = slot
    }
  }
  if (requestSlot) {
    log.debug 'making request'
    lock.requestCode(requestSlot)
  } else {
    log.debug 'no request to make'
  }
}

def updateCode(evt) {
  def codeData = new JsonSlurper().parseText(evt.data)
  def slot = evt.integerValue.toInteger()
  log.debug "code: ${codeData['code']} slot: ${slot}"
  state.codes["slot${slot}"]['code'] = codeData['code']
  state.codes["slot${slot}"]['state'] = 'known'
  log.debug state
  runIn(10, makeRequest)
}

def doorOpenCheck() {
  def currentState = contact.contactState
  if (currentState?.value == "open") {
    def msg = "${contact.displayName} is open.  Scheduled lock failed."
    log.info msg
    if (sendPushMessage) {
      sendPush msg
    }
    if (phone) {
      sendSms phone, msg
    }
  } else {
    lockMessage()
    lock.lock()
  }
}

def lockMessage() {
  def msg = "Locking ${lock.displayName} due to scheduled lock."
  log.info msg
}
