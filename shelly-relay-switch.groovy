/**
 *
 *  Shelly Switch Relay Driver
 *
 *  Copyright © 2019 Scott Grayban
 *
 * Please Note: This app is NOT released under any open-source license.
 * Please be sure to read the license agreement before installing this code.
 *
 * This software package is created and licensed by Scott Grayban.
 *
 * This software, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * You may not distribute any part of this software without the author's express permission
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forth
 * in the Software license agreement.
 * This agreement can be found on-line at: https://sgrayban.github.io/Hubitat-Public/software_License_Agreement.txt
 * 
 * Hubitat is the Trademark and intellectual Property of Hubitat Inc.
 * Shelly is the Trademark and Intellectual Property of Allterco Robotics Ltd
 * Scott Grayban has no formal or informal affiliations or relationships with Hubitat or Allterco Robotics Ltd.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 * See all the Shelly Products at https://shelly.cloud/
 *
 *
 * Shelly http POST at http://IP/relay/0 ( can be 0/2 for the 2.5 or 0/1/2/3/4 for shelly 4Pro) with body form-urlencoded:
 *   turn=on
 *   turn=off
 *   ison=boolean
 *
 *  Changes:
 *  2.0.4 - Code added for all Shelly relay switches
 *  2.0.3 - Changed operand for refresh rate in update()
 *  2.0.2 - Changed how the update check worked if refresh rate was set to No Selection
 *  2.0.1 - Modified code to allow install more then once - added version control
 *  2.0.0 - Removed more ST code and added auto refresh option and debugging info switches.
 *  1.0.0 - Initial port
 *
 */

import groovy.json.*

metadata {
	definition (
		name: "Shelly Relay Switch Versione ok",
		namespace: "sgrayban",
		author: "Scott Grayban",
		importUrl: "https://raw.githubusercontent.com/sgrayban/Hubitat-Ports/master/Drivers/Shelly/Shelly-as-a-Switch.groovy"
		)
	{
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Switch"
        capability "Polling"
        capability "PowerMeter"
        
        command "refreshMeter"
        
        attribute "switch", "string"
        attribute "ShellyfwUpdate", "string"
        attribute "power", "string"
        attribute "overpower", "string"
        attribute "dcpower", "string"
        attribute "max_power", "string"
        attribute "internal_tempC", "string"

	}

   tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOn", defaultState: true
                attributeState "turningOn", label:'Turning On', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOn"
                attributeState "turningOff", label:'Turning Off', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOff"
            }
        }

        standardTile("explicitOn", "device.switch", width: 2, height: 2, decoration: "flat") {
            state "default", label: "On", action: "switch.on", icon: "st.Home.home30", backgroundColor: "#ffffff"
        }
        standardTile("explicitOff", "device.switch", width: 2, height: 2, decoration: "flat") {
            state "default", label: "Off", action: "switch.off", icon: "st.Home.home30", backgroundColor: "#ffffff"
        }

        standardTile("refresh", "device.refresh", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh", nextState: "disabled"
            state "disabled", label:'', action:"", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "explicitOn", "explicitOff","refresh"])

    }

	preferences {
	def refreshRate = [:]
		refreshRate << ["1 min" : "Refresh every minute"]
		refreshRate << ["5 min" : "Refresh every 5 minutes"]
		refreshRate << ["15 min" : "Refresh every 15 minutes"]
		refreshRate << ["30 min" : "Refresh every 30 minutes"]
	input("ip", "string", title:"IP", description:"Shelly IP Address", defaultValue:"" , required: false, displayDuringSetup: true)
    input "deviceHasMeter", "bool", title: "Is the device a Shelly 2.5/4Pro:", description:" ", defaultValue: false, displayDuringSetup: true
	input("channel", "number", title:"Relay Channel", description:"0,1,2,or 3 :", defaultValue:"0" , required: false, displayDuringSetup: true)
    input ("refresh_Rate", "enum", title: "Device Refresh Rate", options: refreshRate, defaultValue: "30 min")
	input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"]
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	input name: "Shellyinfo", type: "text", title: "<center><font color=blue>Info Box</font><br>Shelly API docs are located at:</center>", description: "<center><br>http://shelly-api-docs.shelly.cloud/</center>", required: false
	}
}

