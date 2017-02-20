definition(
  name: 'Lock Manager2',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'Manage locks and users',
  category: 'Safety & Security',
  iconUrl: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lm.jpg',
  iconX2Url: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lm2x.jpg',
  iconX3Url: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lm3x.jpg'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page(name: 'mainPage', title: 'Installed', install: true, uninstall: true, submitOnChange: true)
  page(name: 'lockInfoPage')
  page(name: 'infoRefreshPage')
  page(name: 'notificationPage')
  page(name: "keypadPage")
}

def smartTitle() {
  def title = []
  if (getUserApps()) {
    title.push('Users')
  }
  if (getKeypadApps()) {
    title.push('Keypads')
  }
  return fancyString(title)
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'lockUsers', appName: "Lock User", namespace: "ethayer", title: "New User", multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-plus.png')
      app(name: 'keypads', appName: "Keypad", namespace: "ethayer", title: "New Keypad", multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-plus.png')
    }
    section('Locks') {
      if (locks) {
        def i = 0
        locks.each { lock->
          i++
          href(name: "toLockInfoPage${i}", page: "lockInfoPage", params: [id: lock.id], required: false, title: lock.displayName, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png' )
        }
      }
    }
    section('Global Settings') {
      // needs to run any time a lock is added
      initalizeLockData()
      href(name: "toKeypadPage", page: "keypadPage", title: "Keypad Settings (optional)")
      input 'locks', 'capability.lockCodes', title: 'Select Locks', required: true, multiple: true, submitOnChange: true
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
      input(name: "overwriteMode", title: "Overwrite?", type: "bool", required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      href(name: "toInfoRefreshPage", page: "infoRefreshPage", title: "Refresh Lock Data", description: 'Tap to refresh', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/refresh.png')
    }
  }
}

def infoRefreshPage() {
  dynamicPage(name:"infoRefreshPage", title:"Lock Info") {
    section() {
      doPoll()
      paragraph "Lock info refreshing soon."
      href(name: "toMainPage", page: "mainPage", title: "Back")
    }
  }
}

def lockInfoPage(params) {
  dynamicPage(name:"lockInfoPage", title:"Lock Info") {
    def lock = getLock(params)
    if (lock) {
      section("${lock.displayName}") {
        if (state."lock${lock.id}".codes != null) {
          def i = 0
          def setCode = ''
          def child
          def usage
          def para
          def image
          state."lock${lock.id}".codes.each { code->
            i++
            child = findAssignedChildApp(lock, i)
            setCode = state."lock${lock.id}".codes."slot${i}"
            para = "Slot ${i}\nCode: ${setCode}"
            if (child) {
              para = para + child.getLockUserInfo(lock)
              image = child.lockInfoPageImage(lock)
            } else {
              image = 'https://dl.dropboxusercontent.com/u/54190708/LockManager/times-circle-o.png'
            }
            paragraph para, image: image

          }
        } else {
          paragraph "No Lock data received yet.  Requires custom device driver.  Will be populated on next poll event."
          doPoll()
        }
      }
    } else {
      section() {
        paragraph "Error: Can't find lock!"
      }
    }
  }
}

def notificationPage() {
  dynamicPage(name: "notificationPage", title: "Global Notification Settings") {
    section {
      paragraph "These settings will apply to all users.  Settings on individual users will override these settings"

      input("recipients", "contact", title: "Send notifications to", submitOnChange: true, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/book.png')

      if (!recipients) {
        input(name: "phone", type: "text", title: "Text This Number", description: "Phone number", required: false, submitOnChange: true)
        paragraph "For multiple SMS recipients, separate phone numbers with a semicolon(;)"
        input(name: "notification", type: "bool", title: "Send A Push Notification", description: "Notification", required: false, submitOnChange: true)
      }

      if (phone != null || notification || sendevent) {
        input(name: "notifyAccess", title: "on User Entry", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/unlock-alt.png')
        input(name: "notifyLock", title: "on Lock", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png')
        input(name: "notifyAccessStart", title: "when granting access", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/check-circle-o.png')
        input(name: "notifyAccessEnd", title: "when revoking access", type: "bool", required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/times-circle-o.png')
      }
    }
    section("Only During These Times (optional)") {
      input(name: "notificationStartTime", type: "time", title: "Notify Starting At This Time", description: null, required: false)
      input(name: "notificationEndTime", type: "time", title: "Notify Ending At This Time", description: null, required: false)
    }
  }
}

def keypadPage() {
  dynamicPage(name: "keypadPage",title: "Keypad Settings (optional)") {
    section("Settings") {
      // TODO: put inputs here
      input(name: "keypad", title: "Keypad", type: "capability.lockCodes", multiple: true, required: false)
    }
    def hhPhrases = location.getHelloHome()?.getPhrases()*.label
    hhPhrases?.sort()
    section("Routines", hideable: true, hidden: true) {
      input(name: "armRoutine", title: "Arm/Away routine", type: "enum", options: hhPhrases, required: false)
      input(name: "disarmRoutine", title: "Disarm routine", type: "enum", options: hhPhrases, required: false)
      input(name: "stayRoutine", title: "Arm/Stay routine", type: "enum", options: hhPhrases, required: false)
      input(name: "nightRoutine", title: "Arm/Night routine", type: "enum", options: hhPhrases, required: false)
      input(name: "armDelay", title: "Arm Delay (in seconds)", type: "number", required: false)
      input(name: "notifyIncorrectPin", title: "Notify you when incorrect code is used?", type: "bool", required: false)
    }
  }
}

def fancyString(listOfStrings) {
  listOfStrings.removeAll([null])
  def fancify = { list ->
    return list.collect {
      def label = it
      if (list.size() > 1 && it == list[-1]) {
        label = "and ${label}"
      }
      label
    }.join(", ")
  }

  return fancify(listOfStrings)
}

def notificationPageDescription() {
  def parts = []
  def msg = ""
  if (settings.phone) {
    parts << "SMS to ${phone}"
  }
  if (settings.sendevent) {
    parts << "Event Notification"
  }
  if (settings.notification) {
    parts << "Push Notification"
  }
  msg += fancyString(parts)
  parts = []

  if (settings.notifyAccess) {
    parts << "on entry"
  }
  if (settings.notifyLock) {
    parts << "on lock"
  }
  if (settings.notifyAccessStart) {
    parts << "when granting access"
  }
  if (settings.notifyAccessEnd) {
    parts << "when revoking access"
  }
  if (settings.notificationStartTime) {
    parts << "starting at ${settings.notificationStartTime}"
  }
  if (settings.notificationEndTime) {
    parts << "ending at ${settings.notificationEndTime}"
  }
  if (parts.size()) {
    msg += ": "
    msg += fancyString(parts)
  }
  return msg
}

def installed() {
  log.debug "Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"
  unsubscribe()
  initialize()
}

def initialize() {
  def children = getChildApps()

  initalizeLockData()
  children.each { child ->
    if (child.userSlot) {
      child.initalizeLockData()
    }
  }

  setAccess()
  subscribe(locks, "codeReport", updateCode)
  subscribe(locks, "reportAllCodes", pollCodeReport, [filterEvents:false])
  log.debug "there are ${children.size()} lock users"
}

def initalizeLockData() {
  locks.each { lock->
    if (state."lock${lock.id}" == null) {
      state."lock${lock.id}" = [:]
    }
  }
}

def getLock(params) {
  def id = ''
  // Assign params to id.  Sometimes parameters are double nested.
  if (params.id) {
    id = params.id
  } else if (params.params){
    id = params.params.id
  } else if (state.lastLock) {
    id = state.lastLock
  }
  state.lastLock = id
  return locks.find{it.id == id}
}

def availableSlots(selectedSlot) {
  def options = []
  (1..30).each { slot->
    def children = getChildApps()
    def available = true
    children.each { child ->
      def userSlot = child.userSlot
      if (!selectedSlot) {
        selectedSlot = 0
      }
      if (!userSlot) {
        userSlot = 0
      }
      if (userSlot.toInteger() == slot && selectedSlot.toInteger() != slot) {
        available = false
      }
    }
    if (available) {
      options << ["${slot}": "Slot ${slot}"]
    }
  }
  return options
}

def pollCodeReport(evt) {
  def needPoll = false
  def codeData = new JsonSlurper().parseText(evt.data)
  def currentLock = locks.find{it.id == evt.deviceId}

  populateDiscovery(codeData, currentLock)

  log.debug 'checking children for errors'
  getUserApps.each { child ->
    child.pollCodeReport(evt)
    if (child.isInErrorLoop(evt.deviceId)) {
      log.debug 'child is in error loop'
      needPoll = true
    }
  }
  def unmangedCodesNotReady = false
  if (overwriteMode) {
    unmangedCodesNotReady = removeUnmanagedCodes(evt)
  }
  if (needPoll || unmangedCodesNotReady) {
    log.debug 'asking for poll!'
    runIn(20, doPoll)
  }
}

def removeUnmanagedCodes(evt) {
  def codeData = new JsonSlurper().parseText(evt.data)
  def lock = locks.find{it.id == evt.deviceId}
  def array = []
  def codes = [:]
  def codeSlots = 30
  if (codeData.codes) {
    codeSlots = codeData.codes
  }

  (1..codeSlots).each { slot ->
    def child = findAssignedChildApp(lock, slot)
    if (!child) {
      def currentCode = codeData."code${slot}"
      // there is no app associated
      if (currentCode) {
        // Code is set, We should be disabled.
        array << ["code${slot}", '']
      }
    }
  }
  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    //Lock has codes we don't want
    lock.updateCodes(json)
    return true
  } else {
    //Lock is clean
    return false
  }
}

def setAccess() {
  def userArray
  def json
  locks.each { lock ->
    userArray = []
    getUserApps().each { child ->
      if (child.userSlot) {
        if (child.isActive(lock.id)) {
          userArray << ["code${child.userSlot}", "${child.userCode}"]
        } else {
          userArray << ["code${child.userSlot}", ""]
        }
      }
    }
    json = new groovy.json.JsonBuilder(userArray).toString()
    log.debug json
    lock.updateCodes(json)
  }
  runIn(60, doPoll)
}

def doPoll() {
  locks.poll()
}

def updateCode(evt) {
  def codeNumber = evt.data.replaceAll("\\D+","")
  def codeSlot = evt.integerValue.toInteger()
  def lock = evt.device

  // set parent known code
  state."lock${lock.id}".codes[codeSlot] = codeNumber

  def childApp = findAssignedChildApp(lock, codeSlot)
  if (childApp) {
    childApp.setKnownCode(codeNumber, lock)
  }
}

def populateDiscovery(codeData, lock) {
  def codes = [:]
  def codeSlots = 30
  if (codeData.codes) {
    codeSlots = codeData.codes
  }
  (1..codeSlots).each { slot->
    def childApp = findAssignedChildApp(lock, slot)
    def knownCode = codeData."code${slot}"
    codes."slot${slot}" = knownCode
    if (childApp) {
      childApp.setKnownCode(knownCode, lock)
    }
  }
  state."lock${lock.id}".codes = codes
}

def findAssignedChildApp(lock, slot) {
  def childApp
  getUserApps().each { child ->
    if (child.userSlot?.toInteger() == slot) {
      childApp = child
    }
  }
  return childApp
}

def getUserApps() {
  def userApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.userSlot) {
      userApps.push(child)
    }
  }
  log.debug 'yes'
  log.debug userApps
  return userApps
}

def getKeypadApps() {
  def keypadApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.keypad) {
      keypadApps.push(child)
    }
  }
  return keypadApps
}

// KEYPAD /////////////////////////////////////////////////////////////////////

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

  def i = settings.maxUsers
  def message = " "
  while (i > 0) {
    log.debug "i =" + i
    def correctCode = settings."userCode${i}" as String

    if (codeEntered == correctCode) {

      log.debug "User Enabled: " + state."userState${i}".enabled

      if (state."userState${i}".enabled == true) {
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

        if(settings."burnCode${i}") {
          state."userState${i}".enabled = false
          message += ".  Now burning code."
        }

        log.debug "${message}"
        //log.debug "Initial Usage Count:" + state."userState${i}".usage
        state."userState${i}".usage = state."userState${i}".usage + 1
        //log.debug "Final Usage Count:" + state."userState${i}".usage
        send(message)
        i = 0
      } else if (state."userState${i}".enabled == false){
        log.debug "PIN Disabled"
        //Could also call acknowledgeArmRequest() with a parameter of 4 to report invalid code. Opportunity to simplify code?
        //keypad.sendInvalidKeycodeResponse()
      }
    }
    changedMode = 1
    i--
  }
  if (changedMode == 1 && i == 0) {
    def errorMsg = "Incorrect Code Entered: ${codeEntered}"
    if (notifyIncorrectPin) {
      log.debug "Incorrect PIN"
      send(errorMsg)
    }
    //Could also call acknowledgeArmRequest() with a parameter of 4 to report invalid code. Opportunity to simplify code?
    keypad.sendInvalidKeycodeResponse()
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
