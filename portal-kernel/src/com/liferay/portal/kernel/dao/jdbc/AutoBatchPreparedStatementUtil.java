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

package com.liferay.portal.kernel.dao.jdbc;

import com.liferay.petra.concurrent.FutureListener;
import com.liferay.petra.concurrent.NoticeableExecutorService;
import com.liferay.petra.concurrent.NoticeableFuture;
import com.liferay.petra.executor.PortalExecutorManager;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.portal.kernel.util.ServiceProxyFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Shuyang Zhou
 * @author Preston Crary
 */
public class AutoBatchPreparedStatementUtil {

	public static PreparedStatement autoBatch(
			PreparedStatement preparedStatement)
		throws SQLException {

		Connection connection = preparedStatement.getConnection();

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		if (databaseMetaData.supportsBatchUpdates()) {
			return (PreparedStatement)ProxyUtil.newProxyInstance(
				ClassLoader.getSystemClassLoader(), _INTERFACES,
				new BatchInvocationHandler(preparedStatement));
		}

		return (PreparedStatement)ProxyUtil.newProxyInstance(
			ClassLoader.getSystemClassLoader(), _INTERFACES,
			new NoBatchInvocationHandler(preparedStatement));
	}

	/**
	 * @deprecated As of 7.0.0, replaced by {@link #concurrentAutoBatch(String)}
	 */
	@Deprecated
	public static PreparedStatement concurrentAutoBatch(
			Connection connection, String sql)
		throws SQLException {

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		if (databaseMetaData.supportsBatchUpdates()) {
			return (PreparedStatement)ProxyUtil.newProxyInstance(
				ClassLoader.getSystemClassLoader(), _INTERFACES,
				new ConcurrentBatchInvocationHandler(connection, sql));
		}

		return (PreparedStatement)ProxyUtil.newProxyInstance(
			ClassLoader.getSystemClassLoader(), _INTERFACES,
			new ConcurrentNoBatchInvocationHandler(connection, sql));
	}

	public static PreparedStatement concurrentAutoBatch(String sql)
		throws SQLException {

		Connection connection = DataAccess.getConnection();

		DatabaseMetaData databaseMetaData = connection.getMetaData();

		if (databaseMetaData.supportsBatchUpdates()) {
			return (PreparedStatement)ProxyUtil.newProxyInstance(
				ClassLoader.getSystemClassLoader(), _INTERFACES,
				new ConcurrentBatchInvocationHandler(sql));
		}

		return (PreparedStatement)ProxyUtil.newProxyInstance(
			ClassLoader.getSystemClassLoader(), _INTERFACES,
			new ConcurrentNoBatchInvocationHandler(sql));
	}

	private static final int _HIBERNATE_JDBC_BATCH_SIZE = GetterUtil.getInteger(
		PropsUtil.get(PropsKeys.HIBERNATE_JDBC_BATCH_SIZE));

	private static final Class<?>[] _INTERFACES =
		new Class<?>[] {PreparedStatement.class};

	private static final Method _addBatchMethod;
	private static final Method _closeMethod;
	private static final Method _executeBatch;
	private static volatile PortalExecutorManager _portalExecutorManager =
		ServiceProxyFactory.newServiceTrackedInstance(
			PortalExecutorManager.class, AutoBatchPreparedStatementUtil.class,
			"_portalExecutorManager", true);

	static {
		try {
			_addBatchMethod = PreparedStatement.class.getMethod("addBatch");
			_closeMethod = PreparedStatement.class.getMethod("close");
			_executeBatch = PreparedStatement.class.getMethod("executeBatch");
		}
		catch (NoSuchMethodException nsme) {
			throw new ExceptionInInitializerError(nsme);
		}
	}

	private static class BatchInvocationHandler implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

			if (method.equals(_executeBatch)) {
				if (_count > 0) {
					_count = 0;

					return _preparedStatement.executeBatch();
				}

				return new int[0];
			}

			if (!method.equals(_addBatchMethod)) {
				return method.invoke(_preparedStatement, args);
			}

			_preparedStatement.addBatch();

			if (++_count >= _HIBERNATE_JDBC_BATCH_SIZE) {
				_preparedStatement.executeBatch();

				_count = 0;
			}

