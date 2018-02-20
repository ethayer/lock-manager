definition(
  name: 'New Lock Manager',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  parent: parent ? "ethayer: New Lock Manager" : null,
  description: 'Manage locks and users',
  category: 'Safety & Security',
  singleInstance: true,
  iconUrl: 'https://images.lockmanager.io/app/v1/images/lm.jpg',
  iconX2Url: 'https://images.lockmanager.io/app/v1/images/lm2x.jpg',
  iconX3Url: 'https://images.lockmanager.io/app/v1/images/lm3x.jpg'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.util.regex.*
include 'asynchttp_v1'

preferences {
  // Manager ===
  page name: 'appPageWizard'
  page name: 'mainPage', title: 'Installed', install: true, uninstall: true, submitOnChange: true
  page name: 'createPage'
  page name: 'infoRefreshPage'
  page name: 'notificationPage'
  page name: 'helloHomePage'
  page name: 'lockInfoPage'
  page name: 'keypadPage'
  page name: 'askAlexaPage'

  // Lock ====
  page name: 'lockLandingPage'
  page name: 'lockSetupPage'
  page name: 'lockMainPage'
  page name: 'lockErrorPage'
  page name: 'lockNotificationPage'
  page name: 'lockHelloHomePage'
  page name: 'lockInfoRefreshPage'
  page name: 'lockAskAlexaPage'

  // User ====
  page name: 'userLandingPage'
  page name: 'userSetupPage'
  page name: 'userMainPage'
  page name: 'userLockPage', title: 'Manage Lock', install: false, uninstall: false
  page name: 'schedulingPage', title: 'Schedule User', install: false, uninstall: false
  page name: 'calendarPage', title: 'Calendar', install: false, uninstall: false
  page name: 'userNotificationPage'
  page name: 'reEnableUserLockPage'
  page name: 'lockResetPage'
  page name: 'userKeypadPage'
  page name: 'userAskAlexaPage'

  // Keypad ====
  page name: 'keypadLandingPage'
  page name: 'keypadSetupPage'
  page name: 'keypadMainPage'
  page name: 'keypadErrorPage'

  // API ====
  page name: 'apiSetupPage'
}

def appPageWizard(params) {
  if (params.type) {
    // inital set app type
    setAppType(params.type)
  }
  // find the correct landing page
  switch (state.appType) {
    case 'lock':
      lockLandingPage()
      break
    case 'user':
      userLandingPage()
      break
    case 'keypad':
      keypadLandingPage()
      break
    case 'api':
      apiSetupPage()
      break
    default:
      mainPage()
      break
  }
}

def installed() {

  // find the correct installer
  switch (state.appType) {
    case 'lock':
      lockInstalled()
      break
    case 'user':
      userInstalled()
      break
    case 'keypad':
      installedKeypad()
      break
    case 'api':
      installedApi()
      break
    default:
      debugger("Installed with settings: ${settings}")
      installedMain()
      break
  }
}

def updated() {
  // find the correct updater
  switch (state.appType) {
    case 'lock':
      lockUpdated()
      break
    case 'user':
      userUpdated()
      break
    case 'keypad':
      updatedKeypad()
      break
    case 'api':
      updatedApi()
      break
    default:
      debugger("Installed with settings: ${settings}")
      updatedMain()
      break
  }
}

def uninstalled() {
  switch (state.appType) {
    case 'lock':
      break
    case 'user':
      userUninstalled()
      break
    case 'keypad':
      break
    case 'api':
      break
    default:
      break
  }
}


def installedMain() {
  initializeMain()
}

def updatedMain() {
  log.debug "Main Updated with settings: ${settings}"
  unsubscribe()
  initializeMain()
}

def initializeMain() {
  def children = getLockApps()
  log.debug "there are ${children.size()} locks"

  subscribe(theSwitches, "switch.on", switchOnHandler)
  subscribe(theSwitches, "switch.off", switchOffHandler)
}


def mainPage() {
  dynamicPage(name: 'mainPage', title: 'Lock Manager', install: true, uninstall: true, submitOnChange: true) {
    section('Locks') {
      def lockApps = getLockApps()
      lockApps = lockApps.sort{ it.lock.id }
      if (lockApps) {
        def i = 0
        lockApps.each { lockApp ->
          i++
          href(name: "toLockInfoPage${i}", page: 'lockInfoPage', params: [id: lockApp.lock.id], required: false, title: lockApp.label, image: 'https://images.lockmanager.io/app/v1/images/lock.png' )
        }
      }
    }
    section('Global Settings') {
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')

      def actions = location.helloHome?.getPhrases()*.label
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings', image: 'https://images.lockmanager.io/app/v1/images/home.png')
      }

      def keypadApps = getKeypadApps()
      if (keypadApps) {
        href(name: 'toKeypadPage', page: 'keypadPage', title: 'Keypad Routines (optional)', image: 'https://images.lockmanager.io/app/v1/images/keypad.png')
      }
    }

    section('Install') {
      paragraph 'Create integrations'
      href(name: "toCreatePage", title: 'Create Integration', page: 'createPage', required: false )
    }

    section('API') {
      href(name: 'toApiPage', page: 'apiSetupPage', title: 'API Options', image: 'https://images.lockmanager.io/app/v1/images/keypad.png')
    }

    section('Advanced', hideable: true, hidden: true) {
      input(name: 'overwriteMode', title: 'Overwrite?', type: 'bool', required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      input(name: 'enableDebug', title: 'Enable IDE debug messages?', type: 'bool', required: true, defaultValue: false, description: 'Show activity from Lock Manger in logs for debugging.')
      label(title: 'Label this SmartApp', required: false, defaultValue: 'Lock Manager')
      paragraph 'Lock Manager © 2017 v1.4'
    }
  }
}

def createPage() {
  dynamicPage(name: 'createPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      input name: "appType", type: "enum", title: "Type", options: ['Lock', 'User', 'Keypad', 'Api'], description: "Select the integration you need", submitOnChange: true
      if (settings.appType) {
        def appTypeString = settings.appType
        def miniTypeString = appTypeString.toLowerCase()
        app(name: 'newChild', params: [type: miniTypeString], appName: 'New Lock Manager', namespace: 'ethayer', title: "New ${appTypeString}", multiple: true, image: "https://images.lockmanager.io/app/v1/images/new-${miniTypeString}.png")
      }
    }
  }
}

