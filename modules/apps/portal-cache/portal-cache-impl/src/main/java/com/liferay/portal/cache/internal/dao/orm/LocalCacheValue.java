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

import java.io.Serializable;

/**
 * @author Mariano Álvaro Sáiz
 */
public class LocalCacheValue implements Serializable {

	public LocalCacheValue(long createTime, Serializable value) {
		_createTime = createTime;
		_value = value;
	}

	public Serializable getValue() {
		return _value;
	}

	public boolean isDirty(Long expireTime) {
		if ((expireTime == null) || (_createTime < expireTime)) {
			return true;
		}

		return false;
	}

	private static final long serialVersionUID = 1L;

	private final long _createTime;
	private final Serializable _value;

}