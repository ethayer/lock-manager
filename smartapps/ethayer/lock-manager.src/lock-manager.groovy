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
  page(name: 'infoRefreshPage')
  page(name: 'notificationPage')
  page(name: "keypadPage")
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'locks', appName: 'Lock', namespace: 'ethayer', title: 'New Lock', multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-plus.png')
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
      href(name: 'toKeypadPage', page: 'keypadPage', title: 'Keypad Routines (optional)', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/keypad.png')
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
      input(name: 'overwriteMode', title: 'Overwrite?', type: 'bool', required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      href(name: 'toInfoRefreshPage', page: 'infoRefreshPage', title: 'Refresh Lock Data', description: 'Tap to refresh', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/refresh.png')
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
  log.debug "there are ${children.size()} lock users"
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

def findAssignedChildApp(lock, slot) {
  def childApp
  def userApps = getUserApps()
  userApps.each { child ->
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

def getLockApps() {
  def lockApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.lock) {
      lockApps.push(child)
    }
  }
  return lockApps
}