def setAppType(appType) {
  state.appType = appType
}

def lockInfoPage(params) {
  dynamicPage(name:"lockInfoPage", title:"Lock Info") {
    def lockApp = getLockAppByIndex(params)
    if (lockApp) {
      section("${lockApp.label}") {
        def complete = lockApp.isCodeComplete()
        if (!complete) {
          paragraph 'App is learning codes.  They will appear here when received.\n Lock may require special DTH to work properly'
          lockApp.lock.poll()
        }
        def codeData = lockApp.codeData()
        if (codeData) {
          def setCode = ''
          def usage
          def para
          def image
          def sortedCodes = codeData.sort{it.value.slot}
          sortedCodes.each { data ->
            data = data.value
            if (data.codeState != 'unknown') {
              def userApp = lockApp.findSlotUserApp(data.slot)
              para = "Slot ${data.slot}"
              if (data.code) {
                para = para + "\nCode: ${data.code}"
              }
              if (userApp) {
                para = para + userApp.getLockUserInfo(lockApp.lock)
                image = userApp.lockInfoPageImage(lockApp.lock)
              } else {
                image = 'https://images.lockmanager.io/app/v1/images/times-circle-o.png'
              }
              if (data.codeState == 'refresh') {
                para = para +'\nPending refresh...'
              }
              if (data.control) {
                para = para +"\nControl: ${data.control}"
              }
              paragraph para, image: image
            }
          }
        }
      }

      section('Lock Settings') {
        def pinLength = lockApp.pinLength()
        def lockCodeSlots = lockApp.lockCodeSlots()
        if (pinLength) {
          paragraph "Required Length: ${pinLength}"
        }
        paragraph "Slot Count: ${lockCodeSlots}"
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

      input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/book.png')
      href(name: 'toAskAlexaPage', title: 'Ask Alexa', page: 'askAlexaPage', image: 'https://images.lockmanager.io/app/v1/images/Alexa.png')
      if (!recipients) {
        input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
        paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
        input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
      }

      if (phone != null || notification || recipients) {
        input(name: 'notifyAccess', title: 'on User Entry', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
        input(name: 'notifyLock', title: 'on Lock', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/lock.png')
        input(name: 'notifyAccessStart', title: 'when granting access', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/check-circle-o.png')
        input(name: 'notifyAccessEnd', title: 'when revoking access', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/times-circle-o.png')
      }
    }
    section('Only During These Times (optional)') {
      input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
      input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
    }
  }
}

def helloHomePage() {
  dynamicPage(name: 'helloHomePage', title: 'Global Hello Home Settings (optional)') {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section('Hello Home Phrases') {
      input(name: 'manualUnlockRoutine', title: 'On Manual Unlock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
      input(name: 'manualLockRoutine', title: 'On Manual Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/lock.png')

      input(name: 'codeUnlockRoutine', title: 'On Code Unlock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png' )

      paragraph 'Supported on some locks:'
      input(name: 'codeLockRoutine', title: 'On Code Lock', type: 'enum', options: actions, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/lock.png')

      paragraph 'These restrictions apply to all the above:'
      input "userNoRunPresence", "capability.presenceSensor", title: "DO NOT run Actions if any of these are present:", multiple: true, required: false
      input "userDoRunPresence", "capability.presenceSensor", title: "ONLY run Actions if any of these are present:", multiple: true, required: false
    }
  }
}

def askAlexaPage() {
  dynamicPage(name: 'askAlexaPage', title: 'Ask Alexa Message Settings') {
    section('Que Messages with the Ask Alexa app') {
      paragraph 'These settings apply to all users.  These settings are overridable on the user level'
      input(name: 'alexaAccess', title: 'on User Entry', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/unlock-alt.png')
      input(name: 'alexaLock', title: 'on Lock', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/lock.png')
      input(name: 'alexaAccessStart', title: 'when granting access', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/check-circle-o.png')
      input(name: 'alexaAccessEnd', title: 'when revoking access', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/times-circle-o.png')
    }
    section('Only During These Times (optional)') {
      input(name: 'alexaStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
      input(name: 'alexaEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
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
  if (settings.recipients) {
    parts << 'Sent to Address Book'
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

def getLockAppById(id) {
  def lockApp = false
  def lockApps = getLockApps()
  if (lockApps) {
    def i = 0
    lockApps.each { app ->
      if (app.lock.id == id) {
        lockApp = app
      }
    }
  }
  return lockApp
}

def getLockAppByIndex(params) {
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

  def lockApp = false
  def lockApps = getLockApps()
  if (lockApps) {
    def i = 0
    lockApps.each { app ->
      if (app.lock.id == state.lastLock) {
        lockApp = app
      }
    }
  }

  return lockApp
}

def availableSlots(selectedSlot) {
  def options = []
  (1..30).each { slot->
    def children = getLockApps()
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
    if (userApp.isActiveKeypad()) {
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
  def childApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.theAppType() == 'user') {
      childApps.push(child)
    }
  }
  return childApps
}

def getKeypadApps() {
  def childApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.theAppType() == 'keypad') {
      childApps.push(child)
    }
  }
  return childApps
}

def getLockApps() {
  def childApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.theAppType() == 'lock') {
      childApps.push(child)
    }
  }
  return childApps
}

def setAccess() {
  def lockApps = getLockApps()
  lockApps.each { lockApp ->
    lockApp.makeRequest()
  }
}

def anyoneHome(sensors) {
  def result = false
  if(sensors.findAll { it?.currentPresence == "present" }) {
    result = true
  }
  result
}

def apiApp() {
  def app = false
  def children = getChildApps()
  children.each { child ->
    if (child.enableAPI) {
      app = child
    }
  }
  return app
}

def executeHelloPresenceCheck(routines) {
  if (userNoRunPresence && userDoRunPresence == null) {
    if (!anyoneHome(userNoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else if (userDoRunPresence && userNoRunPresence == null) {
    if (anyoneHome(userDoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else if (userDoRunPresence && userNoRunPresence) {
    if (anyoneHome(userDoRunPresence) && !anyoneHome(userNoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else {
    location.helloHome.execute(routines)
  }
}

def debuggerOn() {
  // needed for child apps
  return enableDebug
}

def theAppType() {
  if (parent) {
    return state.appType
  } else {
    return 'main'
  }
}

def debugger(message) {
  if (parent) {
    def doDebugger = parent.debuggerOn()
    if (doDebugger) {
      log.debug(message)
    }
  } else {
    def doDebugger = debuggerOn()
    if (enableDebug) {
      return log.debug(message)
    }
  }
}
