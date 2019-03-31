def lockInstalled() {
  debugger("Lock Installed with settings: ${settings}")
  lockInitialize()
}

def lockUpdated() {
  debugger("Lock Updated with settings: ${settings}")
  lockInitialize()
}

def lockInitialize() {
  // reset listeners
  unsubscribe()
  unschedule()
  subscribe(lock, 'codeChanged', updateCode, [filterEvents:false])
  subscribe(lock, "lock", lockEvent)
  // Allow save and run setup in headless mode
  queSetupLockData()
}


def isUniqueLock() {
  def unique = true
  if (!state.installComplete) {
    // only look if we're not initialized yet.
    def lockApps = parent.getLockApps()
    lockApps.each { lockApp ->
      debugger(lockApp.lock.id)
      if (lockApp.lock.id == lock.id) {
        unique = false
      }
    }
  }
  return unique
}

def lockLandingPage() {
  if (lock) {
    def unique = isUniqueLock()
    if (unique){
      lockMainPage()
    } else {
      lockErrorPage()
    }
  } else {
    lockSetupPage()
  }
}

def lockSetupPage() {
  dynamicPage(name: "lockSetupPage", title: "Setup Lock", nextPage: "lockLandingPage", uninstall: true) {
    section("Choose devices for this lock") {
      input(name: "lock", title: "Which Lock?", type: "capability.Lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def lockMainPage() {
  dynamicPage(name: "lockMainPage", title: "Lock Settings", install: true, uninstall: true) {
    getLockMaxCodes()
    section("Settings") {
      if (state.installComplete) {
        if (state.sweepMode == 'Enabled') {
          def completeCount = sweepProgress()
          def totalSlots = lockCodeSlots()
          def percent = Math.round((completeCount/totalSlots) * 100)
          def estimatedMinutes = ((totalSlots - completeCount) * 6) / 60
          def p = ""
          p += "${percent}%\n"
          p += 'Sweep is in progress.\n'
          p += "Progress: ${completeCount}/${totalSlots}\n\n"

          p += "Estimated time left: ${estimatedMinutes} Minutes\n"
          p += "Lock will set codes after sweep is complete."
          paragraph p
        }
      } else {
        def totalSlots = lockCodeSlots()
        def estimatedMinutes = (totalSlots * 6) / 60
        def para = ""
        if (!skipSweep) {
          para += "This lock will take about \n${estimatedMinutes} Minutes to install.\n\n"
          para += "You may skip the sweep process and save this time."
        } else {
          para += "WARNING:\n"
          para += "You have choosen to skip the sweep process.\n\n"
          para += "This will save about ${estimatedMinutes} Minutes.\n\n"
          para += "Do this at your own risk.  The sweep process will prevent conflicts."
        }
        paragraph para
        input(name: 'skipSweep', title: 'Skip Sweep?', type: 'bool', required: true, description: 'Skip Process', defaultValue: false, submitOnChange: true)
      }

      def actions = location.helloHome?.getPhrases()*.label
      href(name: 'toNotificationPage', page: 'lockNotificationPage', title: 'Notification Settings', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')
      if (actions) {
        href(name: 'toLockHelloHomePage', page: 'lockHelloHomePage', title: 'Hello Home Settings', image: 'https://images.lockmanager.io/app/v1/images/home.png')
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label title: 'Label', defaultValue: "Lock: ${lock.label}", required: false, description: 'recommended to start with Lock:'
      input(name: 'lock', title: 'Which Lock?', type: 'capability.lock', multiple: false, required: true)
      input(name: 'contactSensor', title: 'Which contact sensor?', type: "capability.contactSensor", multiple: false, required: false)
      input(name: 'slotCount', title: 'How many slots?', type: 'number', multiple: false, required: false, description: 'Overwrite number of slots supported.')
    }
  }
}

def lockHelloHomePage() {
  dynamicPage(name: 'helloHomePage', title: 'Hello Home Settings (optional)') {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section('Hello Home Phrases') {
      input(name: 'manualUnlockRoutine', title: 'On Manual Unlock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
      input(name: 'manualLockRoutine', title: 'On Manual Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/lock.png')

      input(name: 'codeUnlockRoutine', title: 'On Code Unlock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png' )

      paragraph 'Supported on some locks:'
      input(name: 'codeLockRoutine', title: 'On Code Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/lock.png')
      input(name: 'keypadLockRoutine', title: 'On Keypad Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/lock.png')

      paragraph 'These restrictions apply to all the above:'
      input "userNoRunPresence", "capability.presenceSensor", title: "DO NOT run Actions if any of these are present:", multiple: true, required: false
      input "userDoRunPresence", "capability.presenceSensor", title: "ONLY run Actions if any of these are present:", multiple: true, required: false
    }
  }
}

def getLockMaxCodes() {
  // Check to see if the Lock Handler knows how many slots there are
  if (lock?.hasAttribute('maxCodes')) {
    def slotCount = lock.latestValue('maxCodes')
    state.codeSlots = slotCount
  }
}

def isInit() {
  return (state.initializeComplete)
}

def lockErrorPage() {
  dynamicPage(name: 'lockErrorPage', title: 'Lock Duplicate', uninstall: true, nextPage: 'lockLandingPage') {
    section('Oops!') {
      paragraph 'The lock that you selected is already installed. Please choose a different Lock or choose Remove'
    }
    section('Choose devices for this lock') {
      input(name: 'lock', title: 'Which Lock?', type: 'capability.lock', multiple: false, required: true)
      input(name: 'contactSensor', title: 'Which contact sensor?', type: 'capability.contactSensor', multiple: false, required: false)
    }
  }
}

def lockNotificationPage() {
  dynamicPage(name: 'lockNotificationPage', title: 'Notification Settings') {
    section {
      paragraph 'Some options only work on select locks'
      if (!state.supportsKeypadData) {
        paragraph 'This lock only reports manual messages.\n It does not support code on lock or lock on keypad.'
      }
      if (phone == null && !notification && !recipients) {
        input(name: 'muteLock', title: 'Mute this lock?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
      }
      if (!muteLock) {
        input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/book.png')
        href(name: 'toAskAlexaPage', title: 'Ask Alexa', page: 'lockAskAlexaPage', image: 'https://images.lockmanager.io/app/v1/images/Alexa.png')
        if (!recipients) {
          input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
          paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
          input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
        }
        if (phone != null || notification || recipients) {
          input(name: 'notifyManualLock', title: 'On Manual Turn (Lock)', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/lock.png')
          input(name: 'notifyManualUnlock', title: 'On Manual Turn (Unlock)', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
          if (state.supportsKeypadData) {
            input(name: 'notifyKeypadLock', title: 'On Keypad Press to Lock', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
          }
        }
      }
    }
    if (!muteLock) {
      section('Only During These Times (optional)') {
        input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
        input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
      }
    }
  }
}


def queSetupLockData() {
  state.installComplete = true
  runIn(10, setupLockData)
}

def setupLockData() {
  debugger('run lock data setup')

  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    // initialize data attributes for this lock.
    lockUser.initializeLockData()
  }
  if (state.requestCount == null) {
    state.requestCount = 0
  }

  initSlots()
}

def initSlots() {
  def codeState = 'unknown'
  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.requestCount = 0
    // skipSweep may be null
    if (skipSweep != true) {
      state.sweepMode = 'Enabled'
      codeState = 'sweep'
    }
    state.refreshComplete = true
    state.supportsKeypadData = true
    state.pinLength = false
  }
  if (lock?.hasAttribute('pinLength')) {
    state.pinLength = lock.latestValue('pinLength')
  }

  // Check to see if the Lock Handler knows how many slots there are
  if (lock?.hasAttribute('maxCodes')) {
    def slotCount = lock.latestValue('maxCodes')
    state.codeSlots = slotCount
  }

  def userCodeSlots = getUserSlotList()
  def codeSlots = lockCodeSlots()

  (1..codeSlots).each { slot ->
    def control = 'available'
    if (state.codes["slot${slot}"] == null) {
      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      // set attempts
      state.codes["slot${slot}"].attempts = 0
      state.codes["slot${slot}"].recoveryAttempts = 0
      state.codes["slot${slot}"].namedSlot = false
      state.codes["slot${slot}"].codeState = codeState
      state.codes["slot${slot}"].control = control
    }

    // manage controll type
    def currentControl = state.codes["slot${slot}"].control
    switch (currentControl) {
      case 'available':
      case 'controller':
        if (userCodeSlots.contains(slot.toInteger())) {
          control = 'controller'
        }
        break
      case 'api':
      default:
      // nothing to do
        break
    }
    state.codes["slot${slot}"].control = control
  }
  if (state.sweepMode == 'Enabled') {
    state.sweepProgress = 0
    sweepSequance()
  } else {
    setCodes()
  }
}

def sweepSequance() {
  def codeSlots = lockCodeSlots()
  def array = []
  def count = 0
  def completeCount = 0
  (1..codeSlots).each { slot ->
    // sweep in packages of 10
    if (count == 10) {
      // do nothing ~ We're going to stop adding codes for now.
    } else {
      def slotData = state.codes["slot${slot}"]
      if (slotData.codeState == 'sweep') {
        count++
        array << ["code${slotData.slot}", null]
      } else {
        // This code is already known/unset!
        completeCount++
        state.sweepProgress = completeCount
      }
    }
  }

  // allow 10 and 5 seconds per code delete
  def timeOut = 10 + (count * 6)

  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    debugger("Progress: ${completeCount}/${codeSlots} Data: ${json}")
    lock.updateCodes(json)
    runIn(timeOut, sweepSequance)
  } else {
    debugger('Sweep Completed!')
    state.sweepMode = 'Disabled'
    // Allow some cooldown time to prevent conflicts
    runIn(15, setCodes)
  }
}

def withinAllowed() {
  return (state.requestCount <= allowedAttempts())
}

def allowedAttempts() {
  return lockCodeSlots() * 2
}

def updateCode(event) {
  def data = new JsonSlurper().parseText(event.data)
  def name = event.name
  def description = event.descriptionText
  def activity = event.value =~ /(\d{1,3}).(\w*)/
  def slot = activity[0][1].toInteger()
  def activityType = activity[0][2]
  def previousCode = state.codes["slot${slot}"].code

  debugger("name: ${name} slot: ${slot} data: ${data} description: ${description} activity: ${activity[0]}")

  def code = null
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    code = userApp.userCode
  }

  def codeState
  def previousCodeState = state.codes["slot${slot}"].codeState
  switch (slot) {
    case 251:
      // code is duplicate of master
      if (state.incorrectSlots.size() == 1) {
        // the only slot to set must be the incorrect one!
        def errorSlot = state.incorrectSlots[0]
        userApp = findSlotUserApp(errorSlot)
        // We can set this reason code immediatly
        userApp.disableAndSetReason(lock.id, 'Conflicts with Master Code')

        state.codes["slot${errorSlot}"].code = null
        state.codes["slot${errorSlot}"].codeState = 'known'
      }
      break
    default:
      switch (activityType) {
        case 'unset':
          debugger("Slot:${slot} is no longer set!")
          if (previousCodeState == 'unset' || state.sweepMode) {
             codeState = 'correct'
          } else {
            // We were not expecting an unset!
            codeState = 'unexpected'
          }
          state.codes["slot${slot}"].code = null
          state.codes["slot${slot}"].codeState = codeState
          break
        case 'changed':
        case 'set':
          switch(previousCodeState) {
            case 'set':
            case 'recovery':
              codeState = 'correct'
              debugger("Slot:${slot} is set!")
              break
            default:
              // We didnt expect a set, lets unset it and set the correct code
              debugger("Slot:${slot} unexpected set!")
              if (userApp) {
                // we have to delete it and set it again,
                // because if it's the same as user's PIN
                // it will error
                failRecovery(slot, previousCodeState, userApp)
              } else {
                // We can just delete the code because we don't want anything there
                code = 'invalid'
                codeState = 'unexpected'
              }

              break
          }
          state.codes["slot${slot}"].code = code
          state.codes["slot${slot}"].codeState = codeState
          break
        case 'failed':
          failRecovery(slot, previousCodeState, userApp);
          break
        default:
          // unknown action
          break
      }
  } //end switch(slot)

  switch (codeState) {
    case 'correct':
      if (previousCodeState == 'set') {
        codeInform(slot, 'access')
      } else if (previousCodeState == 'unset') {
        codeInform(slot, 'revoked')
      }

      break
    case 'unexpected':
      // run code logic, and reset code
      switch (activityType) {
        case 'set':
        case 'changed':
          codeInform(slot, 'unexpected-set')
          break
        case 'unset':
        default:
          codeInform(slot, 'unexpected-unset')
          break
      }
      debugger('Unexpected change!  Scheduling code logic.')
      runIn(25, setCodes)
      break
    case 'failed':
      if (previousCodeState == 'unset') {
        // I'm not sure if this would ever happen...
        codeInform(slot, 'failed-unset')
      } else if (previousCodeState == 'set') {
        codeInform(slot, 'failed-set')
      }
  }
}

def failRecovery(slot, previousCodeState, userApp) {
  def attempts = state.codes["slot${slot}"].recoveryAttempts
  if (attempts > 3) {
    if (userApp) {
      userApp.disableAndSetReason(lock.id, 'Code failed to set.  Possible duplicate or invalid PIN')
    }
    debugger("Slot:${slot} failed! Recovery failed.")
    state.codes["slot${slot}"].code = 'invalid'
    state.codes["slot${slot}"].codeState = 'failed'
  } else {
    debugger("Slot:${slot} failed, attempting recovery.")
    state.codes["slot${slot}"].recoveryAttempts = attempts + 1
    state.codes["slot${slot}"].code = 'invalid'
    state.codes["slot${slot}"].codeState = 'recovery'
  }
}

def lockEvent(evt) {
  def data = new JsonSlurper().parseText(evt.data)
  debugger("Lock event. ${data}")

  switch(data.method) {
    case 'keypad':
      keypadLockEvent(evt, data)
      break
    case 'manual':
      manualUnlock(evt)
      break
    case 'command':
      commandUnlock(evt)
      break
    case 'auto':
      autoLock(evt)
      break
  }
}

def keypadLockEvent(evt, data) {
  def message
  def userApp = findSlotUserApp(data.usedCode)
  if (evt.value == 'locked') {
    if (userApp) {
      userDidLock(userApp)
    } else {
      message = "${lock.label} was locked by keypad"
      debugger(message)
      if (keypadLockRoutine) {
        executeHelloPresenceCheck(keypadLockRoutine)
      }
      if (notifyKeypadLock) {
        sendLockMessage(message)
      }
      if (alexaKeypadLock) {
        askAlexaLock(message)
      }
    }
  } else if (evt.value == 'unlocked') {
    if (userApp) {
      userDidUnlock(userApp)
    } else {
      debugger('Lock was locked by unknown user!')
      // unlocked by unknown user?
    }
  }
}

def userDidLock(userApp) {
  def message = "${lock.label} was locked by ${userApp.userName}"
  debugger(message)
  // user specific
  if (userApp.userLockPhrase) {
    userApp.executeHelloPresenceCheck(userApp.userLockPhrase)
  }
  // lock specific
  if (codeLockRoutine) {
    userApp.executeHelloPresenceCheck(codeLockRoutine)
  }
  // global
  if (parent.codeLockRoutine) {
    parent.executeHelloPresenceCheck(parent.codeLockRoutine)
  }

  // messages
  if (userApp.notifyLock || parent.notifyLock) {
    userApp.sendUserMessage(message)
  }
  if (userApp.alexaLock || parent.alexaLock) {
    userApp.sendAskAlexaLock(message)
  }
}

def userDidUnlock(userApp) {
  def message
  message = "${lock.label} was unlocked by ${userApp.userName}"
  debugger(message)
  userApp.incrementLockUsage(lock.id)
  if (!userApp.isNotBurned()) {
    parent.setAccess()
    message += '.  Now burning code.'
  }
  // user specific
  if (userApp.userUnlockPhrase) {
    userApp.executeHelloPresenceCheck(userApp.userUnlockPhrase)
  }
  // lock specific
  if (codeUnlockRoutine) {
    userApp.executeHelloPresenceCheck(codeUnlockRoutine)
  }
  // global
  if (parent.codeUnlockRoutine) {
    parent.executeHelloPresenceCheck(parent.codeUnlockRoutine)
  }

  //Send Message
  if (userApp.notifyAccess || parent.notifyAccess) {
    userApp.sendUserMessage(message)
  }
  if (userApp.alexaAccess || parent.alexaAccess) {
    userApp.sendAskAlexaLock(message)
  }
}

def manualUnlock(evt) {
  def message
  if (evt.value == 'locked') {
    // locked manually
    message = "${lock.label} was locked manually"
    debugger(message)
    // lock specific
    if (manualLockRoutine) {
      executeHelloPresenceCheck(manualLockRoutine)
    }
    // global
    if (parent.manualLockRoutine) {
      parent.executeHelloPresenceCheck(parent.manualLockRoutine)
    }

    if (notifyManualLock) {
      sendLockMessage(message)
    }
    if (alexaManualLock) {
      askAlexaLock(message)
    }
  } else if (evt.value == 'unlocked') {
    message = "${lock.label} was unlocked manually"
    debugger(message)
    // lock specific
    if (manualUnlockRoutine) {
      executeHelloPresenceCheck(manualUnlockRoutine)
    }
    // global
    if (parent.manualUnlockRoutine) {
      parent.executeHelloPresenceCheck(parent.manualUnlockRoutine)
    }
  }
}

def commandUnlock(evt) {
  // no options for this scenario yet
}
def autoLock(evt) {
  // no options for this scenario yet
}

def setCodes() {
  def setValue
  def name
  // set what each slot should be in memory
  if (state.sweepMode == 'Enabled') {
    debugger('Not running code logic, Sweep mode is Enabled')
    return false
  }
  // set incorrect slot array to blank
  state.incorrectSlots = []

  debugger('run code logic')
  def codes = state.codes
  codes.each { data ->
    data = data.value
    name = false
    switch(data.control) {
      case 'controller':
        def lockUser = findSlotUserApp(data.slot)
        def codeState = state.codes["slot${data.slot}"].codeState
        if (lockUser?.isActive(lock.id) && codeState != 'recovery') {
          // is active, should be set
          setValue = lockUser.userCode.toString()
          state.codes["slot${data.slot}"].correctValue = setValue
          if (data.code.toString() != setValue) {
            state.codes["slot${data.slot}"].codeState = 'set'
          } else {
            // set name only if code is already set
            name = lockUser.userName
          }
        } else {
          // is inactive, should not be set
          setValue = null
          state.codes["slot${data.slot}"].correctValue = null
          if (data.code != setValue) {
            state.codes["slot${data.slot}"].codeState = 'unset'
          }
        }
        break
      case 'api':
        if (data.correctCode != null) {
          if (data.correctCode != data.code) {
            state.codes["slot${data.slot}"].codeState = 'unset'
          }
        } else if (data.correctCode.toString() != data.code.toString()) {
          state.codes["slot${data.slot}"].codeState = 'set'
        }

        // do nothing, correct code set by API service
        break
      default:
        // only overwrite if enabled
        if (parent.overwriteMode) {
          state.codes["slot${data.slot}"].correctValue = null
        }
        break
    }
    // ensure name is set correctly
    nameSlot(data.slot, name)
  }
  // After setting code data, send to the lock
  runIn(15, loadCodes)
}

def loadCodes() {
  // send codes to lock
  debugger('running load codes')
  def codesToSet
  def unsetCodes = collectCodesToUnset()
  // do this so we unset codes first
  if (unsetCodes.size > 0) {
    codesToSet = unsetCodes
  } else {
    codesToSet = collectCodesToSet()
  }

  def json = new groovy.json.JsonBuilder(codesToSet).toString()
  if (json != '[]') {
    debugger("update: ${json}")
    lock.updateCodes(json)
    // After sending codes, run memory logic again
    def timeOut = (codesToSet.size() * 6) + 10
    runIn(timeOut, setCodes)
  } else {
    // All done, codes should be correct
    debugger('No codes to set')
  }
}

def collectCodesToUnset() {
  def codes = state.codes

  def incorrectSlots = []
  def array = []
  def count = 0

  codes.each { data ->
    data = data.value

    if (count < 10) {
      def currentCode = data.code
      def correctCode = data.correctValue

      if (correctCode == null && currentCode != null) {
        array << ["code${data.slot}", code]
        incorrectSlots << data.slot
        count++
      }
    }
  }

  state.incorrectSlots = incorrectSlots
  return array
}

def collectCodesToSet() {
  def codes = state.codes

  def incorrectSlots = []
  def array = []
  def count = 0

  codes.each { data ->
    data = data.value

    if (count < 10) {
      def currentCode = data.code.toString()
      def correctCode = data.correctValue.toString()

      if (correctCode != currentCode && state.codes["slot${data.slot}"].attempts < 10) {
        array << ["code${data.slot}", correctCode]
        incorrectSlots << data.slot
        // increment attempt count
        state.codes["slot${data.slot}"].attempts = data.attempts + 1
        count++
      } else if (correctCode != currentCode && state.codes["slot${data.slot}"].attempts >= 10) {
        state.codes["slot${data.slot}"].attempts = 0
        // we've tried this slot 10 times, time to disable it
        def userApp = findSlotUserApp(data.slot)
        userApp?.disableLock(lock.id)
      } else {
        // code is correct
        state.codes["slot${data.slot}"].attempts = 0
      }
    }
  }

  state.incorrectSlots = incorrectSlots
  return array
}

def getUserSlotList() {
  def userSlots = []
  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    userSlots << lockUser.userSlot.toInteger()
  }
  return userSlots
}

def findSlotUserApp(slot) {
  if (slot) {
    def lockUsers = parent.getUserApps()
    return lockUsers.find { app -> app.userSlot.toInteger() == slot.toInteger() }
  } else {
    return false
  }
}

def codeInform(slot, action) {
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    def message = ''
    def shouldSend = false
    switch(action) {
      case 'access':
        message = "${userApp.userName} now has access to ${lock.label}"
        // add name
        nameSlot(slot, userApp.userName)
        if (userApp.notifyAccessStart || parent.notifyAccessStart) {
          shouldSend = true
        }
        break
      case 'revoke':
        // remove name
        nameSlot(slot, false)
        message = "${userApp.userName} no longer has access to ${lock.label}"
        if (userApp.notifyAccessEnd || parent.notifyAccessEnd) {
          shouldSend = true
        }
        break
      case 'unexpected-set':
        message = "Unexpected code in Slot:${slot}. ${userApp.userName} may not have valid access to ${lock.label}. Checking for issues."
        shouldSend = true
        break
      case 'unexpected-unset':
        message = "Unexpected code delete Slot:${slot}. ${userApp.userName} may not have valid access to ${lock.label}. Checking for issues."
        shouldSend = true
        break
      case 'failed-set':
        def disabledReason = userApp.disabledReason()
        message = "Controller failed to set code for ${userApp.name}. ${disabledReason}"
        shouldSend = true
        break
    }

    if (shouldSend) {
      userApp.sendUserMessage(message)
    }
    debugger(message)
  } else {
    // remove set user name, no app
    nameSlot(slot, false)
  }
}

def isCodeComplete() {
  if (state.sweepMode == 'Enabled') {
    return false
  } else {
    return true
  }
}

def doorOpenCheck() {
  def currentState = contact.contactState
  if (currentState?.value == 'open') {
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


def sendLockMessage(message) {
  if (notificationStartTime != null && notificationEndTime != null) {
    def start = timeToday(notificationStartTime)
    def stop = timeToday(notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessage(message)
    }
  } else {
    sendMessage(message)
  }
}
def sendMessage(message) {
  if (!muteLock) {
    if (recipients) {
      sendNotificationToContacts(message, recipients)
    } else {
      if (notification) {
        sendPush(message)
      } else {
        sendNotificationEvent(message)
      }
      if (phone) {
        if ( phone.indexOf(';') > 1){
          def phones = phone.split(';')
          for ( def i = 0; i < phones.size(); i++) {
            sendSms(phones[i], message)
          }
        }
        else {
          sendSms(phone, message)
        }
      }
    }
  } else {
    sendNotificationEvent(message)
  }
}

def nameSlot(slot, name) {
  if (state.codes["slot${slot}"].namedSlot != name) {
    state.codes["slot${slot}"].namedSlot = name
    lock.nameSlot(slot, name)
  }
}

def askAlexaLock(message) {
  if (!muteLock) {
    if (alexaStartTime != null && alexaEndTime != null) {
      def start = timeToday(alexaStartTime)
      def stop = timeToday(alexaEndTime)
      def now = new Date()
      if (start.before(now) && stop.after(now)){
        sendAskAlexa(message)
      }
    } else {
      sendAskAlexa(message)
    }
  }
}

def sendAskAlexaLock(message) {
  sendLocationEvent(name: 'AskAlexaMsgQueue',
                    value: 'LockManager/Lock',
                    isStateChange: true,
                    descriptionText: message,
                    unit: "Lock//${lock.label}")
}

def apiCodeUpdate(slot, code, control) {
  state.codes["slot${slot}"]['correctValue'] = code
  state.codes["slot${slot}"]['control'] = control
  setCodes()
}

def isRefreshComplete() {
  return state.refreshComplete
}

def totalUsage() {
  def usage = 0
  def userApps = parent.getUserApps()
  userApps.each { userApp ->
    def lockUsage = userApp.getLockUsage(lock.id)
    usage = usage + lockUsage
  }
  return usage
}

def lockCodeSlots() {
  // default to 30
  def codeSlots = 30
  if (slotCount) {
    // return the user defined value
    codeSlots = slotCount
  } else if (state?.codeSlots) {
    codeSlots = state.codeSlots.toInteger()
  }
  return codeSlots
}

def codeData() {
  return state.codes
}

def userPageCount() {
  def sortData = state.codes.sort{it.value.slot}
  def data = sortData.collect{ it }
  return data.collate(30).size()
}

def codeDataPaginated(page) {
  // collect a paginated list to prevent rate limit issues
  def sortData = state.codes.sort{it.value.slot}
  def data = sortData.collect{ it }
  return data.collate(30)[page]
}

def slotData(slot) {
  state.codes["slot${slot}"]
}

def lockState() {
  state.lockState
}

def sweepProgress() {
  state.sweepProgress
}

def enableUser(slot) {
  state.codes["slot${slot}"].attempts = 0
  runIn(10, setCodes)
}

def pinLength() {
  return state.pinLength
}
