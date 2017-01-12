# org.openhab.binding.xiaomigateway

This binding brings Xiaomi Gateway Smart Home devices (Aqara) integration with OpenHAB1.x
Currently only one gateway is supported and only getting sub device state/reading events confirmed to be working. 
Controlling of gateway light works too (only On/Off, no color change within OpenHAB yet)
Unfortunately I have no switch to test controlling its state using write command, but write key generation based on gateway token seems to be implemented OK.

Based on info found here: https://github.com/louisZL/lumi-gateway-local-api

___You need to enable developer mode in Mi Home app (both Android/iOS versions work) !!!___
Please see https://github.com/fooxy/homeassistant-aqara/wiki/Enable-dev-mode

# build
copy __org.openhab.binding.xiaomigateway__ directory to __binding__ directory of OpenHAB source code (https://github.com/openhab/openhab)

build using maven (mvn clean install)

# install
~~copy gson-2.3.1.jar to addons directory of OpenHAB (search internet or download here: http://central.maven.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar)~~

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
possible values are: magnet, temperature, humidity, virtual_switch (button simulates ON/OFF switch), click, long_click, double_click

#openhab.cfg
If you want to control devices please supply a developer key (you can see it in Mi Home app when you enable developer mode)
If you want to change default startup color, please include startConfig configuration (e.g. xiaomigateway:startColor=1677786880L).
```
xiaomigateway:key=

//Default startColor=1677786880L
xiaomigateway:startColor=
```

#items file
```
Switch  XiaomiSwitch "Xiaomi button" { xiaomigateway="158d0000f9a538.virtual_switch" }
Switch  XiaomiClick "Xiaomi button click" { xiaomigateway="158d0000f9axyz.click" }
Switch  XiaomiLongClick "Xiaomi button long" { xiaomigateway="158d0000f9axyz.long_click" }
Switch  XiaomiDoubleClick "Xiaomi button double" { xiaomigateway="158d0000f9axyz.double_click" }
Contact XiaomiContact "Xiaomi contact" { xiaomigateway="158d00010e4104.magnet" }
Number  RoomTemperature "Temperature  [%.1f °C]" <temperature>	{ xiaomigateway="158d0001182814.temperature" }
Number  RoomHumidity "Humidity  [%.1f %%]" <humidity>	{ xiaomigateway="158d0001182814.humidity" }
Switch  XiaomiGatewayLight "Gateway light" { xiaomigateway="f1b5299a55e5.color" }
```
not tested, but should work - ___send ON command to these items to fire an event___
```
Switch  XiaomiNatural0 "Xiaomi natural CH0" { xiaomigateway="158d0000f9abcd.channel_0" }
Switch  XiaomiNatural1 "Xiaomi natural CH1" { xiaomigateway="158d0000f9abcd.channel_1" }
Switch  XiaomiControl0 "Xiaomi CH0 click" { xiaomigateway="158d0000f9defg.channel_0.click" }
Switch  XiaomiControl0 "Xiaomi CH0 double click" { xiaomigateway="158d0000f9defg.channel_0.double_click" }
Switch  XiaomiControl1 "Xiaomi CH1 click" { xiaomigateway="158d0000f9defg.channel_1.click" }
Switch  XiaomiControl1 "Xiaomi CH1 double click" { xiaomigateway="158d0000f9defg.channel_1.double_click" }
Switch  XiaomiControl1 "Xiaomi both click" { xiaomigateway="158d0000f9defg.both_channel.both_click" }
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