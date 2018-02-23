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
    section("Settings") {
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
      paragraph 'Lock Manager © 2017 v1.4'
    }
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

  def needPoll = initSlots()

  if (needPoll || !state.initializeComplete) {
    debugger('needs poll')
    // get report from lock -> reportAllCodes()
    lock.poll()
  }
  setCodes()
}

def initSlots() {
  def codeSlots = lockCodeSlots()
  def needPoll = false
  def userApp = false

  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.requestCount = 0
    state.initializeComplete = false
    state.installComplete = true
    state.refreshComplete = true
    state.supportsKeypadData = true
    state.pinLength = false
  }
  if (lock?.hasAttribute('pinLength')) {
    state.pinLength = lock.latestValue('pinLength')
  }

  (1..codeSlots).each { slot ->
    def control = 'available'

    if (state.codes["slot${slot}"] == null) {
      needPoll = true

      state.initializeComplete = false
      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      state.codes["slot${slot}"].attempts = 0
      state.codes["slot${slot}"].codeState = 'unknown'
    }
    userApp = findSlotUserApp(slot)
    if (userApp) {
      // there's a smartApp for this slot
      control = 'controller'
    } else if (state.codes["slot${slot}"].control == 'api') {
      // don't change from API control
      control = 'api'
    }
    state.codes["slot${slot}"].control = control
  }
  return needPoll
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
  def slot = activity[0][1]
  def activityType = activity[0][2]

  debugger("name: ${name} slot: ${slot} data: ${data} description: ${description} activity: ${activity[0]}")
  def previousCode = state.codes["slot${slot}"]['code']

  def code = null
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    code = userApp.userCode
  }
  switch (activityType) {
    case 'unset':
      state.codes["slot${slot}"]['code'] = null
      state.codes["slot${slot}"]['codeState'] = 'known'
      debugger("Slot:${slot} is no longer set!")
      break
    case 'changed':
      // we're assuming the change was made correctly
      state.codes["slot${slot}"]['code'] = code
      state.codes["slot${slot}"]['codeState'] = 'known'
      debugger("Slot:${slot} is set!")
    default:
      // do nothing I'm not sure what happened
      break
  }
}


def pollCodeReport(evt) {
  def codeData = new JsonSlurper().parseText(evt.data)

  state.codeSlots = codeData.codes
  def codeSlots = lockCodeSlots()
  initSlots()

  debugger("Received: ${codeData}")
  (1..codeSlots).each { slot->
    def code = codeData."code${slot}"
    if (code != null) { //check to make sure code isn't already null, which will cause .isNumber() to error. --DiddyWolf
      if (code.isNumber()) {
        // do nothing, looks good!
        } else {
        // It's easier on logic if code is empty to be null
         code = null
      }
    }

    state.codes["slot${slot}"]['code'] = code
    if (state.codes["slot${slot}"]['codeState'] != 'refresh') {
      // don't change state if code in refresh mode
      state.codes["slot${slot}"]['codeState'] = 'known'
    }
  }
  state.initializeComplete = true
  // Set codes loaded, set new codes.
  setCodes()
}

def codeUsed(evt) {
  debugger('Code USED')
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
  debugger('run code logic')
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}
  sortedCodes.each { data ->
    data = data.value
    def lockUser = findSlotUserApp(data.slot)
    if (lockUser) {
      if (lockUser.isActive(lock.id)) {
        // is active, should be set
        state.codes["slot${data.slot}"].correctValue = lockUser.userCode.toString()
      } else {
        // is inactive, should not be set
        state.codes["slot${data.slot}"].correctValue = null
      }
    } else if (state.codes["slot${data.slot}"].control == 'api') {
      // do nothing! allow API to handle it.
    } else if (parent.overwriteMode) {
      state.codes["slot${data.slot}"].correctValue = null
    } else {
      // do nothing!
    }
  }
  if (state.initializeComplete && state.refreshComplete) {
    runIn(5, loadCodes)
  } else {
    debugger('initialize not yet complete!')
  }
}

def loadCodes() {
  debugger('running load codes')
  def array = []
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}
  sortedCodes.each { data ->
    data = data.value
    def currentCode = data.code.toString()
    def correctCode = data.correctValue.toString()
    if (currentCode != correctCode) {
      debugger("${currentCode}:${correctCode} s:${data.slot}")
      if (data.attempts <= 10) {
        def code
        if (data.correctValue) {
          code = data.correctValue
        } else {
          code = ''
        }
        array << ["code${data.slot}", code]
        state.codes["slot${data.slot}"].attempts = data.attempts + 1
      } else {
        state.codes["slot${data.slot}"].attempts = 0
        def userApp = findSlotUserApp(data.slot)
        if (userApp) {
          userApp.disableLock(lock.id)
        }
      }
    } else {
      state.codes["slot${data.slot}"].attempts = 0
    }
  }
  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    debugger("update: ${json}")
    lock.updateCodes(json)
    runIn(30, setCodes)
  } else {
    debugger('No codes to set')
  }
}

def findSlotUserApp(slot) {
  def foundLockUser = false
  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    def userSlot = lockUser.userSlot
    if (userSlot.toInteger() == slot.toInteger()) {
      foundLockUser = lockUser
    }
  }
  return foundLockUser
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

def isCodeComplete() {
  return state.initializeComplete
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

def slotData(slot) {
  state.codes["slot${slot}"]
}

def lockState() {
  state.lockState
}

def enableUser(slot) {
  state.codes["slot${slot}"].attempts = 0
  runIn(10, setCodes)
}

def pinLength() {
  return state.pinLength
}
