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
  page name: 'notificationPage'
  page name: 'helloHomePage'
  page name: 'lockInfoPage'
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
  subscribe(lock, "lock", codeUsed)
  setupLockData()
}

def landingPage() {
  if (lock) {
    mainPage()
  } else {
    setupPage()
  }
}

def setupPage() {
  dynamicPage(name: "setupPage", title: "Setup Lock", nextPage: "mainPage", uninstall: true) {
    section("Choose devices for this lock") {
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      setupLockData()
      def actions = location.helloHome?.getPhrases()*.label
      if (lock) {
        href(name: 'toLockInfoPage', page: 'lockInfoPage', required: false, title: 'Lock Info', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png' )
      }
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
      href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/home.png')
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/home.png')
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label title: 'Label', defaultValue: "Lock: ${lock.label}", required: true, description: 'recommended to start with Lock:'
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
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

def lockInfoPage() {
  dynamicPage(name:"lockInfoPage", title:"Lock Info") {
    if (lock) {
      section("${lock.displayName}") {
        if (!state.initializeComplete) {
          paragraph 'App is learning codes.  They will appear here when received.'
        }
        if (state.codes) {
          def i = 0
          def setCode = ''
          def usage
          def para
          def image
          def sortedCodes = state.codes.sort{it.value.slot}
          sortedCodes.each { data ->
            data = data.value
            if (data.codeState != 'unknown') {
              def userApp = findSlotUserApp(data.slot)
              para = "Slot ${data.slot}\nCode: ${data.code}"
              if (userApp) {
                para = para + userApp.getLockUserInfo(lock)
                image = userApp.lockInfoPageImage(lock)
              } else {
                image = 'https://dl.dropboxusercontent.com/u/54190708/LockManager/times-circle-o.png'
              }
              paragraph para, image: image
            }
          }
        }
      }
    }
  }
}

def setupLockData() {
  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    // initialize data attributes for this lock.
    lockUser.initializeLockData()
  }
  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.initializeComplete = false
  }
  def codeSlots = 30
  (1..codeSlots).each { slot ->
    if (state.codes["slot${slot}"] == null) {
      state.initializeComplete = false

      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      state.codes["slot${slot}"].codeState = 'unknown'
    }
  }
  setupCodeValues()
}

def setupCodeValues() {
  state.supportsKeypadData = true
  if (!state.initializeComplete) {
    makeRequest()
  }
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
    lock.requestCode(requestSlot)
  } else {
    state.initializeComplete = true
    log.debug 'no request to make'
  }
}

def updateCode(event) {
  def data = new JsonSlurper().parseText(event.data)
  log.debug data
  def slot = event.value.toInteger()
  def code
  if (data.code.isNumber()) {
    code = data.code
  } else {
    code = ''
  }
  state.codes["slot${slot}"]['code'] = code
  state.codes["slot${slot}"]['codeState'] = 'known'

  log.debug state

  // check logic to see if all codes are in known state
  runIn(10, makeRequest)
  codeInform(slot, code)

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
      message = "${lock.label} was unlocked by ${userApp.label}"
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
      message = "${lock.label} was locked by ${userApp.label}"
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
    log.debug("Sending message: " + message)
    if (action == 'unlocked' && userApp.notifyAccess) {
      userApp.send(message)
    } else if (action == 'locked' && userApp.notifyLock) {
      userApp.send(message)
    }
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
        message = "${userApp.label} now has access to ${lock.label}"
        if (userApp.notifyAccessStart || parent.notifyAccessStart) {
          userApp.send(message)
        }
      } else {
        // user should have access but the code is wrong!
      }
    } else {
      if (!code) {
        message = "${userApp.label} no longer has access to ${lock.label}"
        if (userApp.notifyAccessEnd || parent.notifyAccessEnd) {
          userApp.send(message)
        }
      } else {
        // there's a code set for some reason
        // it should be deleted!
      }
    }
    log.debug message
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
