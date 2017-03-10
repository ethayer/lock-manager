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
  page name: 'landingPage'
  page name: 'setupPage'
  page name: 'mainPage'
  page name: 'errorPage'
  page name: 'notificationPage'
  page name: 'helloHomePage'
  page name: 'infoRefreshPage'
}

def installed() {
  debugger("Installed with settings: ${settings}")
  initialize()
}

def updated() {
  debugger("Updated with settings: ${settings}")
  initialize()
}

def initialize() {
  // reset listeners
  unsubscribe()
  unschedule()
  subscribe(lock, 'codeReport', updateCode, [filterEvents:false])
  subscribe(lock, "lock", codeUsed)
  setupLockData()
}

def landingPage() {
  if (lock) {
    def unique = isUniqueLock()
    if (unique){
      mainPage()
    } else {
      errorPage()
    }
  } else {
    setupPage()
  }
}

def isUniqueLock() {
  def unique = true
  if (!state.installComplete) {
    // only look if we're not initialized yet.
    def lockApps = parent.getLockApps()
    lockApps.each { lockApp ->
      if (lockApp.lock.id == lock.id) {
        unique = false
      }
    }
  }
  return unique
}

def setupPage() {
  dynamicPage(name: "setupPage", title: "Setup Lock", nextPage: "landingPage", uninstall: true) {
    section("Choose devices for this lock") {
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      def actions = location.helloHome?.getPhrases()*.label
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
      href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/home.png')
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/home.png')
        if (state.initializeComplete && state.refreshComplete) {
          href(name: 'toInfoRefreshPage', page: 'infoRefreshPage', title: 'Refresh Lock Data', description: 'Tap to refresh', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/refresh.png')
        } else {
          paragraph 'Lock is loading data'
        }
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label title: 'Label', defaultValue: "Lock: ${lock.label}", required: true, description: 'recommended to start with Lock:'
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def errorPage() {
  dynamicPage(name: "errorPage", title: "Lock Duplicate", uninstall: true, nextPage: 'landingPage') {
    section("Oops!") {
      paragraph 'The lock that you selected is already installed. Please choose a different Lock or choose Remove'
    }
    section("Choose devices for this lock") {
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}
def infoRefreshPage() {
  dynamicPage(name: 'infoRefreshPage', title: "Lock Info Refresh", nextPage: 'landingPage') {
    refreshMode()
    section("Ok!") {
      paragraph 'Lock is now in refresh mode'
    }
  }
}

def notificationPage() {
  dynamicPage(name: "notificationPage", title: "Notification Settings") {
    section {
      paragraph 'Some options only work on select locks'
      if (!state.supportsKeypadData) {
        paragraph 'This lock only reports manual messages.\n It does not support code on lock or lock on keypad.'
      }
      if (phone == null && !notification && !sendevent && !recipients) {
        input(name: "muteLock", title: "Mute this lock?", type: "bool", required: false, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bell-slash-o.png')
      }
      if (!muteLock) {
        input("recipients", "contact", title: "Send notifications to", submitOnChange: true, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/book.png')
        if (!recipients) {
          input(name: "phone", type: "text", title: "Text This Number", description: "Phone number", required: false, submitOnChange: true)
          paragraph "For multiple SMS recipients, separate phone numbers with a semicolon(;)"
          input(name: "notification", type: "bool", title: "Send A Push Notification", description: "Notification", required: false, submitOnChange: true)
        }
        if (phone != null || notification || sendevent || recipients) {
          input(name: "notifyMaunualLock", title: "On Manual Turn (Lock)", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png')
          input(name: "notifyMaunualUnlock", title: "On Manual Turn (Unlock)", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/unlock-alt.png')
          if (state.supportsKeypadData) {
            input(name: "notifyKeypadLock", title: "On Keypad Press to Lock", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/unlock-alt.png')
          }
        }
      }
    }
    if (!muteLock) {
      section("Only During These Times (optional)") {
        input(name: "notificationStartTime", type: "time", title: "Notify Starting At This Time", description: null, required: false)
        input(name: "notificationEndTime", type: "time", title: "Notify Ending At This Time", description: null, required: false)
      }
    }
  }
}

def helloHomePage() {
  dynamicPage(name: 'helloHomePage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section("Hello Home Phrases") {
      input(name: 'manualUnlockRoutine', title: 'On Manual Unlock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/unlock-alt.png')
      input(name: 'manualLockRoutine', title: 'On Manual Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png')
      if (state.supportsKeypadData) {
        input(name: 'keypadLockRoutine', title: 'On keypad Lock', type: 'enum', options: actions, required: false, multiple: true)
      }
    }
  }
}

def refreshMode() {
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    state.codes["slot${slot}"].codeState = 'refresh'
  }
  state.refreshComplete = false
  makeRequest()
}

def setupLockData() {
  debugger('run lock data setup')
  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    // initialize data attributes for this lock.
    lockUser.initializeLockData()
  }
  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.initializeComplete = false
    state.installComplete = true
    state.refreshComplete = true
    state.supportsKeypadData = true
    state.pinLength = false
    if (lock.hasAttribute('pinLength')) {
      state.pinLength = lock.latestValue('pinLength')
    }
  }
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    if (state.codes["slot${slot}"] == null) {
      state.initializeComplete = false
      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      state.codes["slot${slot}"].attempts = 0
      state.codes["slot${slot}"].codeState = 'unknown'
    }
  }
  makeRequest()
}

def makeRequest() {
  def requestSlot = false
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    def codeState = state.codes["slot${slot}"]['codeState']
    if (codeState != 'known') {
      requestSlot = slot
    }
  }
  if (lock && requestSlot) {
    // there is an unknown code!
    debugger("request ${requestSlot}")
    lock.requestCode(requestSlot)
  } else {
    debugger('no request to make')
    state.refreshComplete = true
    state.initializeComplete = true
    setCodes()
  }
}

def updateCode(event) {
  def data = new JsonSlurper().parseText(event.data)
  def slot = event.value.toInteger()
  def code
  if (data.code.isNumber()) {
    code = data.code
  } else {
    // It's easier on logic if code is empty to be null
    code = null
  }

  def previousCode = state.codes["slot${slot}"]['code']

  state.codes["slot${slot}"]['code'] = code
  state.codes["slot${slot}"]['codeState'] = 'known'

  debugger("Recieved: s:${slot} c:${code}")

  // check logic to see if all codes are in known state
  if (!state.initializeComplete || !state.refreshComplete) {
    runIn(5, makeRequest)
  }
  if (previousCode != code) {
    // code changed, let's inform!
    codeInform(slot, code)
  }
}

def codeUsed(evt) {
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
    if (codeUsed.isNumber()) {
      userApp = findSlotUserApp(codeUsed)
    }
    if (data.usedCode == 'manual') {
      manualUse = true
    }
  } else {
    // this lock does not report
    // differance between manual or keypad
    state.supportsKeypadData = false
    manualUse = true
  }

  if (action == 'unlocked') {
    // door was unlocked
    if (userApp) {
      message = "${lock.label} was unlocked by ${userApp.userName}"
      userApp.incrementLockUsage(lock.id)
      if (!userApp.isNotBurned()) {
        message += ".  Now burning code."
      }
      if (userApp.userUnlockPhrase) {
        location.helloHome.execute(userApp.userUnlockPhrase)
      }
    } else if (manualUse) {
      // unlocked manually
      if (manualUnlockRoutine) {
        location.helloHome.execute(manualUnlockRoutine)
      }
      if (notifyMaunualUnlock) {
        message = "${lock.label} was unlocked manually"
        send(message)
      }
    }
  }
  if (action == 'locked') {
    // door was locked
    if (userApp) {
      message = "${lock.label} was locked by ${userApp.userName}"
      if (userApp.userLockPhrase) {
        location.helloHome.execute(userApp.userLockPhrase)
      }
    }
    if (data && data.usedCode == 0) {
      if (keypadLockRoutine) {
        location.helloHome.execute(keypadLockRoutine)
      }
      if (notifyKeypadLock) {
        message = "${lock.label} was locked by keypad"
        send(message)
      }
    }
    if (manualUse) {
      // locked manually
      if (manualLockRoutine) {
        location.helloHome.execute(manualLockRoutine)
      }
      if (notifyMaunualLock) {
        message = "${lock.label} was locked manually"
        send(message)
      }
    }
  }

  // decide if we should send a message per the userApp
  if (userApp && message) {
    debugger("Sending message: " + message)
    if (action == 'unlocked' && userApp.notifyAccess) {
      userApp.send(message)
    } else if (action == 'locked' && userApp.notifyLock) {
      userApp.send(message)
    }
  }
}

def setCodes() {
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}
  sortedCodes.each { data ->
    data = data.value
    def lockUser = findSlotUserApp(data.slot)
    if (lockUser) {
      if (lockUser.isActive(lock.id)) {
        // is active, should be set
        state.codes["slot${data.slot}"].correctValue = lockUser.userCode
      } else {
        // is inactive, should not be set
        state.codes["slot${data.slot}"].correctValue = null
      }
    } else if (overwriteMode) {
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
    if (data.code != data.correctValue) {
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
          userApp.send(message)
        }
      } else {
        // user should have access but the code is wrong!
      }
    } else {
      if (!code) {
        message = "${userApp.userName} no longer has access to ${lock.label}"
        if (userApp.notifyAccessEnd || parent.notifyAccessEnd) {
          userApp.send(message)
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


def send(message) {
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
  if (recipients) {
    sendNotificationToContacts(message, recipients)
  } else {
    if (notification) {
      sendPush(message)
    } else {
      sendNotificationEvent(message)
    }
    if (phone) {
      if ( phone.indexOf(";") > 1){
        def phones = phone.split(";")
        for ( def i = 0; i < phones.size(); i++) {
          sendSms(phones[i], message)
        }
      }
      else {
        sendSms(phone, message)
      }
    }
  }
}

def isCodeComplete() {
  return state.initializeComplete
}

def isRefreshComplete() {
  return state.refreshComplete
}

def codeData() {
  return state.codes
}

def slotData(slot) {
  state.codes["slot${slot}"]
}

def enableUser(slot) {
  state.codes["slot${slot}"].attempts = 0
  makeRequest()
}

def pinLength() {
  return state.pinLength
}

def debugger(message) {
  def doDebugger = parent.debuggerOn()
  if (doDebugger) {
    log.debug(message)
  }
}
