def airbnbInstalled() {
  log.debug "Installed with settings: ${settings}"
  userInitialize()
}

def airbnbUpdated() {
  log.debug "Airbnb Updated with settings: ${settings}"
  airbnbInitialize()
}

def airbnbInitialize() {
  // reset listeners
  unsubscribe()
  // unschedule()

  // setup data
  initializeAirbnbCodeState()
  initializeLockData()
  initializeLocks()
  if (ical) {
    airbnbCalenderCheck()
  }

  // set listeners
  airbnbSubscribeToSchedule()
}

def airbnbUninstalled() {
  unschedule()

  // prompt locks to delete this user
  initializeLocks()
}

def airbnbSubscribeToSchedule() {
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
  if (ical) {
    // schedule airbnb code setter
    runEvery15Minutes('airbnbCalenderCheck')
  }
}

def initializeAirbnbCodeState() {
  if (!atomicState.userCode) { atomicState.userCode = '' }
  if (!state.guestName) { state.guestName = '' }
  if (!state.eventStart) { state.eventStart = '' }
  if (!state.eventEnd) { state.eventEnd = '' }
}

def airbnbLandingPage() {
  if (userName) {
    airbnbMainPage()
  } else {
    airbnbSetupPage()
  }
}

def airbnbSetupPage() {
  dynamicPage(name: 'airbnbSetupPage', title: 'Setup Lock', nextPage: 'airbnbMainPage', uninstall: true) {
    section('Choose details for this Airbnb automation') {
      def defaultTime = timeToday("13:00", timeZone()).format(smartThingsDateFormat(), timeZone())
      def defaultEarlyCheckin = timeToday("8:00", timeZone()).format(smartThingsDateFormat(), timeZone())
      def defaultLateCheckout = timeToday("17:00", timeZone()).format(smartThingsDateFormat(), timeZone())
      input(name: 'ical', type: 'text', title: 'Airbnb Calendar Link',
            image: 'https://images.lockmanager.io/app/v1/images/calendar.png',
            refreshAfterSelection: true, required: true)
      input(name: 'userSlot', type: 'enum', title: 'Select slot',
            options: parent.availableSlots(settings.userSlot),
            refreshAfterSelection: true, required: true)
      input(name: 'checkoutTime', type: 'time', title: 'Code Change Time',
            defaultValue: defaultTime, refreshAfterSelection: true, required: true)
      input(name: 'userName', title: 'Name for User', required: true, defaultValue: 'Airbnb',
            image: 'https://images.lockmanager.io/app/v1/images/user.png')
      input(name: 'earlyCheckin', title: 'Early Checkin Enable', type: 'bool',
            description: 'Moves the code change time to Early Checkin Time if previous night is not booked',
            refreshAfterSelection: true, required: true, defaultValue: true)
      input(name: 'earlyCheckinTime', type: 'time', title: 'Early Checkin Time',
            refreshAfterSelection: true, required: true, defaultValue: defaultEarlyCheckin)
      input(name: 'lateCheckout', title: 'Late Checkout Enable', type: 'bool',
            description: 'Moves the code change time to Late Checkout Time if following night is not booked',
            refreshAfterSelection: true, required: true, defaultValue: true)
      input(name: 'lateCheckoutTime', type: 'time', title: 'Late Checkout Time',
            refreshAfterSelection: true, required: true, defaultValue: defaultLateCheckout)
    }
  }
}

