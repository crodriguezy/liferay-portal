/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.kernel.internal.security.access.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Mariano Álvaro Sáiz
 */
public class AllowedIPsValidatorTest {

	@Test
	public void testIPv4InvalidConfigurationInvalidatesEverything() {
		AllowedIPsValidator invalidIPValidator = new AllowedIPsValidator(
			"192.168.0/24");

		Assert.assertFalse(invalidIPValidator.isAllowedIP(_IP_V4_ADDRESS));

		AllowedIPsValidator invalidSubnetValidator = new AllowedIPsValidator(
			"192.168.1.0/33");

		Assert.assertFalse(invalidSubnetValidator.isAllowedIP(_IP_V4_ADDRESS));
	}

	@Test
	public void testIPv4SubnetCidrValidatesCorrectly() {
		AllowedIPsValidator ipSubnet = new AllowedIPsValidator(
			"192.168.1.0/255.255.255.0");

		Assert.assertTrue(ipSubnet.isAllowedIP(_IP_V4_ADDRESS));

		ipSubnet = new AllowedIPsValidator("192.168.1.128/255.255.255.128");

		Assert.assertFalse(ipSubnet.isAllowedIP(_IP_V4_ADDRESS));

		Assert.assertTrue(ipSubnet.isAllowedIP("192.168.1.159"));
	}

	@Test
	public void testIPv4SubnetDoesNotMatchIPv6Address() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			_IP_V4_ADDRESS);

		Assert.assertFalse(allowedIPsValidator.isAllowedIP(_IP_V6_ADDRESS));
	}

	@Test
	public void testIPv4SubnetMatchesIPv4Address() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			_IP_V4_ADDRESS);

		Assert.assertTrue(allowedIPsValidator.isAllowedIP(_IP_V4_ADDRESS));
	}

	@Test
	public void testIPv4SubnetValidatesCorrectly() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			"192.168.1.0/24");

		Assert.assertTrue(allowedIPsValidator.isAllowedIP(_IP_V4_ADDRESS));

		allowedIPsValidator = new AllowedIPsValidator("192.168.1.128/25");

		Assert.assertFalse(allowedIPsValidator.isAllowedIP(_IP_V4_ADDRESS));

		Assert.assertTrue(allowedIPsValidator.isAllowedIP("192.168.1.159"));
	}

	@Test
	public void testIPv6InvalidConfigurationInvalidatesEverything() {
		AllowedIPsValidator invalidIPValidator = new AllowedIPsValidator(
			"2001:AB9::/48");

		Assert.assertFalse(invalidIPValidator.isAllowedIP(_IP_V6_ADDRESS));

		AllowedIPsValidator invalidSubnetValidator = new AllowedIPsValidator(
			"2001:DB8::/130");

		Assert.assertFalse(invalidSubnetValidator.isAllowedIP(_IP_V6_ADDRESS));
	}

	@Test
	public void testIPv6SubnetDoesNotMatchIPv4Address() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			_IP_V6_ADDRESS);

		Assert.assertFalse(allowedIPsValidator.isAllowedIP(_IP_V4_ADDRESS));
	}

	@Test
	public void testIPv6SubnetMatchesIPv6Address() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			_IP_V6_ADDRESS);

		Assert.assertTrue(allowedIPsValidator.isAllowedIP(_IP_V6_ADDRESS));
	}

	@Test
	public void testIPv6SubnetValidatesCorrectly() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			"2001:AB9::/48");

		Assert.assertTrue(
			allowedIPsValidator.isAllowedIP("2001:AB9:0:0:0:0:0:0"));

		Assert.assertTrue(
			allowedIPsValidator.isAllowedIP("2001:AB9:0:0:0:0:0:1"));

		Assert.assertTrue(
			allowedIPsValidator.isAllowedIP(
				"2001:AB9:0:FFFF:FFFF:FFFF:FFFF:FFFF"));

		Assert.assertFalse(
			allowedIPsValidator.isAllowedIP("2001:AB9:1:0:0:0:0:0"));
	}

	@Test
	public void testZeroMaskValidatesEveryIP() {
		AllowedIPsValidator allowedIPsValidator = new AllowedIPsValidator(
			"0.0.0.0/0");

		Assert.assertTrue(allowedIPsValidator.isAllowedIP("1.2.3.4"));

		Assert.assertTrue(allowedIPsValidator.isAllowedIP("192.168.0.159"));

		allowedIPsValidator = new AllowedIPsValidator("192.168.0.159/0");

		Assert.assertTrue(allowedIPsValidator.isAllowedIP("1.2.3.4"));

		Assert.assertTrue(allowedIPsValidator.isAllowedIP("192.168.0.159"));
	}

	private static final String _IP_V4_ADDRESS = "192.168.1.104";

	private static final String _IP_V6_ADDRESS =
		"2001:AC8:1234:0000:0000:C1C0:ABCD:0876";

}