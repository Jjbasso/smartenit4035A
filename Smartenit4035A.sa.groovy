/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	 Smartenit Outlet (#4035A)
 *
 *	Author: Jeff Basso
 *	Date: 2015-11-21
 */
metadata {
	definition (name: "Smartenit Outlet", namespace: "jjbasso", author: "Jeff Basso") {
		capability "Actuator"		// http://docs.smartthings.com/en/latest/capabilities-reference.html#actuator
		capability "Switch" 		// http://docs.smartthings.com/en/latest/capabilities-reference.html#relay-switch
		capability "Power Meter" 	// http://docs.smartthings.com/en/latest/capabilities-reference.html#power-meter
		capability "Configuration"	// http://docs.smartthings.com/en/latest/capabilities-reference.html#configuration
		capability "Refresh"		// http://docs.smartthings.com/en/latest/capabilities-reference.html#refresh
		capability "Sensor"			// http://docs.smartthings.com/en/latest/capabilities-reference.html#sensor

		// indicates that device keeps track of heartbeat (in state.heartbeat)
		attribute "heartbeat", "string"

		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0015,0702", manufacturer: "smartenit",  model: "4035A", deviceJoinName: "Outlet"
        
      //  0x0000:Basic Attributes for determining basic information and setting and enabling device
      //  0x0003:Identify Attributes and commands for putting a device into Identification mode
      //  0x0004:Groups Attributes and commands for group configuration and manipulation
	  //  0x0005:Scenes Attributes and commands for scene configuration and manipulation
      //  0x0006:On/Off Attributes and commands for switching device.
      //  0x0015:Commission Attributes and commands pertaining to the commissioning and management of ZigBee devices operating in a network
      //  0x0702:Simple Metering Provides mechanism to retrieve electric power usage
        
	}

	// simulator metadata
	simulator {
		// status messages
		status "on": "on/off: 1"
		status "off": "on/off: 0"

		// reply messages
		reply "zcl on-off on": "on/off: 1"
		reply "zcl on-off off": "on/off: 0"
	}

	preferences {
		section {
			image(name: 'educationalcontent', multiple: true, images: [
				"http://cdn.device-gse.smartthings.com/Outlet/US/OutletUS1.jpg",
				"http://cdn.device-gse.smartthings.com/Outlet/US/OutletUS2.jpg"
				])
		}
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "turningOff"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "turningOff"
				attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
			}
			tileAttribute ("power", key: "SECONDARY_CONTROL") {
				attributeState "power", label:'${currentValue} W'
			}
		}

		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "switch"
		details(["switch","refresh"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.info "****************** Begin Parse ******************"

	// save heartbeat (i.e. last time we got a message from device)
	state.heartbeat = Calendar.getInstance().getTimeInMillis()

	def finalResult = zigbee.getKnownDescription(description)
	  
	
    //TODO: Remove this after getKnownDescription can parse it automatically
	if (!finalResult && description!="updated")
		finalResult = getPowerDescription(zigbee.parseDescriptionAsMap(description))
	
        
	if (finalResult) {
    log.info finalResult
		if (finalResult.type == "power") {
        	log.info "Raw: $description"
			def powerValue = (finalResult.value as Integer)
			sendEvent(name: "power", value: powerValue)
		}
		else {
			sendEvent(name: finalResult.type, value: finalResult.value)
        }
	}
	else {
		log.warn "DID NOT PARSE MESSAGE for description : $description"
        finalResult = getPowerDescription(zigbee.parseDescriptionAsMap(description))
    }
	log.info "****************** End Parse ******************"
}

def off() {
	zigbee.off()
}

def on() {
	zigbee.on()
}

def refresh() {
	//Tested:jbasso 11-21-2015 - Clean
	sendEvent(name: "heartbeat", value: "alive", displayed:false)
    zigbee.onOffRefresh() + zigbee.electricMeasurementPowerRefresh()
}

def configure() {
	log.debug "Configuring Reporting and Bindings."
    zigbee.onOffConfig() + zigbee.electricMeasurementPowerConfig()
}

//power config for devices with min reporting interval as 1 seconds and reporting interval if no activity as 10min (600s)
//min change in value is 01
def powerConfig() {
	[
		"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 0x0B04 {${device.zigbeeId}} {}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500"
	]
}

private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}

//TODO: Remove this after getKnownDescription can parse it automatically
def getPowerDescription(descMap) {
	def powerValue = "undefined"
	if (descMap.cluster == "0B04") {
		if (descMap.attrId == "050b") {
			if(descMap.value!="ffff")
				powerValue = zigbee.convertHexToInt(descMap.value)
		}
	}
	else if (descMap.clusterId == "0B04") {
		if(descMap.command=="07"){
			return	[type: "update", value : "power (0B04) capability configured successfully"]
		}
	}
    if (descMap.cluster == "0B04") {
		if (descMap.attrId == "0505") {
        log.info "Parsed Data: $descMap"
			//parse code here
		}
	}
      

	if (powerValue != "undefined"){
		return	[type: "power", value : powerValue]
	}
	else {
		return [:]
	}
}

      

