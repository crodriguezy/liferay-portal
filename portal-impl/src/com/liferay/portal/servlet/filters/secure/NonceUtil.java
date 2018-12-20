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

package com.liferay.portal.servlet.filters.secure;

import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.util.Digester;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.util.PropsValues;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * @author Alexander Chow
 */
public class NonceUtil {

	public static String generate(
		HttpServletRequest request, long companyId, String remoteAddress) {

		String companyKey = null;

		try {
			Company company = CompanyLocalServiceUtil.getCompanyById(companyId);

			companyKey = company.getKey();
		}
		catch (Exception e) {
			throw new RuntimeException("Invalid companyId " + companyId, e);
		}

		long timestamp = System.currentTimeMillis();

		String nonce = DigesterUtil.digestHex(
			Digester.MD5, remoteAddress, String.valueOf(timestamp), companyKey);

		HttpSession session = request.getSession();

		session.setAttribute(WebKeys.NONCE, new Nonce(nonce));

		return nonce;
	}

	public static boolean verify(HttpServletRequest request, String nonce) {
		HttpSession session = request.getSession();

		Nonce originalNonce = (Nonce)session.getAttribute(WebKeys.NONCE);

		if (originalNonce == null) {
			return false;
		}

		session.removeAttribute(WebKeys.NONCE);

		if (originalNonce.isExpired()) {
			return false;
		}

		return originalNonce._nonce.equals(nonce);
	}

	private static final long _NONCE_EXPIRATION =
		PropsValues.WEBDAV_NONCE_EXPIRATION * Time.MINUTE;

	private static class Nonce implements Serializable {

		public Nonce(String nonce) {
			if (nonce == null) {
				throw new NullPointerException("Nonce is null");
			}

			_createTime = System.currentTimeMillis();

			_expirationTime = _NONCE_EXPIRATION + _createTime;

			_nonce = nonce;
		}

		@Override
		public boolean equals(Object obj) {
			Nonce nonce = (Nonce)obj;

			if (_nonce.equals(nonce._nonce)) {
				return true;
			}

			return false;
		}

		@Override
		public int hashCode() {
			return _nonce.hashCode();
		}

		public boolean isExpired() {
			if (System.currentTimeMillis() > _expirationTime) {
				return true;
			}

			return false;
		}

		private final long _createTime;
		private final long _expirationTime;
		private final String _nonce;

	}

}