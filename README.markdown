# Lock Manager

Incomplete List of features:

* Manage user codes on locks.
* Review and manage active lock codes.
* Set lock access based on schedules.
* Receive notifications when a user used their code.
* Run Hello Home phrases based on lock/unlock events.
* Manage Hello Home phrases based on keypad inputs.

This app works using 3 child apps and 1 parent app.  It is reccomended that you install all 4 apps, however if you do not use keypad devices, it is not required to install the keypad child app.

**Note:** If upgrading from any beta version, it is required to uninstall the app completly before installing.  The archetecture of this app has changed in
a way that is incompatable with previous versions.

### How To Install
[User Guide](https://dl.dropboxusercontent.com/u/54190708/LockManager/v1/guide.pdf)


##

### Version 1

This app no longer requires a custom device handler, however it does require a device handler that follows best standards.  Some out of date device handlers do not report codes with the correct state attributes.

One I've run into so far is garyd9's DTH for schlage locks, which I have included a copy of in this repo with the corrected event response.  I am not maintaining this code, however it works and I wanted to make it easy for people to install.



### Please donate

Donations are completly optional, but if this made your life easier, please consider donating.

* Paypal- <a href="https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=LDYNH7HNKBWXJ&lc=US&item_name=Lock%20Code%20Manager%20Donation&item_number=40123&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donate_SM%2egif%3aNonHosted"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif" alt="[paypal]" /></a>

* [Google Wallet-](https://www.google.com/wallet/) Send to: thayer.er@gmail.com
