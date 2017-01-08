definition(
  name: 'Lock Manager',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'Manage locks and users',
  category: 'Safety & Security',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page(name: 'mainPage', title: 'Users', install: true, uninstall: true,submitOnChange: true)
  page(name: "lockInfoPage")
  page(name: "infoRefreshPage")
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'lockUsers', appName: "Lock User", namespace: "ethayer", title: "New User", multiple: true)
    }
    section('Locks') {
      if (locks) {
        def i = 0
        locks.each { lock->
          i++
          href(name: "toLockInfoPage${i}", page: "lockInfoPage", params: [id: lock.id], required: false, title: lock.displayName )
        }
      }
    }
    section('Global Settings') {
      input 'locks', 'capability.lockCodes', title: 'Select Locks', required: true, multiple: true, submitOnChange: true
      input(name: "overwriteMode", title: "Overwrite?", type: "bool", required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      href(name: "toInfoRefreshPage", page: "infoRefreshPage", title: "Refresh Lock Data", description: 'Tap to refresh')
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
          state."lock${lock.id}".codes.each { code->
            i++
            child = findAssignedChildApp(lock, i)
            setCode = state."lock${lock.id}".codes."slot${i}"
            para = "Slot ${i}\nCode: ${setCode}"
            if (child) {
              para = para + child.getLockUserInfo(lock)
            }
            paragraph para

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
  setAccess()
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
  def children = getChildApps()
  log.debug 'checking children for errors'
  children.each { child ->
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
      if (currentCode != '') {
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

// def doErrorPoll() {
//   def needPoll = false
//   def children = getChildApps()
//   def pollThese = []
//   children.each { child ->
//     if (child.isInErrorLoop()) {
//       pollThese << child.errorLoopArray()
//     }
//   }
//   log.debug pollThese
//   if (pollThese != []) {
//     runIn(25, doErrorPoll)
//   }
// }

def setAccess() {
  def children = getChildApps()
  def userArray
  def json
  locks.each { lock ->
    userArray = []
    children.each { child ->
      if (child.isActive(lock.id)) {
        userArray << ["code${child.userSlot}", "${child.userCode}"]
      } else {
        userArray << ["code${child.userSlot}", ""]
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

def populateDiscovery(codeData, lock) {
  def codes = [:]
  def codeSlots = 30
  if (codeData.codes) {
    codeSlots = codeData.codes
  }
  (1..codeSlots).each { slot->
    codes."slot${slot}" = codeData."code${slot}"
  }
  state."lock${lock.id}".codes = codes
}

def findAssignedChildApp(lock, slot) {
  def children = getChildApps()
  def childApp = false
  children.each { child ->
    if (child.userSlot?.toInteger() == slot) {
      childApp = child
    }
  }
  return childApp
}
