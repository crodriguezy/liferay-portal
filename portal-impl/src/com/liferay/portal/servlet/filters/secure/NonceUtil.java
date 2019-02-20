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
import com.liferay.portal.kernel.cluster.ClusterNodeResponse;
import com.liferay.portal.kernel.cluster.ClusterNodeResponses;
import com.liferay.portal.kernel.cluster.ClusterRequest;
import com.liferay.portal.kernel.cluster.FutureClusterResponses;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.util.Digester;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.MethodHandler;
import com.liferay.portal.kernel.util.MethodKey;
import com.liferay.portal.kernel.util.Time;
import com.liferay.portal.util.PropsValues;

import java.util.concurrent.BlockingQueue;
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

		_nonceDelayQueue.put(new NonceDelayed(nonce));

		return nonce;
	}

	public static boolean verify(String nonce) {
		boolean verify = false;

		if (_checkInLocalNode(nonce) || _checkInCluster(nonce)) {
			verify = true;
		}

		return verify;
	}

	private static boolean _checkInCluster(String nonce) {
		if (!ClusterExecutorUtil.isEnabled()) {
			return false;
		}

		MethodHandler methodHandler = new MethodHandler(
			_checkInLocalNode, nonce);

		ClusterRequest clusterRequest = ClusterRequest.createMulticastRequest(
			methodHandler, true);

		FutureClusterResponses futureClusterResponses =
			ClusterExecutorUtil.execute(clusterRequest);

		ClusterNodeResponses clusterNodeResponses;

		try {
			clusterNodeResponses = futureClusterResponses.get(
				10, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			_log.error("Error trying to verify nonce accross the cluster", e);

			return false;
		}

		BlockingQueue<ClusterNodeResponse> clusterResponses =
			clusterNodeResponses.getClusterResponses();

		while (!clusterResponses.isEmpty()) {
			ClusterNodeResponse clusterNodeResponse = clusterResponses.poll();

			if ((boolean)clusterNodeResponse.getResult()) {
				return true;
			}
		}

		return false;
	}

	private static boolean _checkInLocalNode(String nonce) {
		_cleanUp();

		return _nonceDelayQueue.remove(new NonceDelayed(nonce));
	}

	private static void _cleanUp() {
		while (_nonceDelayQueue.poll() != null);
	}

	private static final long _NONCE_EXPIRATION =
		PropsValues.WEBDAV_NONCE_EXPIRATION * Time.MINUTE;

	private static final Log _log = LogFactoryUtil.getLog(NonceUtil.class);

	private static final MethodKey _checkInLocalNode = new MethodKey(
		NonceUtil.class, "checkInLocalNode", String.class);
	private static final DelayQueue<NonceDelayed> _nonceDelayQueue =
		new DelayQueue<>();

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