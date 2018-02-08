def lockPage() {
  dynamicPage(name: "lockPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      setAsLockPage
    }
  }
}

def setAsLockPage
  state.appType = 'lock'
end