def airbnbMainPage() {
  //reset errors on each load
  dynamicPage(name: 'airbnbMainPage', title: '', install: true, uninstall: true) {
    section('Airbnb Settings') {
      def usage = getAllLocksUsage()
      def text
      if (isActive()) {
        text = 'active'
      } else {
        text = 'inactive'
      }
      paragraph "${text}/${usage}"
      paragraph("User Code: " + getCode(this))
      paragraph("Guest Name: " + state.guestName)
      paragraph("Start: " + state.eventStart)
      paragraph("End: " + state.eventEnd)
      input(name: 'userEnabled', type: 'bool', title: "User Enabled?", required: false,
          defaultValue: true, refreshAfterSelection: true)
      input(name: 'ical', type: 'text', title: 'Airbnb Calendar Link',
            image: 'https://images.lockmanager.io/app/v1/images/calendar.png',
            refreshAfterSelection: true, required: true)
      input(name: 'userSlot', type: 'enum', title: 'Select slot',
            options: parent.availableSlots(settings.userSlot),
            refreshAfterSelection: true, required: true)
      input(name: 'checkoutTime', type: 'time', title: 'Code Change Time',
            defaultValue: defaultTime, refreshAfterSelection: true, required: true)
      input(name: 'earlyCheckin', title: 'Early Checkin Enable', type: 'bool',
            description: 'Moves the code change time to Early Checkin Time if previous night is not booked',
            refreshAfterSelection: true, required: true, defaultValue: true)
      input(name: 'earlyCheckinTime', type: 'time', title: 'Early Checkin Time',
            refreshAfterSelection: true, required: true, defaultValue: defaultEarlyCheckin)
      input(name: 'lateCheckout', title: 'Late Checkout Enable', type: 'bool',
            description: 'Moves the code change time to Late Checkout Time if following night is not booked',
            refreshAfterSelection: true, required: true, defaultValue: true)
      input(name: 'lateCheckoutTime', type: 'time', title: 'Late Checkout Time',
            refreshAfterSelection: true, required: true, defaultValue: defaultLateCheckout)
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
      href(name: 'toAirbnbNotificationPage', page: 'airbnbNotificationPage', title: 'Notification Settings', description: airbnbNotificationPageDescription(), state: airbnbNotificationPageDescription() ? 'complete' : '', image: 'https://images.lockmanager.io/app/v1/images/bullhorn.png')
      href(name: 'toAirbnbKeypadPage', page: 'airbnbKeypadPage', title: 'Keypad Routines (optional)', image: 'https://images.lockmanager.io/app/v1/images/keypad.png')
    }
    section('Locks') {
      initializeLockData()
      def lockApps = parent.getLockApps()

      lockApps.each { app ->
        href(name: "toLockPage${app.lock.id}", page: 'airbnbLockPage', params: [id: app.lock.id], description: lockPageDescription(app.lock.id), required: false, title: app.lock.label, image: lockPageImage(app.lock) )
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label(title: "Name for App", defaultValue: 'User: ' + userName, required: true, image: 'https://images.lockmanager.io/app/v1/images/user.png')
      input name: 'userName', title: "Name for user", required: true, image: 'https://images.lockmanager.io/app/v1/images/user.png'
      input(name: "userSlot", type: "enum", options: parent.availableSlots(settings.userSlot), title: "Select slot", required: true, refreshAfterSelection: true )
    }
  }
}

def airbnbLockPage(params) {
  dynamicPage(name:"airbnbLockPage", title:"Lock Settings") {
    debugger('current params: ' + params)
    def lock = getLock(params)
    def lockApp = parent.getLockAppById(lock.id)
    def slotData = lockApp.slotData(userSlot)

    def usage = state."lock${lock.id}".usage

    debugger('found lock id?: ' + lock?.id)

    if (!state."lock${lock.id}".enabled) {
      section {
        paragraph "WARNING:\n\nThis user has been disabled.\n${state."lock${lock.id}".disabledReason}", image: 'https://images.lockmanager.io/app/v1/images/ban.png'
        href(name: 'toReEnableAirbnbLockPage', page: 'reEnableAirbnbLockPage', title: 'Reset Airbnb', description: 'Retry setting this Airbnb automation.',  params: [id: lock.id], image: 'https://images.lockmanager.io/app/v1/images/refresh.png' )
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
      href(name: 'toLockResetPage', page: 'airbnbLockResetPage', title: 'Reset Lock', description: 'Reset lock data for this user.',  params: [id: lock.id], image: 'https://images.lockmanager.io/app/v1/images/refresh.png' )
    }
  }
}

def airbnbKeypadPage() {
  dynamicPage(name: 'airbnbKeypadPage',title: 'Keypad Settings (optional)', install: true, uninstall: true) {
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

def reEnableAirbnbLockPage(params) {
  // do reset
  def lock = getLock(params)
  lockReset(lock.id)

  dynamicPage(name:'reEnableAirbnbLockPage', title:'Airbnb re-enabled') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'airbnbMainPage')
    }
  }
}

def airbnbLockResetPage(params) {
  // do reset
  def lock = getLock(params)

  state."lock${lock.id}".usage = 0
  lockReset(lock.id)

  dynamicPage(name:'airbnbLockResetPage', title:'Lock reset') {
    section {
      paragraph 'Lock has been reset.'
    }
    section {
      href(name: 'toMainPage', title: 'Back To Setup', page: 'airbnbMainPage')
    }
  }
  dynamicPage(name: 'airbnbNotificationPage', title: 'Notification Settings') {

    section {
      if (phone == null && !notification && !recipients) {
        input(name: 'muteUser', title: 'Mute this user?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
      }
      if (!muteUser) {
        input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/book.png')
        if (!recipients) {
          input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
          paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
          input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
        }
        if (phone != null || notification || recipients) {
          input(name: 'muteAfterCheckin', title: 'Mute after checkin', description: 'Mute notifications after first use of new code', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
          input(name: 'notifyCodeChange', title: 'when Code changes', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/check-circle-o.png')
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

def airbnbNotificationPage() {
  dynamicPage(name: 'airbnbNotificationPage', title: 'Notification Settings') {

    section {
      if (phone == null && !notification && !recipients) {
        input(name: 'muteUser', title: 'Mute this user?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally', image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
      }
      if (!muteUser) {
        input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true, image: 'https://images.lockmanager.io/app/v1/images/book.png')
        if (!recipients) {
          input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
          paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
          input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
        }
        if (phone != null || notification || recipients) {
          input(name: 'muteAfterCheckin', title: 'Mute after checkin', description: 'Mute notifications after first use of new code', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/bell-slash-o.png')
          input(name: 'notifyCodeChange', title: 'when Code changes', type: 'bool', required: false, image: 'https://images.lockmanager.io/app/v1/images/check-circle-o.png')
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

def airbnbNotificationPageDescription() {
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

  if (settings.notifyCodeChange) {
    parts << 'on code change'
  }
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

def airbnbNotificationSettings() {
  def userSettings = false
  if (phone != null || notification || muteUser || recipients || muteAfterCheckin) {
    // user has it's own settings!
    userSettings = true
  }
  return userSettings
}

def sendAirbnbMessage(msg) {
  if (airbnbNotificationSettings()) {
    checkIfNotifyAirbnb(msg)
  } else {
    checkIfNotifyGlobal(msg)
  }
}

def checkIfNotifyAirbnb(msg) {
  if (muteAfterCheckin) {
    if (getAllLocksUsage() < 2) {
      sendMessageViaUser(msg)
    }
  } else {
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
}

def resetAllLocksUsage() {
  def lockApps = parent.getLockApps()
  lockApps.each { lockApp ->
    if (state."lock${lockApp.lock.id}"?.usage) {
      state."lock${lockApp.lock.id}"?.usage = 0
    }
  }
}

def getAirbnbCode() {
  return atomicState.userCode
}

def airbnbCalenderCheck() {
  def params = [
    uri: ical
  ]

  asynchttp_v1.get('parseICal', params)
}

def setNewCode() {
  def hubName = location.getName()
  resetAllLocksUsage()
  parent.setAccess()

  if (settings.notifyCodeChange) {
    if (atomicState.userCode != '') {
      sendMessageViaUser("${hubName} ${userName}: Setting code ${settings.userSlot} to ${atomicState.userCode} for ${state.guestName}")
    } else {
      sendMessageViaUser("${hubName} ${userName}: Clearing code ${settings.userSlot}")
    }
  }
}

String readLine(ByteArrayInputStream inputStream) {
  int size = inputStream.available();
  if (size <= 0) {
    return null;
  }

  String ret = "";
  byte data = 0;
  char ch;

  while (true) {
    data = inputStream.read();
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
  return ((event['dtStart'] <= today) && (today <= event['dtEnd']))
}

def parseICal(response, data) {
  if (response.hasError()) {
    log.debug "response received error: ${response.getErrorMessage()}"
    return
  }
  def code = ''
  def event = null
  def events = []
  def iCalEvent = null
  def sincePhone = 100
  def today = rightNow()

  def startTimeOfDay = checkoutTime
  def endTimeOfDay = checkoutTime

  if(earlyCheckin) {
    startTimeOfDay = earlyCheckinTime
  }
  if(lateCheckout) {
    endTimeOfDay = lateCheckoutTime
  }

  InputStream inputStream = new ByteArrayInputStream(response.getData().getBytes())

  while (true) {
    def line = readLine(inputStream)

    if (line == null) {
      break
    }

    if (line == "BEGIN:VEVENT") {
      iCalEvent = [record:'']
    } else if (line == "END:VEVENT") {
      if(iCalEvent['summary'] == 'Not available') {
        // adjust the time of the not available events
        if (earlyCheckin) {
          iCalEvent.put('dtEnd', parseDate(iCalEvent['dtEndString'], earlyCheckinTime))
        }
        if (lateCheckout) {
          iCalEvent.put('dtStart', parseDate(iCalEvent['dtStartString'], lateCheckoutTime))
        }
      }

      if (currentEvent(today, iCalEvent)) {
        events.push(iCalEvent.clone())
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
        else if (key == 'DTSTAMP') { iCalEvent.put('dtStamp', parseDate(value, checkoutTime)) }
        else if (key == 'CHECKIN') { iCalEvent.put('checkin', value) }
        else if (key == 'CHECKOUT') { iCalEvent.put('checkout', value) }
        else if (key == 'NIGHTS') { iCalEvent.put('nights', value) }
        else if (key == 'EMAIL') { iCalEvent.put('email', value) }
        else if (key == 'SUMMARY') { iCalEvent.put('summary', value) }
        else if (key == 'LOCATION') { iCalEvent.put('location', value) }
        else if (key == 'PHONE') { sincePhone = 0; }
        else if (compoundKey == 'DTSTART') {
          iCalEvent.put('dtStartString', value)
          iCalEvent.put('dtStart', parseDate(value, startTimeOfDay))
          iCalEvent.put('dtStartTz', subKey)
        } else if (compoundKey == 'DTEND') {
          iCalEvent.put('dtEndString', value)
          iCalEvent.put('dtEnd', parseDate(value, endTimeOfDay))
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

  // adjust times if there are multiple guests on the same day
  if((earlyCheckin || lateCheckout) && events.size() > 1) {
    if(events[0]['summary'] != 'Not available' && events[1]['summary'] != 'Not available') {
        events[0].put('dtEnd', parseDate(events[0]['dtEndString'], checkoutTime))
        events[0].put('dtStart', parseDate(events[0]['dtStartString'], checkoutTime))
        events[1].put('dtEnd', parseDate(events[1]['dtEndString'], checkoutTime))
        events[1].put('dtStart', parseDate(events[1]['dtStartString'], checkoutTime))
    }
    if (currentEvent(today, events[0])) {
      event = events[0];
    } else if (currentEvent(today, events[1])) {
      event = events[1];
    }
  } else if (events.size() == 1) {
    event = events[0];
  }

  if (event) {
    if (event['summary'] == 'Not available') {
      code = ''
    } else if (event['phone']) {
      code = event['phone'].replaceAll(/\D/, '')[-4..-1]
      debugger("airbnbCalenderCheck: ${event['summary']}, phone: ${event['phone']}, code: ${code}")
    }
  }

  debugger("code: ${code}, atomicState.userCode: ${atomicState.userCode}")
  if (event && ((atomicState.userCode != code) || (state.guestName != event['summary']))) {
    debugger("airbnbCalenderCheck: setting new user code: ${code}")
    state.guestName = event['summary']
    state.eventStart = readableDateTime(event['dtStart'])
    state.eventEnd = readableDateTime(event['dtEnd'])

    atomicState.userCode = code
    setNewCode()
  }
}

Date parseDate(String value, String timeOfDay) {
  def time = timeToday(timeOfDay, timeZone()).format("'T'HH:mm:ss.SSSZ", timeZone())
  def ret = null
  if ( value ==~ /[0-9]*/ ) {
    ret = Date.parse("yyyyMMdd'T'HH:mm:ss.SSSZ", "${value}${time}")
  } else {
    println "WARNING: unknown date format: ${value}"
  }
  return ret
}
