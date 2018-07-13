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

package com.liferay.portal.spring.transaction;

import com.liferay.portal.kernel.dao.orm.EntityCacheUtil;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.model.MVCCModel;
import com.liferay.portal.kernel.transaction.TransactionLifecycleManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.aopalliance.intercept.MethodInvocation;

import org.apache.commons.lang.reflect.ConstructorUtils;
import org.apache.commons.lang.reflect.FieldUtils;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Michael C. Han
 * @author Shuyang Zhou
 */
public class DefaultTransactionExecutor
	implements TransactionExecutor, TransactionHandler {

	@Override
	public void commit(
		PlatformTransactionManager platformTransactionManager,
		TransactionAttributeAdapter transactionAttributeAdapter,
		TransactionStatusAdapter transactionStatusAdapter) {

		_commit(
			platformTransactionManager, transactionAttributeAdapter,
			transactionStatusAdapter, null);
	}

	@Override
	public Object execute(
			PlatformTransactionManager platformTransactionManager,
			TransactionAttributeAdapter transactionAttributeAdapter,
			MethodInvocation methodInvocation)
		throws Throwable {

		TransactionStatusAdapter transactionStatusAdapter = start(
			platformTransactionManager, transactionAttributeAdapter);

		Object returnValue = null;

		try {
			returnValue = methodInvocation.proceed();
		}
		catch (Throwable throwable) {
			rollback(
				platformTransactionManager, throwable,
				transactionAttributeAdapter, transactionStatusAdapter);
		}

		commit(
			platformTransactionManager, transactionAttributeAdapter,
			transactionStatusAdapter);

		return _getUpdatedReturnValue(returnValue);
	}

	@Override
	public void rollback(
			PlatformTransactionManager platformTransactionManager,
			Throwable throwable,
			TransactionAttributeAdapter transactionAttributeAdapter,
			TransactionStatusAdapter transactionStatusAdapter)
		throws Throwable {

		if (transactionAttributeAdapter.rollbackOn(throwable)) {
			try {
				platformTransactionManager.rollback(
					transactionStatusAdapter.getTransactionStatus());
			}
			catch (Throwable t) {
				t.addSuppressed(throwable);

				throw t;
			}
			finally {
				TransactionLifecycleManager.fireTransactionRollbackedEvent(
					transactionAttributeAdapter, transactionStatusAdapter,
					throwable);
			}
		}
		else {
			_commit(
				platformTransactionManager, transactionAttributeAdapter,
				transactionStatusAdapter, throwable);
		}

		throw throwable;
	}

	@Override
	public TransactionStatusAdapter start(
		PlatformTransactionManager platformTransactionManager,
		TransactionAttributeAdapter transactionAttributeAdapter) {

		TransactionStatusAdapter transactionStatusAdapter =
			new TransactionStatusAdapter(
				platformTransactionManager,
				platformTransactionManager.getTransaction(
					transactionAttributeAdapter));

		TransactionLifecycleManager.fireTransactionCreatedEvent(
			transactionAttributeAdapter, transactionStatusAdapter);

		return transactionStatusAdapter;
	}

	private void _commit(
		PlatformTransactionManager platformTransactionManager,
		TransactionAttributeAdapter transactionAttributeAdapter,
		TransactionStatusAdapter transactionStatusAdapter,
		Throwable applicationThrowable) {

		Throwable throwable = null;

		try {
			platformTransactionManager.commit(
				transactionStatusAdapter.getTransactionStatus());
		}
		catch (Throwable t) {
			if (applicationThrowable != null) {
				t.addSuppressed(applicationThrowable);
			}

			throwable = t;

			throw t;
		}
		finally {
			if (throwable != null) {
				TransactionLifecycleManager.fireTransactionRollbackedEvent(
					transactionAttributeAdapter, transactionStatusAdapter,
					throwable);
			}
			else {
				TransactionLifecycleManager.fireTransactionCommittedEvent(
					transactionAttributeAdapter, transactionStatusAdapter);
			}
		}
	}

	private Object _getOneLevelWrapperUpdatedObject(Object returnValue) {
		Class<?> clazz = returnValue.getClass();

		Field[] fields = clazz.getDeclaredFields();

		if (fields.length != 1) {
			return returnValue;
		}

		Field field = fields[0];

		Constructor<?> constructor =
			ConstructorUtils.getMatchingAccessibleConstructor(
				clazz, new Class<?>[] {field.getType()});

		try {
			if (constructor != null) {
				Object fieldValue = FieldUtils.readDeclaredField(
					returnValue, field.getName(), true);

				Object newFieldValue = _getUpdatedReturnValue(fieldValue);

				if (newFieldValue != fieldValue) {
					return constructor.newInstance(fieldValue);
				}
			}
		}
		catch (Exception e) {
		}

		return returnValue;
	}

	private Object _getUpdatedObject(Object object) {
		if (object instanceof BaseModel<?> && object instanceof MVCCModel) {
			BaseModel<?> baseModel = (BaseModel<?>)object;
			MVCCModel mvccModel = (MVCCModel)object;

			Object updatedValue = EntityCacheUtil.getResult(
				baseModel.isEntityCacheEnabled(), object.getClass(),
				baseModel.getPrimaryKeyObj());

			if (updatedValue != null) {
				if (mvccModel.getMvccVersion() <
						((MVCCModel)updatedValue).getMvccVersion()) {

					return updatedValue;
				}
			}
		}

		return object;
	}

	private Object _getUpdatedReturnValue(Object returnValue) {
		if (returnValue instanceof MVCCModel) {
			if (returnValue instanceof BaseModel<?>) {
				returnValue = _getUpdatedObject(returnValue);
			}
			else {
				returnValue = _getOneLevelWrapperUpdatedObject(returnValue);
			}
		}

		return returnValue;
	}

}