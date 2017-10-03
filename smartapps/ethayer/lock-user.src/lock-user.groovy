definition (
  name: 'Lock User',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage users. This is a child app.',
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
  page name: 'lockPage', title: 'Manage Lock', install: false, uninstall: false
  page name: 'schedulingPage', title: 'Schedule User', install: false, uninstall: false
  page name: 'calendarPage', title: 'Calendar', install: false, uninstall: false
  page name: 'notificationPage'
  page name: 'reEnableUserLockPage'
  page name: 'lockResetPage'
  page name: 'keypadPage'
  page name: 'askAlexaPage'
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

  // setup data
  initializeLockData()
  initializeLocks()
  initializeCodeState()

  // set listeners
  subscribe(location, locationHandler)
  subscribeToSchedule()
}

def uninstalled() {
  unschedule()

  // prompt locks to delete this user
  initializeLocks()
}

def subscribeToSchedule() {
  if (startTime) {
    // sechedule time of start!
    log.debug 'scheduling time start'
    schedule(startTime, 'scheduledStartTime')
  }
  if (endTime) {
    // sechedule time of end!
    log.debug 'scheduling time end'
    schedule(endTime, 'scheduledEndTime')
  }
  if (startDateTime()) {
    // schedule calendar start!
    log.debug 'scheduling calendar start'
    runOnce(startDateTime().format(smartThingsDateFormat(), timeZone()), 'calendarStart')
  }
  if (endDateTime()) {
    // schedule calendar end!
    log.debug 'scheduling calendar end'
    runOnce(endDateTime().format(smartThingsDateFormat(), timeZone()), 'calendarEnd')
  }
  if (airbnbEnabled) {
    doCalenderCheck()
    // schedule airbnb code setter
    runEvery15Minutes('doCalenderCheck')
  }
}

def scheduledStartTime() {
  parent.setAccess()
}
def scheduledEndTime() {
  parent.setAccess()
}
def calendarStart() {
  parent.setAccess()
  if (calStartPhrase) {
    location.helloHome.execute(calStartPhrase)
  }
}
def calendarEnd() {
  parent.setAccess()
  if (calEndPhrase) {
    location.helloHome.execute(calEndPhrase)
  }
}

def initializeCodeState() {
  state.userCode = settings.userCode
}

def initializeLockData() {
  debugger('Initialize lock data for user.')
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    def lockId = lockApp.lock.id
    if (state."lock${lockId}" == null) {
      state."lock${lockId}" = [:]
      state."lock${lockId}".enabled = true
      state."lock${lockId}".usage = 0
    }
  }
}

def initializeLocks() {
  debugger('User asking for lock init')
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    lockApp.queSetupLockData()
  }
}

def incrementLockUsage(lockId) {
  // this is called by a lock app when this user
  // used their code to lock the door
  state."lock${lockId}".usage = state."lock${lockId}".usage + 1
}

def lockReset(lockId) {
  state."lock${lockId}".enabled = true
  state."lock${lockId}".disabledReason = ''
  def lockApp = getLockApp(lockId)
  lockApp.enableUser(userSlot)
}

def landingPage() {
  if (userName) {
    mainPage()
  } else {
    setupPage()
  }
}

def getUserCode() {
  return state.userCode
}

def setupPage() {
  dynamicPage(name: 'setupPage', title: 'Setup Lock', nextPage: 'mainPage', uninstall: true) {
    section('Choose devices for this lock') {
      input(name: 'userName', title: 'Name for User', required: true, image: 'https://images.lockmanager.io/app/v1/images/user.png')
      input(name: 'userCode', type: 'text', title: userCodeInputTitle(), required: false, defaultValue: state.'userCode', refreshAfterSelection: true)
      input(name: 'userSlot', type: 'enum', options: parent.availableSlots(settings.userSlot), title: 'Select slot', required: true, refreshAfterSelection: true )
    }
  }
}

