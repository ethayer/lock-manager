def installedKeypad() {
  debugger("Keypad Installed with settings: ${settings}")
  initializeKeypad()
}

def updatedKeypad() {
  debugger("Keypad Updated with settings: ${settings}")
  initializeKeypad()
}

def initializeKeypad() {
  // reset listeners
  unsubscribe()
  atomicState.tries = 0
  atomicState.installComplete = true

  if (keypad) {
    subscribe(location, 'alarmSystemStatus', alarmStatusHandler)
    subscribe(keypad, 'codeEntered', codeEntryHandler)
  }
}

def isUniqueKeypad() {
  def unique = true
  if (!atomicState.installComplete) {
    // only look if we're not initialized yet.
    def keypadApps = parent.getKeypadApps()
    keypadApps.each { keypadApp ->
      if (keypadApp.keypad.id == keypad.id) {
        unique = false
      }
    }
  }
  return unique
}

def keypadLandingPage() {
  if (keypad) {
    def unique = isUniqueKeypad()
    if (unique){
      keypadMainPage()
    } else {
      keypadErrorPage()
    }
  } else {
    keypadSetupPage()
  }
}

def keypadSetupPage() {
  dynamicPage(name: 'keypadSetupPage', title: 'Setup Keypad', nextPage: 'keypadLandingPage', uninstall: true) {
    section('NOTE:') {
      def p =  'Locks with keypads ARE NOT KEYPADS in this context.\n\n'
          p += 'This integration works with stand-alone keypads only!'
      paragraph p
      paragraph 'For locks, use the Lock child-app.'
    }
    section('Choose keypad for this app') {
      input(name: 'keypad', title: 'Which keypad?', type: 'capability.lockCodes', multiple: false, required: true)
    }
  }
}

def keypadErrorPage() {
  dynamicPage(name: 'errorPage', title: 'Keypad Duplicate', uninstall: true, nextPage: 'keypadLandingPage') {
    section('Oops!') {
      paragraph 'The keypad that you selected is already installed. Please choose a different keypad or choose Remove'
    }
    section('Choose keypad for this app') {
      input(name: 'keypad', title: 'Which keypad?', type: 'capability.lockCodes', multiple: false, required: true)
    }
  }
}

def keypadMainPage() {
  dynamicPage(name: 'keypadMainPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section('Routines') {
      paragraph 'settings here are for this keypad only. Global keypad settings, use parent app.'
      input(name: 'runDefaultAlarm', title: 'Act as SHM device?', type: 'bool', defaultValue: true, description: 'Toggle this off if actions should not effect SHM' )
      input(name: 'armRoutine', title: 'Arm/Away routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'disarmRoutine', title: 'Disarm routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'stayRoutine', title: 'Arm/Stay routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'nightRoutine', title: 'Arm/Night routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'armDelay', title: 'Arm Delay (in seconds)', type: 'number', required: false)
      input(name: 'notifyIncorrectPin', title: 'Notify you when incorrect code is used?', type: 'bool', required: false)
      input(name: 'attemptTollerance', title: 'How many times can incorrect code be used before notification?', type: 'number', defaultValue: 3, required: true)
    }
    section('Setup', hideable: true, hidden: true) {
      input(name: 'keypad', title: 'Keypad', type: 'capability.lockCodes', multiple: false, required: true)
      label title: 'Label', defaultValue: "Keypad: ${keypad.label}", required: false, description: 'recommended to start with Keypad:'
    }
  }
}

def alarmStatusHandler(event) {
  debugger("Keypad manager caught alarm status change: ${event.value}")
  if (runDefaultAlarm && event.value == 'off'){
    keypad?.setDisarmed()
  }
  else if (runDefaultAlarm && event.value == 'away'){
    keypad?.setArmedAway()
  }
  else if (runDefaultAlarm && event.value == 'stay') {
    keypad?.setArmedStay()
  }
}

def codeEntryHandler(evt) {
  //do stuff
  debugger("Caught code entry event! ${evt.value.value}")

  def codeEntered = evt.value as String
  def data = evt.data as Integer
  def currentarmMode = keypad.currentValue('armMode')
  def correctUser = parent.keypadMatchingUser(codeEntered)

  if (correctUser) {
    atomicState.tries = 0
    debugger('Correct PIN entered.')
    armCommand(data, correctUser, codeEntered)
  } else {
    debugger('Incorrect code!')
    atomicState.tries = atomicState.tries + 1
    if (atomicState.tries >= attemptTollerance) {
      keypad.sendInvalidKeycodeResponse()
      atomicState.tries = 0
    }
  }
}

def armCommand(value, correctUser, enteredCode) {
  def armMode
  def action
  keypad.acknowledgeArmRequest(value)
  switch (value) {
    case 0:
      armMode = 'off'
      action = 'disarmed'
      break
    case 1:
      armMode = 'stay'
      action = 'armed to \'Stay\''
      break
    case 2:
      armMode = 'night'
      action = 'armed to \'Night\''
      break
    case 3:
      armMode = 'away'
      action = 'armed to \'Away\''
      break
    default:
      log.error "${app.label}: Unexpected arm mode sent by keypad!"
      armMode = false
      break
  }

  // only delay on ARM actions
  def useDelay = 0
  if (armMode != 'off' && armMode != 'stay') {
    useDelay = armDelay
  }

  if (useDelay > 0) {
    keypad.setExitDelay(useDelay)
  }
  if (armMode) {
    // set values for delayed event
    atomicState.codeEntered = enteredCode
    atomicState.armMode = armMode

    runIn(useDelay, execRoutine)
  }

  def message = "${keypad.label} was ${action} by ${correctUser.label}"

  debugger(message)
  correctUser.sendUserMessage(message)
}

def execRoutine() {
  debugger('executing keypad actions')
  def armMode = atomicState.armMode
  def userApp = parent.keypadMatchingUser(atomicState.codeEntered)

  sendSHMEvent(armMode)

  // run hello home actions
  if (armMode == 'away') {
    if (armRoutine) {
      location.helloHome?.execute(armRoutine)
    }
    if (userApp.armRoutine) {
      location.helloHome?.execute(userApp.armRoutine)
    }
    if (parent.armRoutine) {
      location.helloHome?.execute(parent.armRoutine)
    }
  } else if (armMode == 'stay') {
    if (stayRoutine) {
      location.helloHome?.execute(stayRoutine)
    }
    if (userApp.stayRoutine) {
      location.helloHome?.execute(userApp.stayRoutine)
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

def sendSHMEvent(armMode) {
  def event = [
        name:'alarmSystemStatus',
        value: armMode,
        displayed: true,
        description: "System Status is ${armMode}"
      ]
  debugger("Event: ${event}")
  if (runDefaultAlarm) {
    sendLocationEvent(event)
  }
}