def installed() {
    log.debug "Installed"
}

// App Version   *********************************************************************************
def setVersion(){
	state.Version = "2.0.5"
	state.InternalName = "ShellyAsASwitch"

	sendEvent(name: "DriverAuthor", value: "sgrayban")
	sendEvent(name: "DriverVersion", value: state.Version)
	sendEvent(name: "DriverStatus", value: state.Status)
}

def updated() {
    log.debug "Updated"
    log.info "Preferences updated..."
    log.warn "Debug logging is: ${debugOutput == true}"
    unschedule()

    switch(refresh_Rate) {
		case "1 min" :
			runEvery1Minute(autorefresh)
			break
		case "5 min" :
			runEvery5Minutes(autorefresh)
			break
		case "15 min" :
			runEvery15Minutes(autorefresh)
			break
		default:
			runEvery30Minutes(autorefresh)
	}
	if (txtEnable) log.info ("Auto Refresh set for every ${refresh_Rate} minute(s).")

    if (debugOutput) runIn(1800,logsOff)
    state.LastRefresh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone)
    
    version()
    refresh()
}

private dbCleanUp() {
	state.remove("version")
	state.remove("Version")
	state.remove("ShellyfwUpdate")
	state.remove("power")
	state.remove("overpower")
	state.remove("dcpower")
	state.remove("max_power")
	state.remove("internal_tempC")
}

def parse(description) {
    logDebug "Parsing result $description"
	
	def msg = parseLanMessage(description)
	def headerString = msg.header
    def headerMap = msg.headers      // => headers as a Map
	def bodyString   = msg.body
    def data = msg.data

	logDebug "received body:\n${bodyString}"
	
	if(bodyString.trim() == "ok")
	return
	
	def json = null;
	try{
	json = new groovy.json.JsonSlurper().parseText(bodyString)
		logDebug "${json}"
		
		if(json == null){
	logDebug "body object not parsed"
			return
		}
	}
	catch(e){
	log.error("Failed to parse json e = ${e}")
		return
	}

	logDebug "JSON '${json}'"
    
    def evt1 = null

	if (json.containsKey("mode")) {
	sendEvent(name: "mode", value: json.mode)
	}
	if (json.containsKey("power")) {
    if (deviceHasMeter) sendEvent(name: "power", value: json.power)
    }
	if (json.containsKey("dcpower")) {
    sendEvent(name: "dcpower", value: json.dcpower)
    }
	if (json.containsKey("max_power")) {
	sendEvent(name: "max_power", value: json.max_power)
	}
    if (json.containsKey("temperature")) {
	sendEvent(name: "internal_tempC", value: json.temperature)
	}
	if (json.containsKey("overpower")) {
	sendEvent(name: "overpower", value: json.overpower)
	}
    
    if (data.has_update == true) {
        if (txtEnable) log.info "NEW SHELLY FIRMWARE"
        evt1 = sendEvent(name: "FW_Update_Needed", value: "FIRMWARE Update Required")
    }
    if (data.has_update == false) {
        if (txtEnable) log.info "No Shelly FW update needed"
        evt1 = sendEvent(name: "FW_Update_Needed", value: "No")
    }
 
    if (data.ison == true) {
        if (txtEnable) log.info "sendEvent ison=true"
        evt1 = sendEvent(name: "switch", value: "on", displayed: false)
    }
    if (data.ison == false) {
        if (txtEnable) log.info "sendEvent ison=false"
        evt1 = sendEvent(name: "switch", value: "off", displayed: false)
    }
    return evt1
}

//switch.on
def on() {
    logDebug "Executing switch.on"
    sendSwitchCommand "turn=on"
}