def mainPage() {
  //reset errors on each load
  dynamicPage(name: 'mainPage', title: '', install: true, uninstall: true) {
    section('User Settings') {
      def usage = getAllLocksUsage()
      def text
      if (isActive()) {
        text = 'active'
      } else {
        text = 'inactive'
      }
      paragraph "${text}/${usage}"
      input(name: 'userCode', type: 'text', title: userCodeInputTitle(), required: false, defaultValue: state.'userCode', refreshAfterSelection: true)
      input(name: 'userEnabled', type: 'bool', title: "User Enabled?", required: false, defaultValue: true, refreshAfterSelection: true)
    }
    section('Additional Settings') {
      def actions = location.helloHome?.getPhrases()*.label
      if (actions) {
        actions.sort()
        input name: 'userUnlockPhrase', type: 'enum', title: 'Hello Home Phrase on unlock', multiple: true, required: false, options: actions, refreshAfterSelection: true, image: 'https://images.lockmanager.io/app/v1/images/home.png'
        input name: 'userLockPhrase', type: 'enum', title: 'Hello Home Phrase on lock', description: 'Available on select locks only', multiple: true, required: false, options: actions, refreshAfterSelection: true, image: 'https://images.lockmanager.io/app/v1/images/home.png'

        input "userNoRunPresence", "capability.presenceSensor", title: "DO NOT run Actions if any of these are present:", multiple: true, required: false
        input "userDoRunPresence", "capability.presenceSensor", title: "ONLY run Actions if any of these are present:", multiple: true, required: false
      }
      input(name: 'burnAfterInt', title: 'How many uses before burn?', type: 'number', required: false, description: 'Blank or zero is infinite', image: 'https://images.lockmanager.io/app/v1/images/fire.png')
      href(name: 'toSchedulingPage', page: 'schedulingPage', title: 'Schedule (optional)', description: schedulingHrefDescription(), state: schedulingHrefDescription() ? 'complete' : '', image: 'https://images.lockmanager.io/app/v1/images/calendar.png')
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')
      href(name: 'toKeypadPage', page: 'keypadPage', title: 'Keypad Routines (optional)', image: 'https://images.lockmanager.io/app/v1/images/keypad.png')
    }
    section('Locks') {
      initializeLockData()
      def lockApps = parent.getLockApps()

      lockApps.each { app ->
        href(name: "toLockPage${app.lock.id}", page: 'lockPage', params: [id: app.lock.id], description: lockPageDescription(app.lock.id), required: false, title: app.lock.label, image: lockPageImage(app.lock) )
      }
    }
    section('Airbnb', hideable: true, hidden: true) {
      input(name: 'airbnbEnabled', type: 'bool', title: 'Enable Airbnb Automation?', required: false, defaultValue: false, refreshAfterSelection: true)
      input(name: 'ical', type: 'text', title: 'iCal Link', required: false, refreshAfterSelection: true)
      input(name: 'checkoutTime', type: 'time', title: 'Checkout time (when to change codes)', required: false, refreshAfterSelection: true)
      input(name: 'checkinNotify', type: 'bool', title: 'Notify on Checkin', description: 'Send one notification the first time a guest uses their code (Requires Push notifications "on Entry" to be enabled)', required: false, defaultValue: false, refreshAfterSelection: true)
    }
    section('Setup', hideable: true, hidden: true) {
      label(title: "Name for App", defaultValue: 'User: ' + userName, required: true, image: 'https://images.lockmanager.io/app/v1/images/user.png')
      input name: 'userName', title: "Name for user", required: true, image: 'https://images.lockmanager.io/app/v1/images/user.png'
      input(name: "userSlot", type: "enum", options: parent.availableSlots(settings.userSlot), title: "Select slot", required: true, refreshAfterSelection: true )
      paragraph 'Lock Manager © 2017 v1.4'
    }
  }
}

def userCodeInputTitle() {
  def title = 'Code 4-8 digits'
  def pinLength
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    pinLength = lockApp.pinLength()
    if (pinLength) {
      title = "Code (Must be ${lockApp.lock.latestValue('pinLength')} digits)"
    }
  }
  return title
}

def lockPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'https://images.lockmanager.io/app/v1/images/ban.png'
  } else {
    return 'https://images.lockmanager.io/app/v1/images/lock.png'
  }
}

def lockInfoPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'https://images.lockmanager.io/app/v1/images/user-times.png'
  } else {
    return 'https://images.lockmanager.io/app/v1/images/user.png'
  }
}

def lockPage(params) {
  dynamicPage(name:"lockPage", title:"Lock Settings") {
    debugger('current params: ' + params)
    def lock = getLock(params)
    def lockApp = getLockApp(lock.id)
    log.debug lockApp
    def slotData = lockApp.slotData(userSlot)

    def usage = state."lock${lock.id}".usage

    debugger('found lock id?: ' + lock?.id)

    if (!state."lock${lock.id}".enabled) {
      section {
        paragraph "WARNING:\n\nThis user has been disabled.\nReason: ${state."lock${lock.id}".disabledReason}", image: 'https://images.lockmanager.io/app/v1/images/ban.png'
        href(name: 'toReEnableUserLockPage', page: 'reEnableUserLockPage', title: 'Reset User', description: 'Retry setting this user.',  params: [id: lock.id], image: 'https://images.lockmanager.io/app/v1/images/refresh.png' )
      }
    }
    section("${deviceLabel(lock)} settings for ${app.label}") {
      if (slotData.code) {
        paragraph "Lock is currently set to ${slotData.code}"
      }
      paragraph "User unlock count: ${usage}"
      if(slotData.attempts > 0) {
        paragraph "Lock set failed try ${slotData.attempts}/10"
      }
      input(name: "lockDisabled${lock.id}", type: 'bool', title: 'Disable lock for this user?', required: false, defaultValue: settings."lockDisabled${lock.id}", refreshAfterSelection: true, image: 'https://images.lockmanager.io/app/v1/images/ban.png' )
      href(name: 'toLockResetPage', page: 'lockResetPage', title: 'Reset Lock', description: 'Reset lock data for this user.',  params: [id: lock.id], image: 'https://images.lockmanager.io/app/v1/images/refresh.png' )
    }
  }
}

def keypadPage() {
  dynamicPage(name: 'keypadPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section("Settings") {
      paragraph 'settings here are for this user only. When this user enters their passcode, run these routines'
      input(name: 'armRoutine', title: 'Arm/Away routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'disarmRoutine', title: 'Disarm routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'stayRoutine', title: 'Arm/Stay routine', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'nightRoutine', title: 'Arm/Night routine', type: 'enum', options: actions, required: false, multiple: true)
    }
  }
}

def lockPageDescription(lock_id) {
  def usage = state."lock${lock_id}".usage
  def description = "Entries: ${usage} "
  if (!state."lock${lock_id}".enabled) {
    description += '// ERROR//DISABLED'
  }
  if (settings."lockDisabled${lock_id}") {
    description += ' DISABLED'
  }
  description
}

def reEnableUserLockPage(params) {
  // do reset
  def lock = getLock(params)
  lockReset(lock.id)

  dynamicPage(name:'reEnableUserLockPage', title:'User re-enabled') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'mainPage')
    }
  }
}

def lockResetPage(params) {
  // do reset
  def lock = getLock(params)

  state."lock${lock.id}".usage = 0
  lockReset(lock.id)

  dynamicPage(name:'lockResetPage', title:'Lock reset') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'mainPage')
    }
  }
}