			return null;
		}

		private BatchInvocationHandler(PreparedStatement preparedStatement) {
			_preparedStatement = preparedStatement;
		}

		private int _count;
		private final PreparedStatement _preparedStatement;

	}

	private static class ConcurrentBatchInvocationHandler
		extends ConcurrentInvocationHandler {

		@Override
		protected Object addBatchInvocation(PreparedStatement preparedStatement)
			throws SQLException {

			preparedStatement.addBatch();

			if (++_count >= _HIBERNATE_JDBC_BATCH_SIZE) {
				executeConcurrent();
			}

			return null;
		}

		@Override
		protected Object executeBatchInvocation() throws SQLException {
			if (_count > 0) {
				executeConcurrent();
			}

			return new int[0];
		}

		@Override
		protected void executePreparedStatement(
				PreparedStatement preparedStatement)
			throws SQLException {

			preparedStatement.executeBatch();
		}

		/**
		 * @deprecated As of 7.0.0, replaced by {@link #AutoBatchPreparedStatementUtil(String)}
		 */
		@Deprecated
		private ConcurrentBatchInvocationHandler(
				Connection connection, String sql)
			throws SQLException {

			super(connection, sql);
		}

		private ConcurrentBatchInvocationHandler(String sql)
			throws SQLException {

			super(sql);
		}

		private int _count;

	}

	private abstract static class ConcurrentInvocationHandler
		implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

			if (method.equals(_addBatchMethod)) {
				addBatchInvocation(_preparedStatement);
			}

			if (method.equals(_executeBatch)) {
				executeBatchInvocation();
			}

			if (method.equals(_closeMethod)) {
				Throwable throwable = null;

				for (Future<Void> future : _futures) {
					try {
						future.get();
					}
					catch (Throwable t) {
						if (t instanceof ExecutionException) {
							t = t.getCause();
						}

						if (throwable == null) {
							throwable = t;
						}
						else {
							throwable.addSuppressed(t);
						}
					}
				}

				if (throwable != null) {
					throw throwable;
				}
			}

			return method.invoke(_preparedStatement, args);
		}

		protected abstract Object addBatchInvocation(
				PreparedStatement preparedStatement)
			throws SQLException;

		protected abstract Object executeBatchInvocation() throws SQLException;

		protected void executeConcurrent() throws SQLException {
			restartBatch();

			final PreparedStatement preparedStatement = _preparedStatement;

			NoticeableFuture<Void> noticeableFuture =
				_noticeableExecutorService.submit(
					() -> {
						try {
							executePreparedStatement(preparedStatement);
						}
						finally {
							preparedStatement.close();
						}

						return null;
					});

			_futures.add(noticeableFuture);

			noticeableFuture.addFutureListener(
				new FutureListener<Void>() {

					@Override
					public void complete(Future<Void> future) {
						try {
							future.get();

							_futures.remove(future);
						}
						catch (Throwable t) {
						}
					}

				});

			_resetPreparedStatement();
		}

		protected abstract void executePreparedStatement(
				PreparedStatement preparedStatement)
			throws SQLException;

		protected void restartBatch() {
		}

		/**
		 * @deprecated As of 7.0.0, replaced by {@link #AutoBatchPreparedStatementUtil(String)}
		 */
		@Deprecated
		private ConcurrentInvocationHandler(Connection connection, String sql)
			throws SQLException {

			_databaseConcurrent = false;
			_connection = connection;
			_sql = sql;

			_resetPreparedStatement();
		}

		private ConcurrentInvocationHandler(String sql) throws SQLException {
			_databaseConcurrent = true;
			_sql = sql;

			_resetPreparedStatement();
		}

		private synchronized void _resetPreparedStatement()
			throws SQLException {

			if (_databaseConcurrent) {
				_connection = DataAccess.getConnection();
			}

			_preparedStatement = _connection.prepareStatement(_sql);
		}

		private Connection _connection;
		private final boolean _databaseConcurrent;
		private final Set<Future<Void>> _futures = Collections.newSetFromMap(
			new ConcurrentHashMap<>());
		private final NoticeableExecutorService _noticeableExecutorService =
			_portalExecutorManager.getPortalExecutor(
				ConcurrentBatchInvocationHandler.class.getName());
		private PreparedStatement _preparedStatement;
		private final String _sql;

	}

	private static class ConcurrentNoBatchInvocationHandler
		extends ConcurrentInvocationHandler {

		@Override
		protected Object addBatchInvocation(PreparedStatement preparedStatement)
			throws SQLException {

			executeConcurrent();

			return null;
		}

		@Override
		protected Object executeBatchInvocation() throws SQLException {
			return new int[0];
		}

		@Override
		protected void executePreparedStatement(
				PreparedStatement preparedStatement)
			throws SQLException {

			preparedStatement.executeUpdate();
		}

		/**
		 * @deprecated As of 7.0.0, replaced by {@link #AutoBatchPreparedStatementUtil(String)}
		 */
		@Deprecated
		private ConcurrentNoBatchInvocationHandler(
				Connection connection, String sql)
			throws SQLException {

			super(connection, sql);
		}

		private ConcurrentNoBatchInvocationHandler(String sql)
			throws SQLException {

			super(sql);
		}

	}

	private static class NoBatchInvocationHandler implements InvocationHandler {

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

			if (method.equals(_addBatchMethod)) {
				_preparedStatement.executeUpdate();

				return null;
			}

			if (method.equals(_executeBatch)) {
				return new int[0];
			}

			return method.invoke(_preparedStatement, args);
		}

		private NoBatchInvocationHandler(PreparedStatement preparedStatement) {
			_preparedStatement = preparedStatement;
		}

		private final PreparedStatement _preparedStatement;

	}

}