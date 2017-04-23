/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.xiaomigateway.internal;

import org.openhab.binding.xiaomigateway.XiaomiGatewayBindingProvider;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.*;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class XiaomiGatewayGenericBindingProvider extends AbstractGenericBindingProvider implements XiaomiGatewayBindingProvider {

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "xiaomigateway";
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof DimmerItem || item instanceof ColorItem || item instanceof SwitchItem || item instanceof ContactItem || item instanceof NumberItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Dimmer- Color- Switch- Contact- and NumberItems are allowed - please check your *.items configuration");
		}
	}

	public String getItemType(String itemName) {
		final XiaomiGatewayBindingConfig config = (XiaomiGatewayBindingConfig) this.bindingConfigs.get(itemName);
		return config != null ? (config.getType()) : null;
	}

	public Item getItem(String itemName) {
		final XiaomiGatewayBindingConfig config = (XiaomiGatewayBindingConfig) this.bindingConfigs.get(itemName);
		return config != null ? (config.getItem()) : null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);
		XiaomiGatewayBindingConfig config = new XiaomiGatewayBindingConfig(item, bindingConfig);
		
		//parse bindingconfig here ...
		
		addBindingConfig(item, config);		
	}

}
