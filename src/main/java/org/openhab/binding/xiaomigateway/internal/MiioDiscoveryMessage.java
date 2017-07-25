package org.openhab.binding.xiaomigateway.internal;

import static org.openhab.binding.xiaomigateway.internal.EncryptionHelper.parseHexBinary;

/**
 * Created by Ondrej Pecta on 25.07.2017.
 */
public class MiioDiscoveryMessage extends MiioMessage {
    private final byte[] DISCOVERY_MESSAGE = parseHexBinary("21310020ffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

    public MiioDiscoveryMessage() {
        this.token = null;
        this.message = DISCOVERY_MESSAGE;
    }

    public byte[] getMessage() {
        return this.message;
    }
}
