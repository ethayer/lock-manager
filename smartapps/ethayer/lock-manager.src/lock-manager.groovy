definition(
  name: 'Lock Manager',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'Manage locks and users',
  category: 'My Apps',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page(name: 'mainPage', title: 'Users', install: true, uninstall: true,submitOnChange: true)
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'lockUsers', appName: "Lock User", namespace: "ethayer", title: "New User", multiple: true)
    }
    section('Which Locks?') {
      input 'locks', 'capability.lockCodes', title: 'Select Locks', required: true, multiple: true, submitOnChange: true
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
  // nothing needed here, since the child apps will handle preferences/subscriptions
  // this just logs some messages for demo/information purposes
  def children = getChildApps()
  setAccess()
  subscribe(locks, "reportAllCodes", pollCodeReport, [filterEvents:false])

  log.debug "there are ${children.size()} child smartapps"
  childApps.each {child ->
    log.debug "child app: ${child.label}"
  }
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
  if (needPoll) {
    log.debug 'asking for poll!'
    runIn(20, doPoll)
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
