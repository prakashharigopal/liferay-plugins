/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This file is part of Liferay Social Office. Liferay Social Office is free
 * software: you can redistribute it and/or modify it under the terms of the GNU
 * Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Liferay Social Office is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Liferay Social Office. If not, see http://www.gnu.org/licenses/agpl-3.0.html.
 */

package com.liferay.so.hook.service.impl;

import com.liferay.compat.portal.kernel.util.Time;
import com.liferay.compat.portal.util.PortalUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.DestinationNames;
import com.liferay.portal.kernel.messaging.MessageBusUtil;
import com.liferay.portal.kernel.notifications.ChannelHubManagerUtil;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.notifications.NotificationEventFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.service.OrganizationLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portlet.announcements.model.AnnouncementsEntry;
import com.liferay.portlet.announcements.service.AnnouncementsEntryLocalService;
import com.liferay.portlet.announcements.service.AnnouncementsEntryLocalServiceWrapper;
import com.liferay.portlet.announcements.service.persistence.AnnouncementsEntryFinderUtil;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author Jonathan Lee
 * @author Evan Thibodeau
 */
public class SOAnnouncementsEntryLocalServiceImpl
	extends AnnouncementsEntryLocalServiceWrapper {

	public SOAnnouncementsEntryLocalServiceImpl(
		AnnouncementsEntryLocalService announcementsEntryLocalService) {

		super(announcementsEntryLocalService);
	}

	public AnnouncementsEntry addEntry(
			long userId, long classNameId, long classPK, String title,
			String content, String url, String type, int displayDateMonth,
			int displayDateDay, int displayDateYear, int displayDateHour,
			int displayDateMinute, boolean autoDisplayDate,
			int expirationDateMonth, int expirationDateDay,
			int expirationDateYear, int expirationDateHour,
			int expirationDateMinute, int priority, boolean alert)
		throws PortalException, SystemException {

		return addEntry(
			userId, classNameId, classPK, title, content, url, type,
			displayDateMonth, displayDateDay, displayDateYear, displayDateHour,
			displayDateMinute, expirationDateMonth, expirationDateDay,
			expirationDateYear, expirationDateHour, expirationDateMinute,
			priority, alert);
	}

	@Override
	public AnnouncementsEntry addEntry(
			long plid, long classNameId, long classPK, String title,
			String content, String url, String type, int displayDateMonth,
			int displayDateDay, int displayDateYear, int displayDateHour,
			int displayDateMinute, int expirationDateMonth,
			int expirationDateDay, int expirationDateYear,
			int expirationDateHour, int expirationDateMinute, int priority,
			boolean alert)
		throws PortalException, SystemException {

		AnnouncementsEntry announcementEntry = super.addEntry(
			plid, classNameId, classPK, title, content, url, type,
			displayDateMonth, displayDateDay, displayDateYear, displayDateHour,
			displayDateMinute, expirationDateMonth, expirationDateDay,
			expirationDateYear, expirationDateHour, expirationDateMinute,
			priority, alert);

		if (announcementEntry != null) {
			Date displayDate = announcementEntry.getDisplayDate();

			if (!displayDate.after(announcementEntry.getCreateDate())) {
				sendNotificationEvent(announcementEntry);
			}
		}

		return announcementEntry;
	}

	@Override
	public void checkEntries() throws PortalException, SystemException {
		super.checkEntries();

		sendNotificationEvent();
	}

	protected void sendNotificationEvent()
		throws PortalException, SystemException {

		Date now = new Date();

		if (_previousCheckDate == null) {
			_previousCheckDate = new Date(
				now.getTime() - _ANNOUNCEMENTS_ENTRY_CHECK_INTERVAL);
		}

		List<AnnouncementsEntry> announcementEntries =
			AnnouncementsEntryFinderUtil.findByDisplayDate(
				now, _previousCheckDate);

		if (_log.isDebugEnabled()) {
			_log.debug("Processing " + announcementEntries.size() + " entries");
		}

		for (AnnouncementsEntry announcementEntry : announcementEntries) {
			Date displayDate = announcementEntry.getDisplayDate();

			if (displayDate.after(announcementEntry.getCreateDate())) {
				sendNotificationEvent(announcementEntry);
			}
		}

		_previousCheckDate = now;
	}

	protected void sendNotificationEvent(AnnouncementsEntry announcementEntry)
		throws PortalException, SystemException {

		JSONObject notificationEventJSONObject =
			JSONFactoryUtil.createJSONObject();

		notificationEventJSONObject.put("body", announcementEntry.getTitle());
		notificationEventJSONObject.put(
			"entryId", announcementEntry.getEntryId());
		notificationEventJSONObject.put(
			"groupId", announcementEntry.getClassPK());
		notificationEventJSONObject.put("portletId", PortletKeys.ANNOUNCEMENTS);
		notificationEventJSONObject.put("title", "x-sent-a-new-announcement");
		notificationEventJSONObject.put(
			"userId", announcementEntry.getUserId());

		MessageBusUtil.sendMessage(
			DestinationNames.ASYNC_SERVICE,
			new Runnable() {

				@Override
				public void run() {
					try {
						sendUserNotifications(
							announcementEntry, notificationEventJSONObject);
					} catch (Throwable t) {
						throw new RuntimeException(t);
					}
				}

				protected void sendUserNotifications(
						AnnouncementsEntry announcementEntry,
						JSONObject notificationEventJSONObject)
					throws PortalException, SystemException {

					List<User> users = null;

					LinkedHashMap<String, Object> params =
						new LinkedHashMap<String, Object>();

					if (announcementEntry.getClassNameId() == 0) {
						users = UserLocalServiceUtil.getUsers(
							QueryUtil.ALL_POS, QueryUtil.ALL_POS);
					}
					else {
						String className = announcementEntry.getClassName();

						long classPK = announcementEntry.getClassPK();

						if (classPK > 0) {
							if (className.equals(Group.class.getName())) {
								params.put("inherit", Boolean.TRUE);
								params.put("usersGroups", classPK);
							}
							else if (
								className.equals(
									Organization.class.getName())) {
								Organization organization =
									OrganizationLocalServiceUtil.
										fetchOrganization(
											classPK);

								if (organization == null) {
									return;
								}

								params.put(
									"usersOrgsTree",
									ListUtil.fromArray(
										new Organization[]{organization}));
							}
							else if (className.equals(Role.class.getName())) {
								Role role =
									RoleLocalServiceUtil.fetchRole(classPK);

								if (role == null) {
									return;
								}

								if (
									role.getType() ==
										RoleConstants.TYPE_REGULAR) {
									params.put("inherit", Boolean.TRUE);
									params.put("usersRoles", classPK);
								}
								else {
									params.put(
										"userGroupRole",
										new Long[] {Long.valueOf(0), classPK});
								}
							}
							else if (className.equals(UserGroup.class.getName())) {
								params.put("usersUserGroups", classPK);
							}
						}

						users = UserLocalServiceUtil.search(
							announcementEntry.getCompanyId(), null,
							WorkflowConstants.STATUS_APPROVED, params,
							QueryUtil.ALL_POS, QueryUtil.ALL_POS,
							(OrderByComparator)null);
					}

					for (User user : users) {
						NotificationEvent notificationEvent =
							NotificationEventFactoryUtil.createNotificationEvent(
								System.currentTimeMillis(), "6_WAR_soportlet",
								notificationEventJSONObject);

						notificationEvent.setDeliveryRequired(0);

						ChannelHubManagerUtil.sendNotificationEvent(
							user.getCompanyId(), user.getUserId(),
							notificationEvent);
					}
				}
			}
		);
	}

	private static final long _ANNOUNCEMENTS_ENTRY_CHECK_INTERVAL =
		GetterUtil.getInteger(
			PropsUtil.get(
				PropsKeys.ANNOUNCEMENTS_ENTRY_CHECK_INTERVAL)) *
		Time.MINUTE;

	private static Log _log = LogFactoryUtil.getLog(
		SOAnnouncementsEntryLocalServiceImpl.class);

	private Date _previousCheckDate;

}