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
  subscribe(lock, "reportAllCodes", pollCodeReport, [filterEvents:false])
  subscribe(lock, "lock", codeUsed)
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
          para += "You have choosen to skip seep process.\n\n"
          para += "This will save about ${estimatedMinutes} Minutes.\n\n"
          para += "Do this at your own risk.  The sweep process will prevent conflicts."
        }
        paragraph para
        input(name: 'skipSweep', title: 'Skip Sweep?', type: 'bool', required: true, description: 'Skip Process', defaultValue: false, submitOnChange: true)
      }

      def actions = location.helloHome?.getPhrases()*.label
      href(name: 'toNotificationPage', page: 'lockNotificationPage', title: 'Notification Settings', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')
      if (actions) {
        href(name: 'toHelloHomePage', page: 'lockHelloHomePage', title: 'Hello Home Settings', image: 'https://images.lockmanager.io/app/v1/images/home.png')
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

def getLockMaxCodes() {
  // Check to see if the Lock Handler knows how many slots there are
  if (lock?.hasAttribute('maxCodes')) {
    def slotCount = lock.latestValue('maxCodes')
    debugger("Lock Supports ${slotCount} slots")
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
  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.requestCount = 0
    // skipSweep may be null
    if (skipSweep != true) {
      state.sweepMode = 'Enabled'
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
    debugger("Lock Supports ${slotCount} slots")
    state.codeSlots = slotCount
  }
  def codeSlots = lockCodeSlots()

  (1..codeSlots).each { slot ->
    def control = 'available'

    if (state.codes["slot${slot}"] == null) {
      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      state.codes["slot${slot}"].attempts = 0
      state.codes["slot${slot}"].codeState = 'unknown'
    }
    def userApp = findSlotUserApp(slot)
    if (userApp) {
      // there's a smartApp for this slot
      control = 'controller'
    } else if (state.codes["slot${slot}"].control == 'api') {
      // don't change from API control
      control = 'api'
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
      if (slotData.codeState == 'unknown') {
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
  def timeOut = 10 + (count * 5)

  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    debugger('Sweeping')
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
  def previousCode = null
  debugger("name: ${name} slot: ${slot} data: ${data} description: ${description} activity: ${activity[0]}")

  def code = null
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    code = userApp.userCode
  }

  switch (slot) {
    case 251:
      debugger("Incorrect Slots: ${state.incorrectSlots}")
      debugger('A code is a duplicate of Master but unknown which.' + state.incorrectSlots.size())
      if (state.incorrectSlots.size() == 1) {
        // the only slot to set must be the incorrect one!
        def errorSlot = state.incorrectSlots[0]
        userApp = findSlotUserApp(errorSlot)
        // We can set this reason code immediatly
        userApp.disableAndSetReason(lock.id, 'Conflicts with Master Code')

        state.codes["slot${errorSlot}"]['code'] = null
        state.codes["slot${errorSlot}"]['codeState'] = 'known'
      }
      break
    default:
      previousCode = state.codes["slot${slot}"]['code']
      switch (activityType) {
        case 'unset':
          state.codes["slot${slot}"]['code'] = null
          state.codes["slot${slot}"]['codeState'] = 'known'
          debugger("Slot:${slot} is no longer set!")
          break
        case 'changed':
        case 'set':
          // we're assuming the change was made correctly
          state.codes["slot${slot}"]['code'] = code
          state.codes["slot${slot}"]['codeState'] = 'known'
          debugger("Slot:${slot} is set!")
          break
        case 'failed':
          if (userApp) {
            userApp.disableAndSetReason(lock.id, 'Code failed to set.  Possible duplicate or invalid PIN')
          }
          debugger("Slot:${slot} failed!")
          state.codes["slot${slot}"]['code'] = 'invalid'
          state.codes["slot${slot}"]['codeState'] = 'known'
          break
        default:
          // unknown action
          break
      }
  }
  if (previousCode != code) {
    // code changed, let's inform!
    codeInform(slot, code)
  }
}

def codeUsed(evt) {
  debugger('Lock Event')
  def lockId = lock.id
  def message = ''
  def action = evt.value
  def userApp = false
  def codeUsed = false
  def manualUse = false
  def data = false

  if (evt.data) {
    data = new JsonSlurper().parseText(evt.data)
    codeUsed = data.usedCode
    if (codeUsed?.isNumber()) {
      userApp = findSlotUserApp(codeUsed)
    }
  }

  if (!data || data?.usedCode == 'manual') {
    manualUse = true
  }

  if (action == 'unlocked') {
    state.lockState = 'unlocked'
    debugger('UNLOCKED')
    // door was unlocked
    if (userApp) {
      message = "${lock.label} was unlocked by ${userApp.userName}"
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
        executeHelloPresenceCheck(codeUnlockRoutine)
      }
      // global
      if (parent.codeUnlockRoutine) {
        parent.executeHelloPresenceCheck(parent.codeUnlockRoutine)
      }

    } else if (manualUse) {
      // unlocked manually

      // lock specific
      if (manualUnlockRoutine) {
        executeHelloPresenceCheck(manualUnlockRoutine)
      }
      // global
      if (parent.manualUnlockRoutine) {
        parent.executeHelloPresenceCheck(parent.manualUnlockRoutine)
      }

      message = "${lock.label} was unlocked manually"
      if (notifyManualUnlock) {
        sendLockMessage(message)
      }
      if (alexaManualUnlock) {
        sendLockMessage(message)
      }
    }
  }
  if (action == 'locked') {
    state.lockState = 'locked'
    debugger('LOCKED')
    // door was locked
    if (userApp) {
      message = "${lock.label} was locked by ${userApp.userName}"
      // user specific
      if (userApp.userLockPhrase) {
        userApp.executeHelloPresenceCheck(userApp.userLockPhrase)
      }
      // lock specific
      if (codeLockRoutine) {
        executeHelloPresenceCheck(codeLockRoutine)
      }
      // gobal
      if (parent.codeLockRoutine) {
        parent.executeHelloPresenceCheck(parent.codeLockRoutine)
      }
    }
    if (data?.usedCode == -1) {
      message = "${lock.label} was locked by keypad"
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
    if (manualUse) {
      // locked manually
      message = "${lock.label} was locked manually"

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
    }
  }

  // decide if we should send a message per the userApp
  if (userApp && message) {
    debugger("Sending message: " + message)
    if (action == 'unlocked') {
      if (userApp.notifyAccess || parent.notifyAccess) {
        userApp.sendUserMessage(message)
      }
      if (userApp.alexaAccess || parent.alexaAccess) {
        userApp.sendAskAlexaLock(message)
      }
    } else if (action == 'locked') {
      if (userApp.notifyLock || parent.notifyLock) {
        userApp.sendUserMessage(message)
      }
      if (userApp.alexaLock || parent.alexaLock) {
        userApp.sendAskAlexaLock(message)
      }
    }
  }

  if (parent.apiApp()) {
    debugger('send to api!')
    def apiApp = parent.apiApp()
    apiApp.codeUsed(app, action, codeUsed)
  }
}

def setCodes() {
  // set what each slot should be in memory
  if (state.sweepMode == 'Enabled') {
    debugger('Not running code logic, Sweep mode is Enabled')
    return
  }
  // set incorrect slot array to blank
  state.incorrectSlots = []

  debugger('run code logic')
  def codes = state.codes
  // preload user list for performance
  codes.each { data ->
    data = data.value

    switch(state.codes["slot${data.slot}"].control) {
      case 'controller':
        def lockUser = findSlotUserApp(data.slot)
        if (lockUser?.isActive(lock.id)) {
          // is active, should be set
          state.codes["slot${data.slot}"].correctValue = lockUser.userCode.toString()
        } else {
          // is inactive, should not be set
          state.codes["slot${data.slot}"].correctValue = null
        }
        break
      case 'api':
        // do nothing, correct code set by API service
        break
      default:
        // only overwrite if enabled
        if (parent.overwriteMode) {
          state.codes["slot${data.slot}"].correctValue = null
        }
        break
    }
  }
  // After setting code data, send to the lock
  runIn(15, loadCodes)
}

def loadCodes() {
  // send codes to lock
  debugger('running load codes')
  def array = []
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}

  def slotsWithIncorrectCodes = []

  def loadCount = 0

  sortedCodes.each { data ->
    // only set codes in groups of 10
    if (loadCount < 10) {
      data = data.value
      def currentCode = data.code.toString()
      def correctCode = data.correctValue.toString()
      if (currentCode != correctCode) {
        // code is set incorrectly on lock!
        debugger("${currentCode}:${correctCode} s:${data.slot}")
        if (data.attempts <= 10) {
          def code
          if (data.correctValue) {
            code = data.correctValue
          } else {
            code = ''
          }
          loadCount++
          slotsWithIncorrectCodes << data.slot
          array << ["code${data.slot}", code]
          state.codes["slot${data.slot}"].attempts = data.attempts + 1
        } else {
          state.codes["slot${data.slot}"].attempts = 0
          // we've tried this slot 10 times, time to disable it
          def userApp = findSlotUserApp(data.slot)
          userApp?.disableLock(lock.id)
        }
      } else {
        state.codes["slot${data.slot}"].attempts = 0
      }
    }
  }
  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    debugger("update: ${json} slots: ${slotsWithIncorrectCodes}")
    state.incorrectSlots = slotsWithIncorrectCodes
    lock.updateCodes(json)
    // After sending codes, run memory logic again
    runIn(45, setCodes)
  } else {
    // All done, codes should be correct
    state.incorrectSlots = slotsWithIncorrectCodes
    debugger('No codes to set')
  }
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
  def lockUsers = parent.getUserApps()
  return lockUsers.find { app -> app.userSlot.toInteger() == slot.toInteger() }
}

def codeInform(slot, code) {
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    def message = ''
    def isActive = userApp.isActive(lock.id)
    def userCode = userApp.userCode
    if (isActive) {
      if (userCode == code) {
        message = "${userApp.userName} now has access to ${lock.label}"
        if (userApp.notifyAccessStart || parent.notifyAccessStart) {
          userApp.sendUserMessage(message)
        }
      } else {
        // user should have access but the code is wrong!
      }
    } else {
      if (!code) {
        message = "${userApp.userName} no longer has access to ${lock.label}"
        if (userApp.notifyAccessEnd || parent.notifyAccessEnd) {
          userApp.sendUserMessage(message)
        }
      } else {
        // there's a code set for some reason
        // it should be deleted!
      }
    }
    debugger(message)
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