def schedulingPage() {
  dynamicPage(name: 'schedulingPage', title: 'Rules For Access Scheduling') {

    section {
      href(name: 'toCalendarPage', title: 'Calendar', page: 'calendarPage', description: calendarHrefDescription(), state: calendarHrefDescription() ? 'complete' : '')
    }

    section {
      input(name: 'days', type: 'enum', title: 'Allow User Access On These Days', description: 'Every day', required: false, multiple: true, options: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'], submitOnChange: true)
    }
    section {
      input(name: 'modeStart', title: 'Allow Access only when in this mode', type: 'mode', required: false, mutliple: false, submitOnChange: true)
    }
    section {
      input(name: 'startTime', type: 'time', title: 'Start Time', description: null, required: false)
      input(name: 'endTime', type: 'time', title: 'End Time', description: null, required: false)
    }
  }
}

def calendarPage() {
  dynamicPage(name: "calendarPage", title: "Calendar Access") {
    section() {
      paragraph "Enter each field carefully."
    }
    def actions = location.helloHome?.getPhrases()*.label
    section("Start Date") {
      input name: "startDay", type: "number", title: "Day", required: false
      input name: "startMonth", type: "number", title: "Month", required: false
      input name: "startYear", type: "number", description: "Format(yyyy)", title: "Year", required: false
      input name: "calStartTime", type: "time", title: "Start Time", description: null, required: false
      if (actions) {
        actions.sort()
        input name: "calStartPhrase", type: "enum", title: "Hello Home Phrase", multiple: true, required: false, options: actions, refreshAfterSelection: true
      }
    }
    section("End Date") {
      input name: "endDay", type: "number", title: "Day", required: false
      input name: "endMonth", type: "number", title: "Month", required: false
      input name: "endYear", type: "number", description: "Format(yyyy)", title: "Year", required: false
      input name: "calEndTime", type: "time", title: "End Time", description: null, required: false
      if (actions) {
        actions.sort()
        input name: "calEndPhrase", type: "enum", title: "Hello Home Phrase", multiple: true, required: false, options: actions, refreshAfterSelection: true
      }
    }
  }
}

def notificationPage() {
  dynamicPage(name: 'notificationPage', title: 'Notification Settings') {

    section {
      if (phone == null && !notification && !recipients) {
        input(name: 'muteUser', title: 'Mute this user?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
      }
      if (!muteUser) {
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
    }
    if (!muteUser) {
      section('Only During These Times (optional)') {
        input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
        input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
      }
    }
  }
}

def askAlexaPage() {
  dynamicPage(name: 'askAlexaPage', title: 'Ask Alexa Message Settings') {
    section('Que Messages with the Ask Alexa app') {
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

def timeZone() {
  def zone
  if(location.timeZone) {
    zone = location.timeZone
  } else {
    zone = TimeZone.getDefault()
  }
  return zone
}

public smartThingsDateFormat() { "yyyy-MM-dd'T'HH:mm:ss.SSSZ" }

public humanReadableStartDate() {
  new Date().parse(smartThingsDateFormat(), startTime).format('h:mm a', timeZone(startTime))
}
public humanReadableEndDate() {
  new Date().parse(smartThingsDateFormat(), endTime).format('h:mm a', timeZone(endTime))
}

def readableDateTime(date) {
  new Date().parse(smartThingsDateFormat(), date.format(smartThingsDateFormat(), timeZone())).format("EEE, MMM d yyyy 'at' h:mma", timeZone())
}

def getAllLocksUsage() {
  def usage = 0
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    if (state."lock${lockApp.lock.id}"?.usage) {
      usage = usage + state."lock${lockApp.lock.id}".usage
    }
  }
  return usage
}

def resetAllLocksUsage() {
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    if (state."lock${lockApp.lock.id}"?.usage) {
      state."lock${lockApp.lock.id}"?.usage = 0
    }
  }
}

def calendarHrefDescription() {
  def dateStart = startDateTime()
  def dateEnd = endDateTime()
  if (dateEnd && dateStart) {
    def startReadableTime = readableDateTime(dateStart)
    def endReadableTime = readableDateTime(dateEnd)
    return "Accessible from ${startReadableTime} until ${endReadableTime}"
  } else if (!dateEnd && dateStart) {
    def startReadableTime = readableDateTime(dateStart)
    return "Accessible on ${startReadableTime}"
  } else if (dateEnd && !dateStart){
    def endReadableTime = readableDateTime(dateEnd)
    return "Accessible until ${endReadableTime}"
  }
}

def notificationPageDescription() {
  def parts = []
  def msg = ''
  if (settings.phone) {
    parts << "SMS to ${phone}"
  }
  if (settings.notification) {
    parts << 'Push Notification'
  }
  if (settings.recipients) {
    parts << 'Sent to Address Book'
  }
  if (parts.size()) {
    msg += fancyString(parts)
  }
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
  if (muteUser) {
    msg = 'User Muted'
  }
  return msg
}

def deviceLabel(device) {
  return device.label ?: device.name
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
  if (listOfStrings) {
    return fancify(listOfStrings)
  }
}


def schedulingHrefDescription() {
  def descriptionParts = []
  if (startDateTime() || endDateTime()) {
    descriptionParts << calendarHrefDescription()
  }
  if (days) {
    descriptionParts << "On ${fancyString(days)},"
  }
  if ((andOrTime != null) || (modeStart == null)) {
    if (startTime) {
      descriptionParts << "at ${humanReadableStartDate()}"
    }
    if (endTime) {
      descriptionParts << "until ${humanReadableEndDate()}"
    }
  }
  if (modeStart) {
    descriptionParts << "and when ${location.name} enters '${modeStart}' mode"
  }
  if (descriptionParts.size() <= 1) {
    // locks will be in the list no matter what. No rules are set if only locks are in the list
    return null
  }
  return descriptionParts.join(" ")
}

def isActive(lockId) {
  if (
      isUserEnabled() &&
      isValidCode() &&
      isNotBurned() &&
      isEnabled(lockId) &&
      userLockEnabled(lockId) &&
      isCorrectDay() &&
      isInCalendarRange() &&
      isCorrectMode() &&
      isInScheduledTime()
     ) {
    return true
  } else {
    return false
  }
}

def isActiveKeypad() {
  if (
      isUserEnabled() &&
      isValidCode() &&
      isNotBurned() &&
      isCorrectDay() &&
      isInCalendarRange() &&
      isCorrectMode() &&
      isInScheduledTime()
     ) {
    return true
  } else {
    return false
  }
}

def isUserEnabled() {
	if (userEnabled == null || userEnabled) {  //If true or unset, return true
		return true
	} else {
		return false
	}
}

def isValidCode() {
  if (userCode?.isNumber()) {
    return true
  } else {
    return false
  }
}

def isNotBurned() {
  if (burnAfterInt == null || burnAfterInt == 0) {
    return true // is not a burnable user
  } else {
    def totalUsage = getAllLocksUsage()
    if (totalUsage >= burnAfterInt) {
      // usage number is met!
      return false
    } else {
      // dont burn this user yet
      return true
    }
  }
}

def isEnabled(lockId) {
  if (state."lock${lockId}" == null) {
    return true
  } else if (state."lock${lockId}".enabled == null) {
    return true
  } else {
    return state."lock${lockId}".enabled
  }
}

def userLockEnabled(lockId) {
  def lockDisabled = settings."lockDisabled${lockId}"
  if (lockDisabled == null) {
    return true
  } else if (lockDisabled == true) {
    return false
  } else {
    return true
  }
}

def isCorrectDay() {
  def today = new Date().format("EEEE", timeZone())
  if (!days || days.contains(today)) {
    // if no days, assume every day
    return true
  }
  return false
}


def isInCalendarRange() {
  def dateStart = startDateTime()
  def dateEnd = endDateTime()
  def now = rightNow().getTime()
  if (dateStart && dateEnd) {
    // There's both an end time, and a start time.  Allow access between them.
    if (dateStart.getTime() < now && dateEnd.getTime() > now) {
      // It's in calendar times
      return true
    } else {
      // It's not in calendar times
      return false
    }
  } else if (dateEnd && !dateStart) {
    // There's a end time, but no start time.  Allow access until end
    if (dateStart.getTime() > now) {
      // It's after the start time
      return true
    } else {
      // It's before the start time
      return false
    }
  } else if (!dateEnd && dateStart) {
    // There's a start time, but no end time.  Allow access after start
    if (dateStart.getTime() < now) {
      // It's after the start time
      return true
    } else {
      // It's before the start time
      return false
    }
  } else {
    // there's no calendar
    return true
  }
}

def isCorrectMode() {
  if (modeStart) {
    // mode check is on
    if (location.mode == modeStart) {
      // we're in the right one mode
      return true
    } else {
      // we're in the wrong mode
      return false
    }
  } else {
    // mode check is off
    return true
  }
}

def isInScheduledTime() {
  def now = new Date()
  if (startTime && endTime) {
    def start = timeToday(startTime)
    def stop = timeToday(endTime)

    // there's both start time and end time
    if (start.before(now) && stop.after(now)){
      // It's between the times
      return true
    } else {
      // It's not between the times
      return false
    }
  } else if (startTime && !endTime){
    // there's a start time, but no end time
    def start = timeToday(startTime)
    if (start.before(now)) {
      // it's after start time
      return true
    } else {
      //it's before start time
      return false
    }
  } else if (!startTime && endTime) {
    // there's an end time but no start time
    def stop = timeToday(endTime)
    if (stop.after(now)) {
      // it's still before end time
      return true
    } else {
      // it's after end time
      return false
    }
  } else {
    // there are no times
    return true
  }
}

def startDateTime() {
  if (startDay && startMonth && startYear && calStartTime) {
    def time = new Date().parse(smartThingsDateFormat(), calStartTime).format("'T'HH:mm:ss.SSSZ", timeZone(calStartTime))
    return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "${startYear}-${startMonth}-${startDay}${time}")
  } else {
    // Start Date Time not set
    return false
  }
}

def endDateTime() {
  if (endDay && endMonth && endYear && calEndTime) {
    def time = new Date().parse(smartThingsDateFormat(), calEndTime).format("'T'HH:mm:ss.SSSZ", timeZone(calEndTime))
    return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", "${endYear}-${endMonth}-${endDay}${time}")
  } else {
    // End Date Time not set
    return false
  }
}

def rightNow() {
  def now = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", timeZone())
  return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", now)
}

def getLockById(params) {
  return parent.locks.find{it.id == id}
}

def getLockApp(lockId) {
  def lockApp = false
  def lockApps = parent.getLockApps()
  lockApps.each { app ->
    if (app.lock.id == lockId) {
      lockApp = app
    }
  }
  return lockApp
}

def getLock(params) {
  def id = ''
  // Assign params to id.  Sometimes parameters are double nested.
  debugger('params: ' + params)
  debugger('last: ' + state.lastLock)
  if (params?.id) {
    id = params.id
  } else if (params?.params){
    id = params.params.id
  }
  def lockApp = getLockApp(id)
  if (!lockApp) {
    lockApp = getLockApp(state.lastLock)
  }

  if (lockApp) {
    state.lastLock = lockApp.lock.id
    return lockApp.lock
  } else {
    return false
  }
}

def userNotificationSettings() {
  def userSettings = false
  if (phone != null || notification || muteUser || recipients) {
    // user has it's own settings!
    userSettings = true
  }
  return userSettings
}

def send(msg) {
  if (userNotificationSettings()) {
    checkIfNotifyUser(msg)
  } else {
    checkIfNotifyGlobal(msg)
  }
}

def checkIfNotifyUser(msg) {
  if (checkinNotify && getAllLocksUsage() < 2) {
    sendMessageViaUser(msg)
  }
  if (notificationStartTime != null && notificationEndTime != null) {
    def start = timeToday(notificationStartTime)
    def stop = timeToday(notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessageViaUser(msg)
    }
  } else {
    sendMessageViaUser(msg)
  }
}

def checkIfNotifyGlobal(msg) {
  if (parent.notificationStartTime != null && parent.notificationEndTime != null) {
    def start = timeToday(parent.notificationStartTime)
    def stop = timeToday(parent.notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessageViaParent(msg)
    }
  } else {
    sendMessageViaParent(msg)
  }
}

def sendMessageViaParent(msg) {
  if (parent.recipients) {
    sendNotificationToContacts(msg, parent.recipients)
  } else {
    if (parent.notification) {
      sendPush(msg)
    } else {
      sendNotificationEvent(msg)
    }
    if (parent.phone) {
      if ( parent.phone.indexOf(";") > 1){
        def phones = parent.phone.split(";")
        for ( def i = 0; i < phones.size(); i++) {
          sendSms(phones[i], msg)
        }
      }
      else {
        sendSms(parent.phone, msg)
      }
    }
  }
}

def sendMessageViaUser(msg) {
  if (recipients) {
    sendNotificationToContacts(msg, recipients)
  } else {
    if (notification) {
      sendPush(msg)
    } else {
      sendNotificationEvent(msg)
    }
    if (phone) {
      if ( phone.indexOf(";") > 1){
        def phones = phone.split(";")
        for ( def i = 0; i < phones.size(); i++) {
          sendSms(phones[i], msg)
        }
      }
      else {
        sendSms(phone, msg)
      }
    }
  }
}

def disableLock(lockID) {
  state."lock${lockID}".enabled = false
  state."lock${lockID}".disabledReason = 'Controller failed to set user code.'
}

def enableLock(lockID) {
  state."lock${lockID}".enabled = true
  state."lock${lockID}".disabledReason = null
}

def getLockUserInfo(lock) {
  def para = "\n${app.label}"
  if (settings."lockDisabled${lock.id}") {
    para += " DISABLED"
  }
  def usage = state."lock${lock.id}".usage
  para += " // Entries: ${usage}"
  if (!state."lock${lock.id}".enabled) {
    def reason = state."lock${lock.id}".disabledReason
    para += "\n ${reason}"
  }
  para
}

// User Ask Alexa

def userAlexaSettings() {
  if (alexaAccess || alexaLock || alexaAccessStart || alexaAccessEnd || alexaStartTime || alexaEndTime) {
    // user has it's own settings!
    return true
  }
  // user doesn't !
  return false
}

def askAlexa(msg) {
  if (userAlexaSettings()) {
    checkIfAlexaUser(msg)
  } else {
    checkIfAlexaGlobal(msg)
  }
}

def checkIfAlexaUser(message) {
  if (!muteUser) {
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

def checkIfAlexaGlobal(message) {
  if (parent.alexaStartTime != null && parent.alexaEndTime != null) {
    def start = timeToday(parent.alexaStartTime)
    def stop = timeToday(parent.alexaEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendAskAlexa(message)
    }
  } else {
    sendAskAlexa(message)
  }
}

def sendAskAlexa(message) {
  sendLocationEvent(name: 'AskAlexaMsgQueue',
                    value: 'LockManager/User',
                    isStateChange: true,
                    descriptionText: message,
                    unit: "User//${userName}")
}

private anyoneHome(sensors) {
  def result = false
  if(sensors.findAll { it?.currentPresence == "present" }) {
    result = true
  }
  result
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

def debugger(message) {
  def doDebugger = parent.debuggerOn()
  if (doDebugger) {
    log.debug(message)
  }
}

def doCalenderCheck() {
  def params = [
    uri: ical
  ]
  def now = new Date()
  def newCode = null
  def beforeCheckout = (now.before(timeToday(checkoutTime)))

  try {
    httpGet(params) { resp ->
      def data = parseICal(resp.data)

      for (event in data) {
        if (event['phone']) {
          def code = event['phone'].replaceAll(/\D/, '')[-4..-1]
          debugger("start: ${event['dtStart']}, end: ${event['dtEnd']}, phone: ${event['phone']}, codeIndex: ${codeIndex}, code: ${code}")
          newCode = code
          if (beforeCheckout) {
            // set the first code we find
            break
          }
        } else {
          send("Airbnb Warning: Phone number not set for event today! - ${event['record']}")
        }
      }
    }
  } catch (e) {
    log.error "something went wrong: $e"
  }

  if (newCode) {
    if (state.userCode != newCode) {
      state.userCode = newCode
      debugger("setting code to ${newCode}, state.userCode = ${state.userCode}")
      resetAllLocksUsage()
      parent.setAccess()
    }
  } else {
    // there is no guest today
    state.userCode = ''
    resetAllLocksUsage()
    parent.setAccess()
  }
}

String readLine(ByteArrayInputStream is) {
  int size = is.available();
  if (size <= 0) {
    return null;
  }

  String ret = "";
  byte data = 0;
  char ch;

  while (true) {
    data = is.read();
    if (data == -1) {
      // we are done here
      break;
    }

    ch = (char)(data&0xff);
    if (ch == '\n') {
      break;
    }

    ret += ch;

    if (ret.endsWith("\\n")) {
      ret = ret.replaceAll(/\\n/,"");
      break;
    }
  }

  return ret;
}

def currentEvent(today, event) {
  return ((event['dtStart'] < today) && (today < (event['dtEnd']+1)))
}

def parseICal(ByteArrayInputStream is) {
  def iCalEvents = []
  def iCalEvent = null
  def sincePhone = 100
  def today = new Date()

  while (true) {
    def line = readLine(is)

    if (line == null) {
      break;
    }

    if (line == "BEGIN:VEVENT") {
      iCalEvent = [record:'']
    } else if (line == "END:VEVENT") {
      if (currentEvent(today, iCalEvent) && iCalEvent['summary'] != 'Not available') {
        iCalEvents.push(iCalEvent)
      }
      iCalEvent = null
    } else if (iCalEvent != null) {
      // parse line
      def compoundKey = null
      def subKey = null
      def key = null
      def value = null

      sincePhone++;

      if ( line ==~ /^[A-Z]+[;:].*/ ) {
        // grab everything before the :
        key = line.replaceAll(/:.*/, '')
        // grab everything before the ;
        compoundKey = key.replaceAll(/;.*/, '')
        // grab everything after the ${key}:
        value = line.replaceFirst(key + ':', '').trim()
        // grab everything before the ; in the key
        if (compoundKey != key) {
          // we found a compound date key
          subKey = key.replaceFirst(compoundKey + ';', '').trim()
        }

        if (key == 'DESCRIPTION') {
          // we found the start of the description
          key = value.replaceAll(/:.*/, '')
          value = value.replaceFirst(key + ':', '').trim()
        }

        if (key == 'UID') { iCalEvent.put('uid',value) }
        else if (key == 'CREATED') { iCalEvent.put('created', value) }
        else if (key == 'RRULE') { iCalEvent.put('rRule', value) }
        else if (key == 'RDATE') { iCalEvent.put('rDate', value) }
        else if (key == 'DTSTAMP') { iCalEvent.put('dtStamp', parseDate(value)) }
        else if (key == 'CHECKIN') { iCalEvent.put('checkin', value) }
        else if (key == 'CHECKOUT') { iCalEvent.put('checkout', value) }
        else if (key == 'NIGHTS') { iCalEvent.put('nights', value) }
        else if (key == 'EMAIL') { iCalEvent.put('email', value) }
        else if (key == 'SUMMARY') { iCalEvent.put('summary', value) }
        else if (key == 'LOCATION') { iCalEvent.put('location', value) }
        else if (key == 'PHONE') { sincePhone = 0; }
        else if (compoundKey == 'DTSTART') {
          iCalEvent.put('dtStartString', value)
          iCalEvent.put('dtStart', parseDate(value))
          iCalEvent.put('dtStartTz', subKey)
        } else if (compoundKey == 'DTEND') {
          iCalEvent.put('dtEndString', value)
          iCalEvent.put('dtEnd', parseDate(value)) 
          iCalEvent.put('dtEndTz', subKey)
        }
      }

      if (sincePhone == 1) {
        // phone number
        iCalEvent.put('phone', line)
      }

      if (line) {
        iCalEvent['record'] = iCalEvent['record'] + line + '\n'
      }
    }
  }
  

  return iCalEvents
}

Date parseDate(String value) {
  if ( value ==~ /[0-9]*T[0-9]*Z/ ) {
    Date.parse("yyyyMMdd'T'HHmmss'Z'", value)
  } else if ( value ==~ /[0-9]*T[0-9]*/ ) {
    Date.parse("yyyyMMdd'T'HHmmss", value)
  } else if ( value ==~ /[0-9]*/ ) {
    Date.parse("yyyyMMdd", value)
  } else {
    println "WARNING: unknown date format: ${value}"
    null
  }
}
