# org.openhab.binding.xiaomigateway

This binding brings Xiaomi Gateway Smart Home devices (Aqara) integration with OpenHAB1.x
Currently only one gateway is supported.

Supported devices:
- gateway light (including color and brightness change)
- magnet sensor (door sensor)
- motion sensor
- temperature & humidity sensor
- button (simple round switch)
- plug (zigbee version, reporting of power consumed, power load, state and if device in use)
- 86plug (reporting of power consumed, state and power load)
- natgas & smoke sensors (alarm integer events - see below)
- switch 86sw1/2 (no controlling, only getting events click, double click & both click)
- magic cube (all events)

Alarm events (natgas & smoke)
- 0: Release alarm
- 1: Fire alarm 
- 2: Analog alarm 
- 8: Battery fault alarm (smoke alarm only) 
- 64: Sensitivity fault alarm 
- 32768: IIC communication failure

Based on info found here: https://github.com/louisZL/lumi-gateway-local-api

___You need to enable developer mode in Mi Home app (both Android/iOS versions work) !!!___
Please see https://github.com/fooxy/homeassistant-aqara/wiki/Enable-dev-mode

# build
copy __org.openhab.binding.xiaomigateway__ directory to __binding__ directory of OpenHAB source code (https://github.com/openhab/openhab)

build using maven (mvn clean install)

# install
copy target file __org.openhab.binding.xiaomigateway*.jar__ to __addons__ directory of OpenHAB distribution

# usage
The binding detects a Xiaomi gateway on local network and lists all sub devices. For example:
```
2016-12-24 00:29:27.679 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Discovered Xiaomi Gateway - sid: f1b5299a55e5 ip: 192.168.2.187 port: 9898
2016-12-24 00:29:27.681 [INFO ] [.service.AbstractActiveService] - XiaomiGateway Refresh Service has been started
2016-12-24 00:29:28.084 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Discovered total of 3 Xiaomi smart devices
2016-12-24 00:29:28.089 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d0001182814 model: sensor_ht
2016-12-24 00:29:28.093 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d0000f9a538 model: switch
2016-12-24 00:29:28.094 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d00010e4104 model: magnet
```

sid is a sub device identificator used in item configuration file.
- possible sensor values are: magnet, motion, temperature, humidity, voltage
- possible button values are: virtual_switch (button simulates ON/OFF switch), click, long_click, double_click, voltage
- possible switch values are: click, double_click, both_click, voltage
- possible cube values are: flip90, flip180, move, tap_twice, shake_air, swing, alert, free_fall, rotate_left, rotate_right, voltage (ON command is received when an event fired)  
- possible plug values are: plug, inuse, power_consumed, load_power

#openhab.cfg
If you want to control devices please supply a developer key (you can see it in Mi Home app when you enable developer mode)
If you want to change default startup color, please include startConfig configuration (e.g. xiaomigateway:startColor=1677786880).
```
xiaomigateway:key=

//Default startColor=1677786880
xiaomigateway:startColor=
```

#items file
```
Switch  XiaomiSwitch "Xiaomi button" { xiaomigateway="158d0000f9a538.virtual_switch" }
Switch  XiaomiClick "Xiaomi button click" { xiaomigateway="158d0000f9axyz.click" }
Switch  XiaomiLongClick "Xiaomi button long" { xiaomigateway="158d0000f9axyz.long_click" }
Switch  XiaomiLongClickRelease "Xiaomi button long release" { xiaomigateway="158d0000f9axyz.long_click_release" }
Switch  XiaomiDoubleClick "Xiaomi button double" { xiaomigateway="158d0000f9axyz.double_click" }
Contact XiaomiContact "Xiaomi contact" { xiaomigateway="158d00010e4104.magnet" }
Contact XiaomiMotion "Xiaomi motion" { xiaomigateway="158d00010e4105.motion" }
Number  RoomTemperature "Temperature  [%.1f °C]" <temperature>	{ xiaomigateway="158d0001182814.temperature" }
Number  RoomHumidity "Humidity  [%.1f %%]" <humidity>	{ xiaomigateway="158d0001182814.humidity" }
Number  RoomSensorVoltage "Sensor voltage [%.0f mV]" { xiaomigateway="158d0001182814.voltage" }
Switch  XiaomiGatewayLight "Gateway light" { xiaomigateway="f1b5299a55e5.light" }
Number  XiaomiGatewayIllumination "Gateway illumination [%d]" { xiaomigateway="f1b5299a55e5.illumination" }
Color   XiaomiGatewayLightColor "Gateway light color" { xiaomigateway="f1b5299a55e5.color" }
Dimmer  XiaomiGatewayBrightness "Gateway brightness" { xiaomigateway="f1b5299a55e5.brightness" }
Switch  XiaomiPlug "Xiaomi zigbee plug" { xiaomigateway="158d00012944b3.plug" }
Switch  XiaomiPlugInUse "Xiaomi zigbee plug in use" { xiaomigateway="158d00012944b3.inuse" }
Number  XiaomiPlugConsumed "Xiaomi zigbee plug consumed [%.0f Wh]" { xiaomigateway="158d00012944b3.power_consumed" }
Number  XiaomiPlugLoad "Xiaomi zigbee plug load [%.0f W]" { xiaomigateway="158d00012944b3.load_power" }
Number  XiaomiSmokeAlarm "Smoke Alarm [%d]" { xiaomigateway="f1b5299a66e5.alarm" }
Number  XiaomiNatgasAlarm "Natgas Alarm [%d]" { xiaomigateway="f1b5212e78e4.alarm" }

//only getting event values, no remote control
Switch  XiaomiControl0 "Xiaomi CH0 click" { xiaomigateway="158d0000f9defg.channel_0.click" }
Switch  XiaomiControl0D "Xiaomi CH0 double click" { xiaomigateway="158d0000f9defg.channel_0.double_click" }
Switch  XiaomiControl1 "Xiaomi CH1 click" { xiaomigateway="158d0000f9defg.channel_1.click" }
Switch  XiaomiControl1D "Xiaomi CH1 double click" { xiaomigateway="158d0000f9defg.channel_1.double_click" }
Switch  XiaomiControlBoth "Xiaomi both click" { xiaomigateway="158d0000f9defg.dual_channel.both_click" }
```
not working yet
```
Switch  XiaomiNatural0 "Xiaomi natural CH0" { xiaomigateway="158d0000f9abcd.channel_0" }
Switch  XiaomiNatural1 "Xiaomi natural CH1" { xiaomigateway="158d0000f9abcd.channel_1" }
```

#rule examples
```
rule "Control bathroom ventilator with xiaomi button"
when 
  Item XiaomiSwitch changed
then
    sendCommand(Ventilator, XiaomiSwitch.state.toString)
end

rule "Control garage door with Xiaomi button"
when 
  Item XiaomiClick received command ON
then
    sendCommand(GarageDoor, ON)
end

rule "Control gate door with Xiaomi button - full"
when 
  Item XiaomiLongClick received command ON
then
    sendCommand(GateDoorFull, ON)
end

rule "Control gate door with Xiaomi button - partial"
when
  Item XiaomiDoubleClick received command ON
then
    sendCommand(GateDoorPartial, ON)
end

//click
rule "Rollershutter control with Xiaomi button (UP)"
when 
  Item XiaomiRollershutterUP received command ON
then
    sendCommand(RollershutterGaming, UP)
end

//long_click
rule "Rollershutter control with Xiaomi button (DOWN)"
when 
  Item XiaomiRolleshutterDOWN received command ON
then
    sendCommand(RollershutterGaming, DOWN)
end

//double_click
rule "Rollershutter control with Xiaomi button (STOP)"
when 
  Item XiaomiRolleshutterSTOP received command ON
then
    sendCommand(RollershutterGaming, STOP)
end
```