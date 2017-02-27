definition(
  name: 'Lock Manager',
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
      app(name: 'lockUsers', appName: 'Lock User', namespace: 'ethayer', title: 'New User', multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-plus.png')
      app(name: 'keypads', appName: 'Keypad', namespace: 'ethayer', title: 'New Keypad', multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-plus.png')
    }
    section('Locks') {
      if (locks) {
        def i = 0
        locks.each { lock->
          i++
          href(name: "toLockInfoPage${i}", page: 'lockInfoPage', params: [id: lock.id], required: false, title: lock.displayName, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png' )
        }
      }
    }
    section('Global Settings') {
      // needs to run any time a lock is added
      initalizeLockData()
      href(name: 'toKeypadPage', page: 'keypadPage', title: 'Keypad Routines (optional)', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/keypad.png')
      input 'locks', 'capability.lock', title: 'Select Locks', multiple: true, submitOnChange: true
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
      input(name: 'overwriteMode', title: 'Overwrite?', type: 'bool', required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      href(name: 'toInfoRefreshPage', page: 'infoRefreshPage', title: 'Refresh Lock Data', description: 'Tap to refresh', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/refresh.png')
    }
  }
}

def infoRefreshPage() {
  dynamicPage(name:'infoRefreshPage', title:'Lock Info') {
    section() {
      doPoll()
      paragraph 'Lock info refreshing soon.'
      href(name: 'toMainPage', page: 'mainPage', title: 'Back')
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
          paragraph 'No Lock data received yet.  Requires custom device driver.  Will be populated on next poll event.'
          doPoll()
        }
      }
    } else {
      section() {
        paragraph 'Error: Can\'t find lock!'
      }
    }
  }
}

def notificationPage() {
  dynamicPage(name: 'notificationPage', title: 'Global Notification Settings') {
    section {
      paragraph 'These settings will apply to all users.  Settings on individual users will override these settings'

      input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/book.png')

      if (!recipients) {
        input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
        paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
        input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
      }

      if (phone != null || notification || sendevent || recipients) {
        input(name: 'notifyAccess', title: 'on User Entry', type: 'bool', required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/unlock-alt.png')
        input(name: 'notifyLock', title: 'on Lock', type: 'bool', required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png')
        input(name: 'notifyAccessStart', title: 'when granting access', type: 'bool', required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/check-circle-o.png')
        input(name: 'notifyAccessEnd', title: 'when revoking access', type: 'bool', required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/times-circle-o.png')
      }
    }
    section('Only During These Times (optional)') {
      input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
      input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
    }
  }
}

def keypadPage() {
  dynamicPage(name: 'keypadPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section("Settings") {
      paragraph 'settings here are for all users. When any user enters their passcode, run these routines'
      input(name: 'armRoutine', title: 'Arm/Away routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'disarmRoutine', title: 'Disarm routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'stayRoutine', title: 'Arm/Stay routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'nightRoutine', title: 'Arm/Night routine', type: 'enum', options: actions, required: false, multiple: true)
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
    parts << 'Event Notification'
  }
  if (settings.notification) {
    parts << 'Push Notification'
  }
  msg += fancyString(parts)
  parts = []

  if (settings.notifyAccess) {
    parts << 'on entry'
  }
  if (settings.notifyLock) {
    parts << 'on lock'
  }
  if (settings.notifyAccessStart) {
    parts << 'when granting access'
  }
  if (settings.notifyAccessEnd) {
    parts << 'when revoking access'
  }
  if (settings.notificationStartTime) {
    parts << "starting at ${settings.notificationStartTime}"
  }
  if (settings.notificationEndTime) {
    parts << "ending at ${settings.notificationEndTime}"
  }
  if (parts.size()) {
    msg += ': '
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
  subscribe(locks, 'codeReport', updateCode)
  subscribe(locks, 'reportAllCodes', pollCodeReport, [filterEvents:false])
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

def keypadMatchingUser(usedCode){
  def correctUser = false
  def userApps = getUserApps()
  userApps.each { userApp ->
    def code
    log.debug userApp.userCode
    if (userApp.isActiveKeypad(1234)) {
      code = userApp.userCode.take(4)
      log.debug "code: ${code} used: ${usedCode}"
      if (code.toInteger() == usedCode.toInteger()) {
        correctUser = userApp
      }
    }
  }
  return correctUser
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
