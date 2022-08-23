package com.liferay.expando.kernel.exception;

import java.util.Locale;

import com.liferay.portal.kernel.exception.PortalException;

public class MustInformDefaultLocaleException extends PortalException {

	public MustInformDefaultLocaleException(Locale locale) {
		super(
			"A value for the default locale (" + locale.getLanguage() +
				") must be defined");
	}
}
