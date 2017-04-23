package org.openhab.binding.xiaomigateway.internal;

import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;

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

    public Item getItem() {
        return item;
    }

    // put member fields here which holds the parsed values
    private String type;
    private Item item;

    public XiaomiGatewayBindingConfig(Item item, String type)
    {
        this.type = type;
        this.item = item;
    }
}