/*******************************************************************************
 * Copyright (c) 2022,2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package test.jakarta.data.web;

import java.time.OffsetDateTime;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import jakarta.data.Limit;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.Pageable;
import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;

import io.openliberty.data.repository.Compare;
import io.openliberty.data.repository.Filter;
import io.openliberty.data.repository.Function;
import io.openliberty.data.repository.Select;

/**
 * Uses the Repository interface that is copied from Jakarta NoSQL
 */
@Repository
public interface Reservations extends BasicRepository<Reservation, Long> {
    boolean deleteByHostIn(List<String> hosts);

    long deleteByHostNot(String host);

    @Select("meetingId")
    @Filter(by = "stop", fn = Function.WithSecond)
    @OrderBy("id")
    List<Long> endsAtSecond(int second);

    Iterable<Reservation> findByHost(String host);

    @OrderBy("id")
    Stream<Reservation> findByInviteesElementCount(int size);

    Collection<Reservation> findByLocationContainsOrderByMeetingID(String locationSubstring);

    List<Reservation> findByMeetingIDOrLocationLikeAndStartAndStopOrHost(long meetingID,
                                                                         String location,
                                                                         OffsetDateTime start,
                                                                         OffsetDateTime stop,
                                                                         String host);

    ArrayList<Reservation> findByStartBetweenAndLocationIn(OffsetDateTime minStart, OffsetDateTime maxStart, List<String> locations);

    CopyOnWriteArrayList<Reservation> findByStartGreaterThanOrderByStartDescStopDesc(OffsetDateTime startAfter, Limit maxResults);

    Reservation[] findByStartLessThanOrStartGreaterThanOrderByMeetingIDDesc(OffsetDateTime startBefore, OffsetDateTime startAfter);

    Vector<Reservation> findByStartNotBetween(OffsetDateTime startBefore, OffsetDateTime startAfter);

    LinkedList<Reservation> findByStopGreaterThanEqual(OffsetDateTime minEndTime);

    Stack<Reservation> findByStopGreaterThanOrderByLocationDescHostAscStopAsc(OffsetDateTime endAfter);

    UserDefinedCollection<Reservation> findByStopLessThan(OffsetDateTime maxEndTime, Sort... sortBy);

    AbstractCollection<Reservation> findByStopLessThanEqual(OffsetDateTime maxEndTime);

    AbstractList<Reservation> findByStopLessThanOrderByHostAscLocationDescStart(OffsetDateTime endBefore);

    Stream<Reservation> findByStopOrStart(OffsetDateTime stop, OffsetDateTime start);

    @Select("location")
    Stream<String> findByStopOrStartOrStart(OffsetDateTime stop, OffsetDateTime start1, OffsetDateTime start2);

    @Select("meetingID")
    LongStream findByStopOrStartOrStartOrStart(OffsetDateTime stop, OffsetDateTime start1, OffsetDateTime start2, OffsetDateTime start3);

    // Use a stream of record as the return type
    @Select({ "start", "stop" })
    Stream<ReservedTimeSlot> findByStopOrStopOrStop(OffsetDateTime stop1, OffsetDateTime stop2, OffsetDateTime stop3);

    Page<Reservation> findByHostStartsWith(String hostPrefix, Pageable pagination, Sort sort);

    LinkedHashSet<Reservation> findByInviteesContainsOrderByMeetingID(String invitee);

    HashSet<Reservation> findByLocationAndInviteesNotContains(String location, String noninvitee);

    @OrderBy("host")
    List<Long> findMeetingIdByStartWithHourBetweenAndStartWithMinute(int minHour, int maxHour, int minute);

    List<Long> findMeetingIdByStopWithSecond(int second);

    // Use a record as the return type
    ReservedTimeSlot[] findStartAndStopByLocationAndStartBetweenOrderByStart(String location, OffsetDateTime startAfter, OffsetDateTime startBefore);

    ArrayDeque<Reservation> findByLocationStartsWith(String locationPrefix);

    CopyOnWriteArrayList<Reservation> findByHostIgnoreCaseEndsWith(String hostPostfix);

    int removeByHostNotIn(Collection<String> hosts);

    @Select("meetingId")
    @Filter(by = "start", fn = Function.WithHour, op = Compare.Between)
    @Filter(by = "start", fn = Function.WithMinute)
    @OrderBy("host")
    List<Long> startsWithinHoursWithMinute(int minHour, int maxHour, int minute);

    int updateByHostAndLocationSetLocation(String host, String currentLocation, String newLocation);

    boolean updateByMeetingIDSetHost(long meetingID, String newHost);

    @Filter(by = "invitees", fn = Function.ElementCount)
    @OrderBy("id")
    Stream<Reservation> withInviteeCount(int size);
}