//switch.off
def off() {
    logDebug "Executing switch.off"
    sendSwitchCommand "turn=off"
}

def ping() {
	logDebug "ping"
	poll()
}

def initialize() {
	log.info "initialize"
	if (txtEnable) log.info "initialize" //RK
	refresh()
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def autorefresh() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'auto refresh'" //RK

    refresh()
}

def refresh() {
    logDebug "Refresh - Getting Status"
    dbCleanUp()
    sendHubCommand(new physicalgraph.device.HubAction(
      method: "GET",
      path: "/relay/" + channel,
      headers: [
        HOST: getShellyAddress(),
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
    refreshStats()
    refreshMeter()
}

def refreshStats() {
    logDebug "Refresh - Getting Status"
//    dbCleanUp()
      sendHubCommand(new physicalgraph.device.HubAction(
      method: "GET",
      path: "/status",
      headers: [
        HOST: getShellyAddress(),
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
}

def refreshMeter() {
    if (deviceHasMeter == false) { log.info ("Meter Status not available for this device") ;return }
    logDebug "Refresh - Getting Settings"
//    dbCleanUp()
      sendHubCommand(new physicalgraph.device.HubAction(
      method: "GET",
      path: "/meter/" + channel,
      headers: [
        HOST: getShellyAddress(),
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "$msg"
	}
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
	state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
	state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
	sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'" //RK
	refresh()
//    refreshStats()
//    refreshSettings()
}

def sendSwitchCommand(action) {
    if (txtEnable) log.info "Calling /relay/ with $action"
    sendHubCommand(new physicalgraph.device.HubAction(
      method: "POST",
      path: "/relay/" + channel,
      body: action,
      headers: [
        HOST: getShellyAddress(),
        "Content-Type": "application/x-www-form-urlencoded"
      ]
    ))
    refresh()
}

private getShellyAddress() {
    def port = 80
    def iphex = ip.tokenize( '.' ).collect { String.format( '%02x', it.toInteger() ) }.join().toUpperCase()
    def porthex = String.format('%04x', port.toInteger())
    def shellyAddress = iphex + ":" + porthex
    if (txtEnable) log.info "Using IP " + ip + ", PORT 80 and HEX ADDRESS " + shellyAddress + " for device: ${device.id}"
	return shellyAddress
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (github CobraVmax) for his original code **************
def version(){
	updatecheck()
	schedule("0 0 18 1/1 * ? *", updatecheck) // Cron schedule
//	schedule("0 0/1 * 1/1 * ? *", updatecheck) // Test Cron schedule
}

def updatecheck(){
	setVersion()
	 def paramsUD = [uri: "https://sgrayban.github.io/Hubitat-Public/version.json"]
	  try {
			httpGet(paramsUD) { respUD ->
				  if (txtEnable) log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
				  def copyrightRead = (respUD.data.copyright)
				  state.Copyright = copyrightRead
				  def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
				  def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
				  def currentVer = state.Version.replace(".", "")
				  state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
				  state.author = (respUD.data.author)
				  state.icon = (respUD.data.icon)
				  if(newVer == "NLS"){
					   state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
					   log.warn "** This driver is no longer supported by $state.author **"      
				  }           
				  else if(currentVer < newVer){
					   state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
					   log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
					   log.warn "** $state.UpdateInfo **"
				 } 
				 else if(currentVer > newVer){
					   state.Status = "<b>You are using a Test version of this Driver (Version: $newVerRaw)</b>"
				 }
				 else{ 
					 state.Status = "Current"
					 log.info "You are using the current version of this driver"
				 }
			} // httpGet
	  } // try

	  catch (e) {
		   log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
	  }

	  if(state.Status == "Current"){
		   state.UpdateInfo = "Up to date"
		   sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }
	  else {
		   sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
		   sendEvent(name: "DriverStatus", value: state.Status)
	  }

	  sendEvent(name: "DriverAuthor", value: "sgrayban")
	  sendEvent(name: "DriverVersion", value: state.Version)
}