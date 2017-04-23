package org.openhab.binding.xiaomigateway.internal;

import org.openhab.core.binding.BindingConfig;

/**
 * This is a helper class holding binding specific configuration details
 *
 * @author Ondrej Pecta
 * @since 1.9.0
 */
class XiaomiGatewayBindingConfig implements BindingConfig {
    public String getType() {
        return type;
    }

    // put member fields here which holds the parsed values
    private String type;

    public XiaomiGatewayBindingConfig(String type) {
        this.type = type;
    }
}