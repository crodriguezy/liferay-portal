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

import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Mariano Álvaro Sáiz
 */
public final class AllowedIPsValidator {

	public AllowedIPsValidator(String allowedIpsMask) {
		_protocolValidator = ProtocolValidator.getValidator(allowedIpsMask);
	}

	public boolean isAllowedIP(String ipAddress) {
		return _protocolValidator.isAllowedIP(ipAddress);
	}

	private static final int[] _BYTE = {
		0b00000000, 0b10000000, 0b11000000, 0b11100000, 0b11110000, 0b11111000,
		0b11111100, 0b11111110, 0b11111111
	};

	private static final Log _log = LogFactoryUtil.getLog(
		AllowedIPsValidator.class);

	private final ProtocolValidator _protocolValidator;

	private static class AllowedIPsDummyValidator extends ProtocolValidator {

		public byte[] getEmptyMask() {
			return null;
		}

		public boolean isAllowedIP(String ipAddress) {
			return false;
		}

		public boolean isValidCIDR(int cidr) {
			return false;
		}

		public boolean isValidSubnetMask(String mask) {
			return false;
		}

	}

	private static class AllowedIPsv4Validator extends ProtocolValidator {

		public byte[] getEmptyMask() {
			return new byte[4];
		}

		public boolean isValidCIDR(int cidr) {
			if ((cidr >= 0) && (cidr <= 32)) {
				return true;
			}

			return false;
		}

		public boolean isValidSubnetMask(String mask) {
			return Validator.isIPv4Address(mask);
		}

	}

	private static class AllowedIPsv6Validator extends ProtocolValidator {

		public byte[] getEmptyMask() {
			return new byte[16];
		}

		public boolean isValidCIDR(int cidr) {
			if ((cidr >= 0) && (cidr <= 128)) {
				return true;
			}

			return false;
		}

		public boolean isValidSubnetMask(String mask) {
			return Validator.isIPv6Address(mask);
		}

	}

	private abstract static class ProtocolValidator {

		public static ProtocolValidator getValidator(String allowedIpsMask) {
			String[] ipAndMask = StringUtil.split(
				allowedIpsMask, StringPool.SLASH);

			ProtocolValidator protocolValidator = null;

			if (Validator.isIPv4Address(ipAndMask[0])) {
				protocolValidator = new AllowedIPsv4Validator();
			}
			else if (Validator.isIPv6Address(ipAndMask[0])) {
				protocolValidator = new AllowedIPsv6Validator();
			}
			else {
				return new AllowedIPsDummyValidator();
			}

			try {
				protocolValidator.setAllowedAddress(
					InetAddress.getByName(ipAndMask[0]));
				protocolValidator.setMask(ipAndMask);
			}
			catch (Exception e) {
				_log.error("Invalid configured address: ", e);

				return new AllowedIPsDummyValidator();
			}

			return protocolValidator;
		}

		public abstract byte[] getEmptyMask();

		public boolean isAllowedIP(String ipAddress) {
			InetAddress inetAddress = null;

			try {
				inetAddress = InetAddress.getByName(ipAddress);
			}
			catch (UnknownHostException uhe) {
				return false;
			}

			byte[] inetAddressBytes = inetAddress.getAddress();

			if (!isSameProtocol(inetAddressBytes)) {
				return false;
			}

			if (_mask == null) {
				return _allowedAddress.equals(inetAddress);
			}

			for (int i = 0; i < _mask.length; i++) {
				if ((inetAddressBytes[i] & _mask[i]) !=
						(_allowedAddressBytes[i] & _mask[i])) {

					return false;
				}
			}

			return true;
		}

		public boolean isSameProtocol(byte[] ipAddressBytes) {
			if (_allowedAddressBytes.length == ipAddressBytes.length) {
				return true;
			}

			return false;
		}

		public abstract boolean isValidCIDR(int cidr);

		public abstract boolean isValidSubnetMask(String mask);

		protected void setAllowedAddress(InetAddress allowedAddress) {
			_allowedAddress = allowedAddress;

			_allowedAddressBytes = _allowedAddress.getAddress();
		}

		protected void setMask(String[] ipAndMask) throws UnknownHostException {
			if (_hasMask(ipAndMask)) {
				String mask = GetterUtil.getString(ipAndMask[1]);

				if (Validator.isNumber(mask)) {
					_mask = _getMaskFromCIDR(mask);
				}
				else {
					_mask = _getMaskFromSubnet(mask);
				}
			}
		}

		private byte[] _getMaskFromCIDR(String mask) {
			int cidr = GetterUtil.getInteger(mask);

			byte[] bytesMask = getEmptyMask();

			int maskBytes = cidr / 8;
			int byteOffset = cidr % 8;

			for (int i = 0; i < maskBytes; i++) {
				bytesMask[i] = (byte)_BYTE[8];
			}

			if (maskBytes < bytesMask.length) {
				bytesMask[maskBytes] = (byte)_BYTE[byteOffset];
			}

			return bytesMask;
		}

		private byte[] _getMaskFromSubnet(String mask)
			throws UnknownHostException {

			InetAddress address = InetAddress.getByName(mask);

			return address.getAddress();
		}

		private boolean _hasMask(String[] ipAndMask) {
			if (ipAndMask.length > 1) {
				return true;
			}

			return false;
		}

		private InetAddress _allowedAddress;
		private byte[] _allowedAddressBytes;
		private byte[] _mask;

	}

}