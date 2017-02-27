definition (
  name: 'Keypad',
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
  page name: 'rootPage'
  page(name: 'keypadPage')
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
  atomicState.tries = 0
  if (keypad) {
    subscribe(location, 'alarmSystemStatus', alarmStatusHandler)
    subscribe(keypad, 'codeEntered', codeEntryHandler)
  }
}

def rootPage() {
  dynamicPage(name: 'keypadPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    section("Settings") {
      // TODO: put inputs here
      input(name: 'keypad', title: 'Keypad', type: 'capability.lockCodes', multiple: false, required: true)
    }
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section('Routines') {
      paragraph 'settings here are for this keypad only. Global keypad settings, use parent app.'
      input(name: 'runDefaultAlarm', title: 'Act as SHM device?', type: 'bool', defaultValue: true, description: 'Toggle this off if actions should not effect SHM' )
      input(name: 'armRoutine', title: 'Arm/Away routine', type: 'enum', options: actions, required: false)
      input(name: 'disarmRoutine', title: 'Disarm routine', type: 'enum', options: actions, required: false)
      input(name: 'stayRoutine', title: 'Arm/Stay routine', type: 'enum', options: actions, required: false)
      input(name: 'nightRoutine', title: 'Arm/Night routine', type: 'enum', options: actions, required: false)
      input(name: 'armDelay', title: 'Arm Delay (in seconds)', type: 'number', required: false)
      input(name: 'notifyIncorrectPin', title: 'Notify you when incorrect code is used?', type: 'bool', required: false)
      input(name: 'attemptTollerance', title: 'How many times can incorrect code be used before notification?', type: 'number', defaultValue: 3, required: true)
    }
  }
}

def alarmStatusHandler(event) {
  log.debug "Keypad manager caught alarm status change: "+event.value
  if (runDefaultAlarm && event.value == "off"){
    keypad?.setDisarmed()
  }
  else if (runDefaultAlarm && event.value == "away"){
    keypad?.setArmedAway()
  }
  else if (runDefaultAlarm && event.value == "stay") {
    keypad?.setArmedStay()
  }
}

private sendSHMEvent(String shmState) {
  keypad.acknowledgeArmRequest(3)
  def event = [
        name:'alarmSystemStatus',
        value: shmState,
        displayed: true,
        description: "System Status is ${shmState}"
      ]
  log.debug "test ${event}"
  sendLocationEvent(event)
}

private execRoutine(armMode, userApp) {
  if (armMode == 'away') {
    if (armRoutine) {
      location.helloHome?.execute(armRoutine)
    }
    if (userApp.armRoutine) {
      location.helloHome?.execute(armRoutine)
    }
    if (parent.armRoutine) {
      location.helloHome?.execute(parent.armRoutine)
    }
  } else if (armMode == 'stay') {
    if (stayRoutine) {
      location.helloHome?.execute(stayRoutine)
    }
    if (userApp.stayRoutine) {
      location.helloHome?.execute(stayRoutine)
    }
    if (parent.stayRoutine) {
      location.helloHome?.execute(parent.stayRoutine)
    }
  } else if (armMode == 'off') {
    if (disarmRoutine) {
      location.helloHome?.execute(disarmRoutine)
    }
    if (userApp.disarmRoutine) {
      location.helloHome?.execute(userApp.disarmRoutine)
    }
    if (parent.disarmRoutine) {
      location.helloHome?.execute(parent.disarmRoutine)
    }
  } else if (armMode == 'night') {
    if (nightRoutine) {
      location.helloHome?.execute(nightRoutine)
    }
    if (userApp.nightRoutine) {
      location.helloHome?.execute(userApp.nightRoutine)
    }
    if (parent.nightRoutine) {
      location.helloHome?.execute(parent.nightRoutine)
    }
  }
}

def codeEntryHandler(evt) {
  //do stuff
  log.debug "Caught code entry event! ${evt.value.value}"

  def codeEntered = evt.value as String

  def data = evt.data as String
  def armMode = ''
  def currentarmMode = keypad.currentValue('armMode')
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

  def message = ''

  def correctUser = parent.keypadMatchingUser(codeEntered)

  if (correctUser) {
    atomicState.tries = 0
    log.debug "Correct PIN entered. Change SHM state to ${armMode}"
    //log.debug "Delay: ${armDelay}"
    //log.debug "Data: ${data}"
    //log.debug "armMode: ${armMode}"

    if (data == '0') {
      //log.debug "sendDisarmCommand"
      execRoutine('off', correctUser)
      if (runDefaultAlarm) {
        runIn(0, 'sendDisarmCommand')
      }
      message = "${evt.displayName} was disarmed by ${correctUser.label}"
    }
    else if (data == "1") {
      //log.debug "sendStayCommand"
      execRoutine('stay', correctUser)
      if (runDefaultAlarm) {
        if(armDelay > 0) {
          keypad.setExitDelay(armDelay)
        }
        runIn(armDelay, "sendStayCommand")
      }
      message = "${evt.displayName} was armed to 'Stay' by ${correctUser.label}"
    }
    else if (data == "2") {
      //log.debug "sendNightCommand"
      execRoutine('night', correctUser)
      if (runDefaultAlarm) {
        if(armDelay > 0) {
          keypad.setExitDelay(armDelay)
        }
        runIn(armDelay, 'sendNightCommand')
      }
      message = "${evt.displayName} was armed to 'Night' by ${correctUser.label}"
    }
    else if (data == "3") {
      //log.debug "sendArmCommand"
      execRoutine('away', correctUser)
      if (runDefaultAlarm) {
        if(armDelay > 0) {
          keypad.setExitDelay(armDelay)
        }
        runIn(armDelay, "sendArmCommand")
      }
      message = "${evt.displayName} was armed to 'Away' by ${correctUser.label}"
    }

    log.debug "${message}"
    correctUser.send(message)
  } else {
    log.debug 'Incorrect code!'
    atomicState.tries = atomicState.tries + 1
    if (atomicState.tries >= attemptTollerance) {
      keypad.sendInvalidKeycodeResponse()
      atomicState.tries = 0
    }
  }
}
def sendArmCommand() {
  log.debug 'Sending Arm Command.'
  keypad.acknowledgeArmRequest(3)
  sendSHMEvent('away')
}
def sendDisarmCommand() {
  log.debug 'Sending Disarm Command.'
  keypad.acknowledgeArmRequest(0)
  sendSHMEvent('off')
}
def sendStayCommand() {
  log.debug 'Sending Stay Command.'
  keypad.acknowledgeArmRequest(1)
  sendSHMEvent('stay')
}
def sendNightCommand() {
  log.debug 'Sending Night Command.'
  keypad.acknowledgeArmRequest(2)
  sendSHMEvent('stay')
}
