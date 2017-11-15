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
 *  Author: SmartThings, BigRedBrent
 */

definition(
    name: "Enhanced Virtual Thermostat",
    namespace: "BigRedBrent",
    author: "BigRedBrent",
    description: "Control a space heater or window air conditioner in conjunction with any temperature sensor, like a SmartSense Multi.",
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
    section("How far away from the desired temperature before activating..."){
        input "threshold", "decimal", title: "Threshold", defaultValue: 0.1, required: true
    }
    section("Simulated temperature sensor(s) to copy the current temperature to... (not required)"){
        input "simulatedTemperatureSensors", "capability.temperatureMeasurement", title: "Simulated Temperature Sensors", multiple: true, required: false
    }
}

def installed()
{
    subscribeEventHandlers()
}

def updated()
{
    unsubscribe()
    subscribeEventHandlers()
}

private def subscribeEventHandlers()
{
    subscribe(sensor, "temperature", temperatureHandler)
    if (simulatedTemperatureSensors) {
        simulatedTemperatureSensors.setTemperature(sensor.currentValue("temperature"))
    }
    subscribe(thermostat, "thermostatMode", setThermostatTemperatureHandler)
    if (heatOutlets) {
        subscribe(thermostat, "heatingSetpoint", setThermostatTemperatureHandler)
    }
    if (coolOutlets) {
        subscribe(thermostat, "coolingSetpoint", setThermostatTemperatureHandler)
    }
    setThermostatTemperatureHandler()
}

def temperatureHandler(evt)
{
    if (simulatedTemperatureSensors) {
        simulatedTemperatureSensors.setTemperature(evt.value)
    }
    evaluate()
}

def setThermostatTemperatureHandler()
{
    def mode = thermostat.currentValue("thermostatMode")
    if (mode == "auto" && thermostat.currentValue("coolingSetpoint") - thermostat.currentValue("heatingSetpoint") < 4) {
        if (thermostat.currentValue("heatingSetpoint") + 4 <= 95) {
            thermostat.setCoolingSetpoint(thermostat.currentValue("heatingSetpoint") + 4)
        }
        else {
            thermostat.setHeatingSetpoint(91)
            thermostat.setCoolingSetpoint(95)
        }
    }
    if (heatOutlets && mode == "heat") {
        thermostat.setTemperature(thermostat.currentValue("heatingSetpoint"))
    }
    else if (coolOutlets && mode == "cool") {
        thermostat.setTemperature(thermostat.currentValue("coolingSetpoint"))
    }
    else {
        thermostat.setTemperature()
    }
    evaluate()
}

private evaluate()
{
    def mode = thermostat.currentValue("thermostatMode")
    def currentTemp = sensor.currentValue("temperature")
    if (heatOutlets && mode == "heat") {
        if (coolOutlets) {
            coolOutlets.off()
        }
        def setpoint = thermostat.currentValue("heatingSetpoint")
        if (currentTemp >= setpoint) {
            heatOutlets.off()
        }
        else if (currentTemp < setpoint - threshold) {
            heatOutlets.on()
        }
    }
    else if (coolOutlets && mode == "cool") {
        if (heatOutlets) {
            heatOutlets.off()
        }
        def setpoint = thermostat.currentValue("coolingSetpoint")
        if (currentTemp <= setpoint) {
            coolOutlets.off()
        }
        else if (currentTemp > setpoint + threshold) {
            coolOutlets.on()
        }
    }
    else {
        if (coolOutlets) {
            coolOutlets.off()
        }
        if (heatOutlets) {
            heatOutlets.off()
        }
    }
}