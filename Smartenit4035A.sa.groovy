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
 *	SmartPower Outlet (CentraLite)
 *
 *	Author: SmartThings
 *	Date: 2015-08-23
 */
 import groovy.transform.Field
    
 @Field final ElecMeasCluster = "0B04"
 @Field final RMSVoltageAttribute = "0505"
 @Field final RMSCurrentAttribute = "0805"
 @Field final ActivePowerAttribute = "0B05"
 @Field final PowerFactorAttribute = "1005"
 @Field final UnsignedInteger16 = "21" 
 @Field final SignedInteger16 = "29"
 @Field final SignedInteger8 = "28"
 @Field final Milli = 0.001
 @Field final Centi = 0.01
 
metadata {
	// Automatically generated. Make future change here.
	definition (name: "Smartenit Outlet", namespace: "Jjbasso/smartenit4035A", author: "Jeff Basso") {
		capability "Actuator"
		capability "Switch"
		capability "Power Meter"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"

		// indicates that device keeps track of heartbeat (in state.heartbeat)
		attribute "heartbeat", "string"
		
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0015,0702", manufacturer: "smartenit",  model: "4035A", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0015,0702"
        
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
	log.debug "description is $description"

	// save heartbeat (i.e. last time we got a message from device)
	state.heartbeat = Calendar.getInstance().getTimeInMillis()

	def finalResult = zigbee.getKnownDescription(description)

	//TODO: Remove this after getKnownDescription can parse it automatically
	if (!finalResult && description!="updated")
		finalResult = getPowerDescription(zigbee.parseDescriptionAsMap(description))

	if (finalResult) {
		log.info finalResult
		if (finalResult.type == "update") {
			log.info "$device updates: ${finalResult.value}"
		}
		else if (finalResult.type == "power") {
			def powerValue = (finalResult.value as Integer)
			sendEvent(name: "power", value: powerValue)
		}
		else {
			sendEvent(name: finalResult.type, value: finalResult.value)
		}
	}
	else {
		//log.warn "DID NOT PARSE MESSAGE for description : $description"
		//log.debug zigbee.parseDescriptionAsMap(description)
        log.info "HA Electrical Measurement Specification : $description"
        finalResult = getPowerDescription(zigbee.parseDescriptionAsMap(description))
        if (finalResult.type == "power") {
        	def powerValue = (finalResult.value as Integer)
			sendEvent(name: "power", value: powerValue)
		}
        else {
            log.warn "Description not parsed for HA Electrical Measurement Specification"
    	}
	}
}

def off() {
	zigbee.off()
}

def on() {
	zigbee.on()
}

def refresh() {
	sendEvent(name: "heartbeat", value: "alive", displayed:false)
	//zigbee.onOffRefresh() + zigbee.refreshData("0x0B04", "0x050B")
    zigbee.onOffRefresh() + zigbee.electricMeasurementPowerRefresh() + zigbee.refreshData("0x0B04", "0x050B")
}

def configure() {
	//zigbee.onOffConfig() + powerConfig() + refresh()
     zigbee.onOffConfig() + powerConfig() + zigbee.electricMeasurementPowerConfig() + Refresh()
}

//power config for devices with min reporting interval as 1 seconds and reporting interval if no activity as 10min (600s)
//min change in value is 01
def powerConfig() {
	[
		"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 0x0B04 {${device.zigbeeId}} {}", "delay 200",
		"zcl global send-me-a-report 0x0B04 0x050B 0x29 1 600 {05 00}",				//The send-me-a-report is custom to the attribute type for CentraLite
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
    
    if (descMap.cluster == ElecMeasCluster) {
		if (descMap.attrId == RMSVoltageAttribute) {
        	def RMSVoltageValue = FindAttrib(descMap.raw, RMSVoltageAttribute)
			def RMSCurrentValue = FindAttrib(descMap.raw, RMSCurrentAttribute)
			def PowerFactorValue= FindAttrib(descMap.raw, PowerFactorAttribute)
			if ((RMSVoltageValue != 'error') || (RMSCurrentValue != 'error') || (PowerFactorValue != 'error')){
    			powerValue = RMSVoltageValue * RMSCurrentValue * Milli * PowerFactorValue * Centi
        	}
		}
	}

	if (powerValue != "undefined"){
		return	[type: "power", value : powerValue]
	}
	else {
		return [:]
	}
}

def FindAttrib(descMap, attrib){
    def dtStartPoint = descMap.indexOf(attrib) + attrib.length()
    def dtValue = descMap.substring(dtStartPoint, dtStartPoint + 2)
    def AttribValueStartPoint = descMap.indexOf(attrib) + attrib.length() + 2
    def AttribValue
    
    if(dtValue == UnsignedInteger16){ 
       AttribValue = descMap.substring(AttribValueStartPoint ,AttribValueStartPoint + 4)         
       AttribValue = "${AttribValue.substring(2,4)}${AttribValue.substring(0,2)}".toString() 
       AttribValue  = Long.parseLong(AttribValue , 16);       
       if(AttribValue == 'ffff'){ AttribValue = "error"}
    }
    else if(dtValue == SignedInteger16){
       AttribValue = descMap.substring(AttribValueStartPoint ,AttribValueStartPoint + 4)         
       AttribValue = "${AttribValue.substring(2,4)}${AttribValue.substring(0,2)}".toString()
       AttribValue  = Long.parseLong(AttribValue , 16);
       if(AttribValue == '8000'){ AttribValue = "error"}    
    }
    else if(dtValue == SignedInteger8){
       AttribValue = descMap.substring(AttribValueStartPoint ,AttribValueStartPoint + 2)         
     AttribValue  = Long.parseLong(AttribValue , 16);      
        if(AttribValue == '80'){ AttribValue = "error"}    
    }
    else{
       AttribValue = 'error'
    }
    return AttribValue 
}    

