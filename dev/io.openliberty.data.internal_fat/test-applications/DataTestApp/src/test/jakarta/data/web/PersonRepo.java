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

import java.util.List;

import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.openliberty.data.repository.Filter;
import io.openliberty.data.repository.Select;
import io.openliberty.data.repository.update.Assign;

/**
 * This example only references the entity class as a parameterized type.
 * Do not add methods or inheritance that would allow the entity class
 * to be discovered another way.
 */
@Repository
@Transactional(TxType.SUPPORTS)
public interface PersonRepo {
    @Query("SELECT o FROM Person o WHERE o.lastName=?1")
    List<Person> find(String lastName);

    @Filter(by = "lastName")
    @OrderBy("firstName")
    @Select("firstName")
    List<String> findFirstNames(String surname);

    void insert(Person p);

    void insertAll(Person... p);

    void insertAll(Iterable<Person> p);

    void save(List<Person> people);

    @Select("firstName")
    @Transactional(TxType.SUPPORTS)
    String getFirstNameInCurrentOrNoTransaction(Long ssn_id);

    @Transactional(TxType.REQUIRED)
    boolean setFirstNameInCurrentOrNewTransaction(Long ssn_id,
                                                  @Assign String firstName);

    @Transactional(TxType.MANDATORY)
    boolean setFirstNameInCurrentTransaction(Long ssn_id,
                                             @Assign("firstName") String newFirstName);

    @Transactional(TxType.REQUIRES_NEW)
    boolean setFirstNameInNewTransaction(Long ssn_id,
                                         @Assign("FirstName") String newFirstName);

    @Transactional(TxType.NEVER)
    boolean setFirstNameWhenNoTransactionIsPresent(Long id,
                                                   @Assign("FIRSTNAME") String newFirstName);

    @Transactional(TxType.NOT_SUPPORTED)
    boolean setFirstNameWithCurrentTransactionSuspended(Long id,
                                                        @Assign("firstname") String newFirstName);

    boolean updateOne(Person person);

    long updateSome(Person... people);
}