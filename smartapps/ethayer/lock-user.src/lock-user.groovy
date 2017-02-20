definition (
  name: 'Lock User2',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage users. This is a child app.',
  category: 'Safety & Security',

  parent: 'ethayer:Lock Manager2',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page name: 'rootPage'
  page name: 'lockPage', title: 'Manage Lock', install: false, uninstall: false
  page name: 'schedulingPage', title: 'Schedule User', install: false, uninstall: false
  page name: 'calendarPage', title: 'Calendar', install: false, uninstall: false
  page name: 'notificationPage'
  page name: 'reEnableUserLockPage'
  page name: 'lockResetPage'
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
  initalizeLockData()

  // set listeners
  subscribe(parent.locks, "codeReport", codeReturn)
  subscribe(parent.locks, "lock", codeUsed)
  subscribe(location, locationHandler)
  subscribeToSchedule()

  // ask for parent init
  parent.setAccess()
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
    runOnce(startDateTime().format(smartThingsDateFormat(), location.timeZone), 'calendarStart')
  }
  if (endDateTime()) {
    // schedule calendar end!
    log.debug 'scheduling calendar end'
    runOnce(endDateTime().format(smartThingsDateFormat(), location.timeZone), 'calendarEnd')
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

def initalizeLockData() {
  parent.locks.each { lock->
    if (state."lock${lock.id}" == null) {
      state."lock${lock.id}" = [:]
      state."lock${lock.id}".code = false
      state."lock${lock.id}".enabled = true
      state."lock${lock.id}".access = false
      state."lock${lock.id}".errorLoop = false
      state."lock${lock.id}".errorLoopCount = 0
      state."lock${lock.id}".disabledReason = ''
      state."lock${lock.id}".usage = 0
    }
  }
}

def resetLockUsage(lockId) {
  state."lock${lockId}".usage = 0
  lockReset(lockId)
}

def lockReset(lockId) {
  state."lock${lockId}".enabled = true
  state."lock${lockId}".access = false
  state."lock${lockId}".errorLoop = false
  state."lock${lockId}".errorLoopCount = 0
  state."lock${lockId}".disabledReason = ''
}

def rootPage() {
  //reset errors on each load
  dynamicPage(name: 'rootPage', title: '', install: true, uninstall: true) {
    section('User Settings') {
      def title = 'Code (4 to 8 digits)'
      def usage = getAllLocksUsage()
      def text
      if (isActive()) {
        text = 'active'
      } else {
        text = 'inactive'
      }

      paragraph "${text}/${usage}"
      parent.locks.each { lock->
        // set required pin length if a lock requires it
        if (lock.hasAttribute('pinLength')) {
          title = "Code (Must be ${lock.latestValue('pinLength')} digits)"
        }
      }
      label title: "Name for User", defaultValue: app.label, required: false, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user.png'

      input(name: "userCode", type: "text", title: title, required: false, defaultValue: settings."userCode", refreshAfterSelection: true)
      input(name: "userSlot", type: "enum", options: parent.availableSlots(settings.userSlot), title: "Select slot", required: true, refreshAfterSelection: true )
    }
    section('Additional Settings') {
      def actions = location.helloHome?.getPhrases()*.label
      if (actions) {
        actions.sort()
        input name: 'userUnlockPhrase', type: 'enum', title: 'Hello Home Phrase', multiple: true,required: false, options: actions, refreshAfterSelection: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/home.png'
      }
      input(name: 'burnAfterInt', title: "How many uses before burn?", type: "number", required: false, description: 'Blank or zero is infinite', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/fire.png')
      href(name: 'toSchedulingPage', page: 'schedulingPage', title: 'Schedule (optional)', description: schedulingHrefDescription(), state: schedulingHrefDescription() ? 'complete' : '', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/calendar.png')
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bullhorn.png')
    }
    section('Locks') {
      initalizeLockData()
      parent.locks.each { lock->
        href(name: "toLockPage${lock.id}", page: 'lockPage', params: [id: lock.id], description: lockPageDescription(lock.id), required: false, title: lock.displayName, image: lockPageImage(lock) )
      }
    }
  }
}

def lockPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'https://dl.dropboxusercontent.com/u/54190708/LockManager/ban.png'
  } else {
    return 'https://dl.dropboxusercontent.com/u/54190708/LockManager/lock.png'
  }
}

def lockInfoPageImage(lock) {
  if (!state."lock${lock.id}".enabled || settings."lockDisabled${lock.id}") {
    return 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user-times.png'
  } else {
    return 'https://dl.dropboxusercontent.com/u/54190708/LockManager/user.png'
  }
}

def lockPage(params) {
  dynamicPage(name:"lockPage", title:"Lock Settings") {
    def lock = getLock(params)
    def lockCode = state."lock${lock.id}".code
    def lockAccessable = state."lock${lock.id}".access
    def errorLoopCount = state."lock${lock.id}".errorLoopCount
    def usage = state."lock${lock.id}".usage
    if (!state."lock${lock.id}".enabled) {
      section {
        paragraph "WARNING:\n\nThis user has been disabled.\nReason: ${state."lock${lock.id}".disabledReason}", image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/ban.png'
        href(name: "reEnableUserLockPage", title: "Reset User", page: "reEnableUserLockPage", params: [id: lock.id], description: "Tap to reset")
      }
    }
    section("${deviceLabel(lock)} settings for ${app.label}") {
      if (lockCode) {
        paragraph "Lock is currently set to ${lockCode}"
      }
      paragraph "User unlock count: ${usage}"
      if( errorLoopCount > 0) {
        paragraph "Lock set failed try ${errorLoopCount}/10"
      }
      input(name: "lockDisabled${lock.id}", type: "bool", title: "Disable lock for this user?", required: false, defaultValue: settings."lockDisabled${lock.id}", refreshAfterSelection: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/ban.png' )
      href(name: "toLockResetPage", page: "lockResetPage", title: "Reset Lock", description: 'Reset lock data for this user.',  params: [id: lock.id], image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/refresh.png' )
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
  dynamicPage(name:"reEnableUserLockPage", title:"User re-enabled") {
    section {
      paragraph "Lock has been reset."
    }
    section {
      href(name: "toRootPage", title: "Back To Setup", page: "rootPage")
    }
  }
}

def lockResetPage(params) {
  // do reset
  def lock = getLock(params)
  resetLockUsage(lock.id)
  dynamicPage(name:"lockResetPage", title:"Lock reset") {
    section {
      paragraph "Lock has been reset."
    }
    section {
      href(name: "toRootPage", title: "Back To Setup", page: "rootPage")
    }
  }
}

def schedulingPage() {
  dynamicPage(name: "schedulingPage", title: "Rules For Access Scheduling") {

    section {
      href(name: "toCalendarPage", title: "Calendar", page: "calendarPage", description: calendarHrefDescription(), state: calendarHrefDescription() ? "complete" : "")
    }

    section {
      input(name: "days", type: "enum", title: "Allow User Access On These Days", description: "Every day", required: false, multiple: true, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"], submitOnChange: true)
    }
    section {
      input(name: "modeStart", title: "Allow Access only when in this mode", type: "mode", required: false, mutliple: false, submitOnChange: true)
    }
    section {
      input(name: "startTime", type: "time", title: "Start Time", description: null, required: false)
      input(name: "endTime", type: "time", title: "End Time", description: null, required: false)
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
  dynamicPage(name: "notificationPage", title: "Notification Settings") {

    section {
      if (phone == null && !notification && !sendevent && !recipients) {
        input(name: "muteUser", title: "Mute this user?", type: "bool", required: false, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/bell-slash-o.png')
      }
      input("recipients", "contact", title: "Send notifications to", submitOnChange: true, required: false, multiple: true, image: 'https://dl.dropboxusercontent.com/u/54190708/LockManager/book.png')
      if (!recipients) {
        input(name: "phone", type: "text", title: "Text This Number", description: "Phone number", required: false, submitOnChange: true)
        paragraph "For multiple SMS recipients, separate phone numbers with a semicolon(;)"
        input(name: "notification", type: "bool", title: "Send A Push Notification", description: "Notification", required: false, submitOnChange: true)
      }
      if (phone != null || notification || sendevent || recipients) {
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

public smartThingsDateFormat() { "yyyy-MM-dd'T'HH:mm:ss.SSSZ" }

public humanReadableStartDate() {
  new Date().parse(smartThingsDateFormat(), startTime).format("h:mm a", timeZone(startTime))
}
public humanReadableEndDate() {
  new Date().parse(smartThingsDateFormat(), endTime).format("h:mm a", timeZone(endTime))
}

def readableDateTime(date) {
  new Date().parse(smartThingsDateFormat(), date.format(smartThingsDateFormat(), location.timeZone)).format("EEE, MMM d yyyy 'at' h:mma", location.timeZone)
}

def getAllLocksUsage() {
  def usage = 0
  parent.locks.each { lock ->
    if (state."lock${lock.id}"?.usage) {
      usage = usage + state."lock${lock.id}".usage
    }
  }
  return usage
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
  if (muteUser) {
    msg = 'User Muted'
  }
  return msg
}

def fancyDeviceString(devices = []) {
  fancyString(devices.collect { deviceLabel(it) })
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

  return fancify(listOfStrings)
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
  def today = new Date().format("EEEE", location.timeZone)
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
  def now = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
  return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", now)
}

def codeUsed(evt) {
  log.debug("codeUsed evt.value: " + evt.value + ". evt.data: " + evt.data)
  def message = null
  def lockId = evt.deviceId
  if(evt.value == "unlocked" && evt.data) {
    def codeData = new JsonSlurper().parseText(evt.data)
    if(codeData.usedCode && codeData.usedCode.isNumber() && codeData.usedCode.toInteger() == userSlot.toInteger()) {
      // check the status of the lock, helpful for some schlage locks.
      runIn(10, doPoll)

      message = "${evt.displayName} was unlocked by ${app.label}"
      state."lock${lockId}".usage = state."lock${lockId}".usage + 1
      if (!isNotBurned()) {
        message += ".  Now burning code."
      }
      if (userUnlockPhrase) {
        location.helloHome.execute(userUnlockPhrase)
      }
    }
  } else if(evt.value == "locked" && settings.notifyLock) {
    // message = "${evt.displayName} has been locked"
    // TODO: Handle what to do when the lock is locked
  }

  if (message) {
    log.debug("Sending message: " + message)
    send(message)
  }
}

def codeReturn(evt) {
  def codeNumber = evt.data.replaceAll("\\D+","")
  def codeSlot = evt.integerValue.toInteger()
  def lock = evt.device

  if (userSlot.toInteger() == codeSlot) {
    if (codeNumber == "") {
      if (state."lock${lock.id}".access == true) {
        log.debug "Lock is ${state."lock${lock.id}".access} setting to false!"
        state."lock${lock.id}".access = false
        if (notifyAccessEnd || parent.notifyAccessEnd) {
          def message = "${app.label} no longer has access to ${evt.displayName}"
          if (codeNumber.isNumber()) {
            state."lock${lock.id}".codes."slot${codeSlot}" = codeNumber
          }
          send(message)
        }
      }
    } else if (state."lock${lock.id}".access == false) {
      state."lock${lock.id}".access = true
      if (notifyAccessStart || parent.notifyAccessStart) {
        def message = "${app.label} now has access to ${evt.displayName}"
        if (codeNumber.isNumber() && codeNumber.toInteger() != userCode.toInteger()) {
          log.debug "code: ${codeNumber} should be ${userCode.toInteger()}"
          log.debug 'set message to null'
          // number is set to the wrong value!
          message = null
        }
        if (message) {
          send(message)
        }
      }
    }
  }
}

def pollCodeReport(evt) {
  def codeData = new JsonSlurper().parseText(evt.data)

  def lock = parent.locks.find{it.id == evt.deviceId}
  def active = isActive(lock.id)
  def currentCode = codeData."code${userSlot}"
  def array = []

  if (active) {
    if (currentCode != userCode) {
      array << ["code${userSlot}", userCode]
    }
  } else {
    if (currentCode) {
      // Code is set, We should be disabled.
      array << ["code${userSlot}", '']
    }
  }


  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    //Lock is in an error state
    state."lock${lock.id}".errorLoop = true
    def errorNumber = state."lock${lock.id}".errorLoopCount + 1
    if (errorNumber <= 10) {
      log.debug "sendCodes fix is: ${json} Error: ${errorNumber}/10"
      state."lock${lock.id}".errorLoopCount = errorNumber
      lock.updateCodes(json)
    } else {
      // reset code
      array = []
      array << ["code${userSlot}", '']
      json = new groovy.json.JsonBuilder(array).toString()

      log.debug "kill fix is: ${json}"
      lock.updateCodes(json)

      // set user to disabled state
      if (state."lock${lock.id}".enabled) {
        state."lock${lock.id}".enabled = false
        state."lock${lock.id}".errorLoop = false
        state."lock${lock.id}".disabledReason = "Controller failed to set code"
        send("Controller failed to set code for ${app.label}")
      }
    }
  } else {
    // reset disabled state, set was successful!
    state."lock${lock.id}".errorLoop = false
    state."lock${lock.id}".errorLoopCount = 0
  }
}

def isInErrorLoop(lockId) {
  def errorInLoop = false
  if (state."lock${lockId}".errorLoop) {
    // Child is in error state
    errorInLoop = true
  }
  return errorInLoop
}

def errorLoopArray() {
  def loopArray = []
  parent.locks.each { lock ->
    if (state."lock${lock.id}".errorLoop) {
      // Child is in error state
      loopArray << lock.id
    }
  }
  return loopArray
}

def doPoll() {
  parent.locks.poll()
}

def setKnownCode(currentCode, lock) {
  def setCode
  if (currentCode.isNumber()) {
    setCode = currentCode
  } else if (!currentCode) {
    setCode = false
  }
  state."lock${lock.id}".code = setCode
}

def getLockById(params) {
  return parent.locks.find{it.id == id}
}

def getLock(params) {
  def id = ''
  // Assign params to id.  Sometimes parameters are double nested.
  if (params?.id) {
    id = params.id
  } else if (params?.params){
    id = params.params.id
  } else if (state.lastLock) {
    id = state.lastLock
  }

  state.lastLock = id
  return parent.locks.find{it.id == id}
}

def userNotificationSettings() {
  if (phone != null || notification || sendevent || muteUser) {
    // user has it's own settings!
    return true
  }
  // user doesn'r !
  return false
}

def send(msg) {
  if (userNotificationSettings()) {
    checkIfNotifyUser(msg)
  } else {
    checkIfNotifyGlobal(msg)
  }
}

def checkIfNotifyUser(msg) {
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
    if (parent.start.before(now) && parent.stop.after(now)){
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
      if ( phone.indexOf(";") > 1){
        def phones = parent.phone.split(";")
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
