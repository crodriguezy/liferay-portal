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

import com.liferay.portal.kernel.cluster.ClusterExecutorUtil;
import com.liferay.portal.kernel.cluster.ClusterInvokeThreadLocal;
import com.liferay.portal.kernel.cluster.ClusterRequest;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.util.Digester;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.MethodHandler;
import com.liferay.portal.kernel.util.MethodKey;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.util.PropsValues;

import java.io.Serializable;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexander Chow
 * @author Cristina Rodríguez
 * @author Mariano Álvaro Sáiz
 */
public class NonceUtil {

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

		_addNonceAndNotify(new NonceDelayed(nonce));

		return nonce;
	}

	public static boolean verify(String nonce) {
		_cleanUp();

		return _removeNonceAndNotify(new NonceDelayed(nonce));
	}

	@SuppressWarnings("unused")
	private static void _addNonce(NonceDelayed nonceDelayed) {
		boolean enabled = ClusterInvokeThreadLocal.isEnabled();

		ClusterInvokeThreadLocal.setEnabled(true);

		try {
			_nonceDelayQueue.put(nonceDelayed);
		}
		finally {
			ClusterInvokeThreadLocal.setEnabled(enabled);
		}
	}

	private static void _addNonceAndNotify(NonceDelayed nonceDelayed) {
		_nonceDelayQueue.put(nonceDelayed);

		_notifyNodes(_addNonceMethodKey, nonceDelayed);
	}

	private static void _cleanUp() {
		while (_nonceDelayQueue.poll() != null);
	}

	private static void _notifyNodes(MethodKey methodKey, Object... arguments) {
		if (!ClusterExecutorUtil.isEnabled()) {
			return;
		}

		MethodHandler methodHandler = new MethodHandler(methodKey, arguments);

		ClusterRequest clusterRequest = ClusterRequest.createMulticastRequest(
			methodHandler, true);

		clusterRequest.setFireAndForget(true);

		ClusterExecutorUtil.execute(clusterRequest);
	}

	@SuppressWarnings("unused")
	private static boolean _removeNonce(NonceDelayed nonceDelayed) {
		_cleanUp();

		boolean enabled = ClusterInvokeThreadLocal.isEnabled();

		ClusterInvokeThreadLocal.setEnabled(true);

		try {
			return _nonceDelayQueue.remove(nonceDelayed);
		}
		finally {
			ClusterInvokeThreadLocal.setEnabled(enabled);
		}
	}

	private static boolean _removeNonceAndNotify(NonceDelayed nonceDelayed) {
		boolean removed = _nonceDelayQueue.remove(nonceDelayed);

		_notifyNodes(_removeNonceMethodKey, nonceDelayed);

		return removed;
	}

	private static final long _NONCE_EXPIRATION =
		PropsValues.WEBDAV_NONCE_EXPIRATION * Time.MINUTE;

	private static final MethodKey _addNonceMethodKey = new MethodKey(
		NonceUtil.class, "_addNonce", NonceDelayed.class);
	private static final DelayQueue<NonceDelayed> _nonceDelayQueue =
		new DelayQueue<>();
	private static final MethodKey _removeNonceMethodKey = new MethodKey(
		NonceUtil.class, "_removeNonce", NonceDelayed.class);

	private static class NonceDelayed implements Delayed, Serializable {

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