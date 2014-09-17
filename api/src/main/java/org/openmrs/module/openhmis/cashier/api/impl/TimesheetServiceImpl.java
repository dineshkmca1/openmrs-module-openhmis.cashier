/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.openhmis.cashier.api.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.openhmis.cashier.api.ITimesheetService;
import org.openmrs.module.openhmis.cashier.api.model.Timesheet;
import org.openmrs.module.openhmis.cashier.api.util.PrivilegeConstants;
import org.openmrs.module.openhmis.commons.api.entity.impl.BaseEntityDataServiceImpl;
import org.openmrs.module.openhmis.commons.api.entity.security.IEntityAuthorizationPrivileges;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class TimesheetServiceImpl
		extends BaseEntityDataServiceImpl<Timesheet>
		implements ITimesheetService, IEntityAuthorizationPrivileges {

	private static final String CLOCK_IN = "clockIn";
	private static final String CLOCK_OUT = "clockOut";

	@Override
	protected IEntityAuthorizationPrivileges getPrivileges() {
		return this;
	}

	@Override
	protected void validate(Timesheet entity) throws APIException {
	}

	@Override
	public String getVoidPrivilege() {
		return PrivilegeConstants.MANAGE_TIMESHEETS;
	}

	@Override
	public String getSavePrivilege() {
		return PrivilegeConstants.MANAGE_TIMESHEETS;
	}

	@Override
	public String getPurgePrivilege() {
		return PrivilegeConstants.PURGE_TIMESHEETS;
	}

	@Override
	public String getGetPrivilege() {
		return PrivilegeConstants.VIEW_TIMESHEETS;
	}

	@Override
	public Timesheet getCurrentTimesheet(Provider cashier) {
		Criteria criteria = repository.createCriteria(Timesheet.class);
		criteria.add(Restrictions.and(
				Restrictions.eq("cashier", cashier),
				Restrictions.isNull(CLOCK_OUT))
		);
		criteria.addOrder(Order.desc(CLOCK_IN));

		return repository.selectSingle(Timesheet.class, criteria);
	}

	@Override
	public void closeOpenTimesheets() {
		Criteria criteria = repository.createCriteria(Timesheet.class);
		criteria.add(Restrictions.isNull("clockOut"));
		criteria.addOrder(Order.desc("clockIn"));

		List<Timesheet> timesheets = repository.select(Timesheet.class, criteria);

		Date clockOutDate = new Date();
		int counter = 0;
		for (Timesheet timesheet : timesheets) {
			timesheet.setClockOut(clockOutDate);

			if (counter++ > 50) {
				//ensure changes are persisted to DB before reclaiming memory
				Context.flushSession();
				Context.clearSession();
				counter = 0;
			}
		}
	}

	@Override
	public List<Timesheet> getTimesheetsByDate(Provider cashier, Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		Date startDate = calendar.getTime();

		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		Date endDate = calendar.getTime();

		Criteria criteria = repository.createCriteria(Timesheet.class);
		criteria.add(Restrictions.and(
				Restrictions.eq("cashier", cashier),
				Restrictions.or(
					// Start or end on date
					Restrictions.or(
						Restrictions.between(CLOCK_IN, startDate, endDate),
						Restrictions.between(CLOCK_OUT, startDate, endDate)
					),
					Restrictions.or(
						// Start on or before date and have not ended
						Restrictions.and(
							Restrictions.le(CLOCK_IN, endDate),
							Restrictions.isNull(CLOCK_OUT)
						),
						// Start before and end after date
						Restrictions.and(
							Restrictions.le(CLOCK_IN, startDate),
							Restrictions.ge(CLOCK_OUT, endDate)
						)
					)
				)
			)
		);
		criteria.addOrder(Order.desc(CLOCK_IN));

		return repository.select(Timesheet.class, criteria);
	}
}
