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
 *  Enhanced Virtual Thermostat
 *
 *  Author: BigRedBrent
 */

definition(
    name: "Enhanced Virtual Thermostat",
    namespace: "BigRedBrent",
    author: "BigRedBrent",
    description: "Control a space heater or window air conditioner using any temperature sensor with a Simulated Thermostat.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png"
)

preferences {
    section("The simulated thermostat that will be used to control the Virtual Thermostat..."){
        input "thermostat", "capability.thermostat", title: "Simulated Thermostat", required: true
    }
    section("Choose the temperature sensor that will be used... "){
        input "sensor", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true
    }
    section("Select heater outlet(s)... "){
        input "heatOutlets", "capability.switch", title: "Heater Outlets", multiple: true, required: false
    }
    section("Select air conditioner outlet(s)... "){
        input "coolOutlets", "capability.switch", title: "Air Conditioner Outlets", multiple: true, required: false
    }
    section("Select emergency heater outlet(s)... "){
        input "emergencyHeatOutlets", "capability.switch", title: "Emergency Heater Outlets", multiple: true, required: false
    }
    section("How far away from the desired temperature before activating..."){
        input "threshold", "decimal", title: "Threshold", defaultValue: 0.1, required: true
    }
    section("Dimmer Switch(s) for optional control of the simulated thermostat... (not required)"){
        input "dimmerSwitchs", "capability.switchLevel", title: "Simulated Dimmer Switch", multiple: true, required: false
    }
    section("Simulated temperature sensor(s) to copy the current temperature to... (not required)"){
        input "simulatedTemperatureSensors", "capability.temperatureMeasurement", title: "Simulated Temperature Sensors", multiple: true, required: false
    }
}

def installed() {
    runUpdate()
}

def updated() {
    unsubscribe()
    runUpdate()
}

private def runUpdate() {
    subscribeEventHandlers()
    setThermostatTemperature()
    if (simulatedTemperatureSensors) {
        simulatedTemperatureSensors.setTemperature(sensor.currentValue("temperature"))
    }
    if (heatOutlets || coolOutlets || emergencyHeatOutlets) {
        evaluate()
    }
}

private def subscribeEventHandlers() {
    if (heatOutlets || coolOutlets || emergencyHeatOutlets) {
        subscribe(sensor, "temperature", temperatureHandler)
        subscribe(thermostat, "thermostatMode", setpointHandler)
        if (heatOutlets || emergencyHeatOutlets) {
            subscribe(thermostat, "heatingSetpoint", setpointHandler)
        }
        if (coolOutlets) {
            subscribe(thermostat, "coolingSetpoint", setpointHandler)
        }
        if (dimmerSwitchs) {
            subscribe(dimmerSwitchs, "level", levelHandler)
        }
    } else if (simulatedTemperatureSensors) {
        subscribe(sensor, "temperature", temperatureHandler)
    }
}

def temperatureHandler(evt) {
    if (simulatedTemperatureSensors) {
        simulatedTemperatureSensors.setTemperature(evt.value)
    }
    if (heatOutlets || coolOutlets || emergencyHeatOutlets) {
        evaluate()
    }
}

def setpointHandler(evt) {
    setThermostatTemperature()
    evaluate()
}

def levelHandler(evt) {
    def level = evt.value
    def mode = thermostat.currentValue("thermostatMode")
    if (level > 95) {
        level = 95
    } else if (level < 35){
        level = 35
    }
    if (mode == "heat" || mode == "emergency heat") {
        thermostat.setHeatingSetpoint(level)
    } else if (mode == "cool") {
        thermostat.setCoolingSetpoint(level)
    } else {
        if (level > 93) {
            level = 93
        } else if (level < 37){
            level = 37
        }
        thermostat.setHeatingSetpoint(level - 2)
        thermostat.setCoolingSetpoint(level + 2)
    }
}

private def setThermostatTemperature() {
    def mode = thermostat.currentValue("thermostatMode")
    def currentTemp = sensor.currentValue("temperature")
    def heatingSetpoint = thermostat.currentValue("heatingSetpoint")
    def coolingSetpoint = thermostat.currentValue("coolingSetpoint")
    if (mode == "auto" && coolingSetpoint - heatingSetpoint < 4) {
        if (heatingSetpoint + 4 <= 95) {
            coolingSetpoint = heatingSetpoint + 4
            unsubscribe()
            thermostat.setCoolingSetpoint(coolingSetpoint)
            subscribeEventHandlers()
        } else {
            heatingSetpoint = 91
            coolingSetpoint = 95
            unsubscribe()
            thermostat.setHeatingSetpoint(heatingSetpoint)
            thermostat.setCoolingSetpoint(coolingSetpoint)
            subscribeEventHandlers()
        }
    }
    if ((mode == "heat" && heatOutlets) || (mode == "emergency heat" && emergencyHeatOutlets) || (mode == "auto" && heatOutlets && (!coolOutlets || currentTemp - heatingSetpoint <= coolingSetpoint - currentTemp))) {
        thermostat.setTemperature(heatingSetpoint)
    } else if ((mode == "cool" || mode == "auto") && coolOutlets) {
        thermostat.setTemperature(coolingSetpoint)
    } else {
        thermostat.setTemperature()
    }
}

private evaluate() {
    def mode = thermostat.currentValue("thermostatMode")
    def currentTemp = sensor.currentValue("temperature")
    def heatingSetpoint = thermostat.currentValue("heatingSetpoint")
    def coolingSetpoint = thermostat.currentValue("coolingSetpoint")
    if (mode == "heat" && heatOutlets) {
        if (coolOutlets) {
            coolOutlets.off()
        }
        if (emergencyHeatOutlets) {
            emergencyHeatOutlets.off()
        }
        if (currentTemp >= heatingSetpoint) {
            heatOutlets.off()
        } else if (currentTemp < heatingSetpoint - threshold) {
            heatOutlets.on()
        }
    } else if (mode == "cool" && coolOutlets) {
        if (heatOutlets) {
            heatOutlets.off()
        }
        if (emergencyHeatOutlets) {
            emergencyHeatOutlets.off()
        }
        if (currentTemp <= coolingSetpoint) {
            coolOutlets.off()
        } else if (currentTemp > coolingSetpoint + threshold) {
            coolOutlets.on()
        }
    } else if (mode == "emergency heat" && emergencyHeatOutlets) {
        if (heatOutlets) {
            heatOutlets.off()
        }
        if (coolOutlets) {
            coolOutlets.off()
        }
        if (currentTemp >= heatingSetpoint) {
            emergencyHeatOutlets.off()
        } else if (currentTemp < heatingSetpoint - threshold) {
            emergencyHeatOutlets.on()
        }
    } else if (mode == "auto" && (coolOutlets || heatOutlets)) {
        if (emergencyHeatOutlets) {
            emergencyHeatOutlets.off()
        }
        if (heatOutlets) {
            if (currentTemp >= heatingSetpoint) {
                heatOutlets.off()
            } else if (currentTemp < heatingSetpoint - threshold) {
                heatOutlets.on()
            }
        }
        if (coolOutlets) {
            if (currentTemp <= coolingSetpoint) {
                coolOutlets.off()
            } else if (currentTemp > coolingSetpoint + threshold) {
                coolOutlets.on()
            }
        }
    } else {
        if (heatOutlets) {
            heatOutlets.off()
        }
        if (coolOutlets) {
            coolOutlets.off()
        }
        if (emergencyHeatOutlets) {
            emergencyHeatOutlets.off()
        }
    }
}