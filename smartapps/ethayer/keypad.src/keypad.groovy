definition (
  name: 'Keypad',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage keypads. This is a child app.',
  category: 'Safety & Security',

  parent: 'ethayer:Lock Manager2',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page name: 'rootPage'
  page(name: "keypadPage")
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

  if (keypad) {
    subscribe(location,"alarmSystemStatus",alarmStatusHandler)
    subscribe(keypad,"codeEntered",codeEntryHandler)
  }
}

def rootPage() {
  dynamicPage(name: "keypadPage",title: "Keypad Settings (optional)", install: true, uninstall: true) {
    section("Settings") {
      // TODO: put inputs here
      input(name: "keypad", title: "Keypad", type: "capability.lockCodes", multiple: false, required: true)
    }
    def hhPhrases = location.getHelloHome()?.getPhrases()*.label
    hhPhrases?.sort()
    section("Routines", hideable: true, hidden: true) {
      paragraph 'settings here are for this keypad only. Global keypad settings, use parent app.'
      input(name: "armRoutine", title: "Arm/Away routine", type: "enum", options: hhPhrases, required: false)
      input(name: "disarmRoutine", title: "Disarm routine", type: "enum", options: hhPhrases, required: false)
      input(name: "stayRoutine", title: "Arm/Stay routine", type: "enum", options: hhPhrases, required: false)
      input(name: "nightRoutine", title: "Arm/Night routine", type: "enum", options: hhPhrases, required: false)
      input(name: "armDelay", title: "Arm Delay (in seconds)", type: "number", required: false)
      input(name: "notifyIncorrectPin", title: "Notify you when incorrect code is used?", type: "bool", required: false)
    }
  }
}


def alarmStatusHandler(event) {
  log.debug "Keypad manager caught alarm status change: "+event.value
  if (event.value == "off"){
    keypad?.setDisarmed()
  }
  else if (event.value == "away"){
    keypad?.setArmedAway()
  }
  else if (event.value == "stay") {
    keypad?.setArmedStay()
  }
}

private sendSHMEvent(String shmState) {
  keypad.acknowledgeArmRequest(3)
  def event = [
        name:"alarmSystemStatus",
        value: shmState,
        displayed: true,
        description: "System Status is ${shmState}"
      ]
  log.debug "test ${event}"
  sendLocationEvent(event)
}

private execRoutine(armMode) {
  if (armMode == 'away') {
    location.helloHome?.execute(settings.armRoutine)
  } else if (armMode == 'stay') {
    location.helloHome?.execute(settings.stayRoutine)
  } else if (armMode == 'off') {
    location.helloHome?.execute(settings.disarmRoutine)
  }
}

def codeEntryHandler(evt) {
  //do stuff
  log.debug "Caught code entry event! ${evt.value.value}"

  def codeEntered = evt.value as String

  def data = evt.data as String
  def armMode = ''
  def currentarmMode = keypad.currentValue("armMode")
  def changedMode = 0

  if (data == '0') {
    armMode = 'off'
  }
  else if (data == '3') {
    armMode = 'away'
  }
  else if (data == '1') {
    armMode = 'stay'
  }
  else if (data == '2') {
    armMode = 'stay' //Currently no separate night mode for SHM, set to 'stay'
  } else {
    log.error "${app.label}: Unexpected arm mode sent by keypad!: "+data
    return []
  }

  def message = " "

  def userApps = parent.getUserApps()
  def correctUser = false
  userApps.each { userApp ->
    def code
    log.debug userApp.userCode
    if (userApp.isActiveKeypad()) {
      code = userApp.userCode.take(4)
      log.debug "code: ${code}"
      if (code.toInteger() == codeEntered.toInteger()) {
        correctUser = userApp
      }
    } else {
      log.debug "NO ACTIVE!"
    }
  }

  if (correctUser) {
    log.debug "Correct PIN entered. Change SHM state to ${armMode}"
    //log.debug "Delay: ${armDelay}"
    //log.debug "Data: ${data}"
    //log.debug "armMode: ${armMode}"

    def unlockUserName = settings."userName${i}"

    if (data == "0") {
      //log.debug "sendDisarmCommand"
      runIn(0, "sendDisarmCommand")
      message = "${evt.displayName} was disarmed by ${unlockUserName}"
    }
    else if (data == "1") {
      //log.debug "sendStayCommand"
      if(armDelay) {
        keypad.setExitDelay(armDelay)
      }
      runIn(armDelay, "sendStayCommand")
      message = "${evt.displayName} was armed to 'Stay' by ${unlockUserName}"
    }
    else if (data == "2") {
      //log.debug "sendNightCommand"
      if(armDelay) {
        keypad.setExitDelay(armDelay)
      }
      runIn(armDelay, "sendNightCommand")
      message = "${evt.displayName} was armed to 'Night' by ${unlockUserName}"
    }
    else if (data == "3") {
      //log.debug "sendArmCommand"
      if(armDelay) {
        keypad.setExitDelay(armDelay)
      }
      runIn(armDelay, "sendArmCommand")
      message = "${evt.displayName} was armed to 'Away' by ${unlockUserName}"
    }

    log.debug "${message}"
    send(message)
  } else {
    log.debug "Incorrect code!"
    // keypad.sendInvalidKeycodeResponse()
  }
}
def sendArmCommand() {
  log.debug "Sending Arm Command."
  keypad.acknowledgeArmRequest(3)
  sendSHMEvent("away")
  execRoutine("away")
}
def sendDisarmCommand() {
  log.debug "Sending Disarm Command."
  keypad.acknowledgeArmRequest(0)
  sendSHMEvent("off")
  execRoutine("off")
}
def sendStayCommand() {
  log.debug "Sending Stay Command."
  keypad.acknowledgeArmRequest(1)
  sendSHMEvent("stay")
  execRoutine("stay")
}
def sendNightCommand() {
  log.debug "Sending Night Command."
  keypad.acknowledgeArmRequest(2)
  sendSHMEvent("stay")
  execRoutine("stay")
}
