# org.openhab.binding.xiaomigateway

Currently only one gateway is supported and only getting sub device state/reading events is supported.
Unfortunately I have no switch to test controlling its state using write command.

Based on info found here: https://github.com/louisZL/lumi-gateway-local-api

___You need to enable developer mode in Mi Home app android version!!!___
Please see https://github.com/fooxy/homeassistant-aqara/wiki/Enable-dev-mode

# build
copy __org.openhab.binding.xiaomigateway__ directory to __binding__ directory of OpenHAB source code (https://github.com/openhab/openhab)

build using maven (mvn clean install)

# install
~~copy gson-2.3.1.jar to addons directory of OpenHAB (search internet or download here: http://central.maven.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar)~~

copy target file __org.openhab.binding.xiaomigateway*__.jar__ to __addons__ directory of OpenHAB distribution

# usage
The binding detects Xiaomi gateway on local network and list all sub devices. For example:
```
2016-12-24 00:29:27.679 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Discovered Xiaomi Gateway - sid: f0b4299a54e4 ip: 192.168.2.187 port: 9898
2016-12-24 00:29:27.681 [INFO ] [.service.AbstractActiveService] - XiaomiGateway Refresh Service has been started
2016-12-24 00:29:28.084 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Discovered total of 3 Xiaomi smart devices
2016-12-24 00:29:28.089 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d0001182814 model: sensor_ht
2016-12-24 00:29:28.093 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d0000f9a538 model: switch
2016-12-24 00:29:28.094 [INFO ] [o.o.b.x.i.XiaomiGatewayBinding] - Detected Xiaomi smart device - sid: 158d00010e4104 model: magnet
```

sid is a sub device identificator used in item configuration file.
possible values are: magnet, temperature, humidity, virtual_switch (button simulates ON/OFF switch), click, long_click, double_click

#openhab.cfg
If you want to control devices please supply a developer key (you can see it in android Mi Home app when you enable developer mode)
```
xiaomigateway.key=
```

#items file
```
Switch  XiaomiClick "Xiaomi button" { xiaomigateway="158d0000f9a538.virtual_switch" }
Contact XiaomiContact "Xiaomi contact" { xiaomigateway="158d00010e4104.magnet" }
Number  RoomTemperature "Temperature  [%.1f °C]" <temperature>	{ xiaomigateway="158d0001182814.temperature" }
Number  RoomHumidity "Humidity  [%.1f %%]" <humidity>	{ xiaomigateway="158d0001182814.humidity" }
```
not tested, but should work - ___send them ON command to simulate event___
```
Switch  XiaomiNatural0 "Xiaomi natural CH0" { xiaomigateway="158d0000f9abcd.channel_0" }
Switch  XiaomiNatural1 "Xiaomi natural CH1" { xiaomigateway="158d0000f9abcd.channel_1" }
Switch  XiaomiControl0 "Xiaomi CH0 click" { xiaomigateway="158d0000f9defg.channel_0.click" }
Switch  XiaomiControl0 "Xiaomi CH0 double click" { xiaomigateway="158d0000f9defg.channel_0.double_click" }
Switch  XiaomiControl1 "Xiaomi CH1 click" { xiaomigateway="158d0000f9defg.channel_1.click" }
Switch  XiaomiControl1 "Xiaomi CH1 double click" { xiaomigateway="158d0000f9defg.channel_1.double_click" }
Switch  XiaomiControl1 "Xiaomi both click" { xiaomigateway="158d0000f9defg.both_channel.both_click" }
```