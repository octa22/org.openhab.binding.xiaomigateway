package org.openhab.binding.xiaomigateway.internal;

import java.net.InetAddress;
import java.util.Date;

/**
 * Created by Ondrej on 24.07.2017.
 */
public class MiioDevice {
    private int deviceId;
    private long messageId =  (int)(Math.random() * 5000); ;
    private int stamp;
    private String token;
    private InetAddress address;
    private Date lastMessageTime;

    public MiioDevice(int deviceId, String token, InetAddress address, int stamp) {
        this.deviceId = deviceId;
        this.stamp = stamp;
        this.token = token;
        this.address = address;
        this.lastMessageTime = new Date();
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getStamp() {
        return stamp;
    }

    public void setStamp(int stamp) {
        this.stamp = stamp;
        this.lastMessageTime = new Date();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public long getMessageId() {
        return messageId++;
    }

    public int getSecondsPassed() {
        return (int) (new Date().getTime() - lastMessageTime.getTime())/1000;
    }
}
