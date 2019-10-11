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

package com.liferay.portal.cache.internal.dao.orm;

import com.liferay.portal.kernel.cache.PortalCache;
import com.liferay.portal.kernel.cache.PortalCacheException;
import com.liferay.portal.kernel.cache.PortalCacheListener;
import com.liferay.portal.kernel.util.GetterUtil;

import java.io.Serializable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Mariano Álvaro Sáiz
 */
public class PortalCacheModificationListener
	implements PortalCacheListener<Serializable, Serializable> {

	@Override
	public void dispose() {
	}

	public long getPortalCacheLastModifiedTime(
		PortalCache<Serializable, Serializable> portalCache) {

		Long lastModifiedTime = _portalCacheLastModifiedTime.get(
			portalCache.getPortalCacheName());

		return GetterUtil.getLong(lastModifiedTime);
	}

	@Override
	public void notifyEntryEvicted(
			PortalCache<Serializable, Serializable> portalCache,
			Serializable key, Serializable value, int timeToLive)
		throws PortalCacheException {
	}

	@Override
	public void notifyEntryExpired(
			PortalCache<Serializable, Serializable> portalCache,
			Serializable key, Serializable value, int timeToLive)
		throws PortalCacheException {
	}

	@Override
	public void notifyEntryPut(
			PortalCache<Serializable, Serializable> portalCache,
			Serializable key, Serializable value, int timeToLive)
		throws PortalCacheException {

		_portalCacheLastModifiedTime.put(
			portalCache.getPortalCacheName(), System.currentTimeMillis());
	}

	@Override
	public void notifyEntryRemoved(
			PortalCache<Serializable, Serializable> portalCache,
			Serializable key, Serializable value, int timeToLive)
		throws PortalCacheException {

		_portalCacheLastModifiedTime.put(
			portalCache.getPortalCacheName(), System.currentTimeMillis());
	}

	@Override
	public void notifyEntryUpdated(
			PortalCache<Serializable, Serializable> portalCache,
			Serializable key, Serializable value, int timeToLive)
		throws PortalCacheException {

		_portalCacheLastModifiedTime.put(
			portalCache.getPortalCacheName(), System.currentTimeMillis());
	}

	@Override
	public void notifyRemoveAll(
			PortalCache<Serializable, Serializable> portalCache)
		throws PortalCacheException {

		_portalCacheLastModifiedTime.put(
			portalCache.getPortalCacheName(), System.currentTimeMillis());
	}

	private final ConcurrentMap<String, Long> _portalCacheLastModifiedTime =
		new ConcurrentHashMap<>();

}