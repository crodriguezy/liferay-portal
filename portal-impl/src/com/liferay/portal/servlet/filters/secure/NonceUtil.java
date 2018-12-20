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

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

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

	public static String generate(long companyId, String remoteAddress) {
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

		_nonceDelayQueue.put(new NonceDelayed(nonce));

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

	public static boolean verify(String nonce) {
		_cleanUp();

		return _nonceDelayQueue.contains(new NonceDelayed(nonce));
	}

	private static void _cleanUp() {
		while (_nonceDelayQueue.poll() != null);
	}

	private static final long _NONCE_EXPIRATION =
		PropsValues.WEBDAV_NONCE_EXPIRATION * Time.MINUTE;

	private static final DelayQueue<NonceDelayed> _nonceDelayQueue =
		new DelayQueue<>();

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

	private static class NonceDelayed implements Delayed {

		public NonceDelayed(String nonce) {
			if (nonce == null) {
				throw new NullPointerException("Nonce is null");
			}

			_nonce = nonce;
			_createTime = System.currentTimeMillis();
		}

		@Override
		public int compareTo(Delayed delayed) {
			NonceDelayed nonceDelayed = (NonceDelayed)delayed;

			long result = _createTime - nonceDelayed._createTime;

			if (result == 0) {
				return 0;
			}
			else if (result > 0) {
				return 1;
			}

			return -1;
		}

		@Override
		public boolean equals(Object obj) {
			NonceDelayed nonceDelayed = (NonceDelayed)obj;

			if (_nonce.equals(nonceDelayed._nonce)) {
				return true;
			}

			return false;
		}

		@Override
		public long getDelay(TimeUnit timeUnit) {
			long leftDelayTime =
				_NONCE_EXPIRATION + _createTime - System.currentTimeMillis();

			return timeUnit.convert(leftDelayTime, TimeUnit.MILLISECONDS);
		}

		@Override
		public int hashCode() {
			return _nonce.hashCode();
		}

		private final long _createTime;
		private final String _nonce;

	}

}