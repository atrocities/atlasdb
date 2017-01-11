/**
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.timelock.atomix;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.palantir.atlasdb.timestamp.TimestampServiceTests;

import io.atomix.AtomixReplica;
import io.atomix.catalyst.transport.Address;
import io.atomix.catalyst.transport.local.LocalServerRegistry;
import io.atomix.catalyst.transport.local.LocalTransport;
import io.atomix.copycat.server.storage.Storage;
import io.atomix.copycat.server.storage.StorageLevel;
import io.atomix.variables.DistributedLong;

public class AtomixTimestampServiceIntegrationTest {
    private static final Address LOCAL_ADDRESS = new Address("localhost", 8700);
    private static final String CLIENT_KEY = "client";

    private static final AtomixReplica ATOMIX_REPLICA = AtomixReplica.builder(LOCAL_ADDRESS)
            .withStorage(Storage.builder()
                    .withStorageLevel(StorageLevel.MEMORY)
                    .build())
            .withTransport(new LocalTransport(new LocalServerRegistry()))
            .build();

    private AtomixTimestampService atomixTimestampService;

    @BeforeClass
    public static void startAtomix() {
        ATOMIX_REPLICA.bootstrap().join();
    }

    @AfterClass
    public static void stopAtomix() {
        ATOMIX_REPLICA.leave();
    }

    @Before
    public void setupTimestampService() {
        DistributedLong distributedLong = DistributedValues.getTimestampForClient(ATOMIX_REPLICA, CLIENT_KEY);
        atomixTimestampService = new AtomixTimestampService(distributedLong);
    }

    @Test
    public void timestampsAreReturnedInOrder() {
        TimestampServiceTests.timestampsAreReturnedInOrder(atomixTimestampService);
    }

    @Test
    public void canRequestTimestampRange() {
        TimestampServiceTests.canRequestTimestampRangeWithGetFreshTimestamps(atomixTimestampService);
    }

    @Test
    public void timestampRangesAreReturnedInNonOverlappingOrder() {
        TimestampServiceTests.timestampRangesAreReturnedInNonOverlappingOrder(atomixTimestampService);
    }

    @Test
    public void willNotHandOutTimestampsEarlierThanAFastForward() {
        TimestampServiceTests.willNotHandOutTimestampsEarlierThanAFastForward(
                atomixTimestampService,
                atomixTimestampService);
    }

    @Test public void
    willDoNothingWhenFastForwardToEarlierTimestamp() {
        TimestampServiceTests.willDoNothingWhenFastForwardToEarlierTimestamp(
                atomixTimestampService,
                atomixTimestampService);
    }

    @Test
    public void canReturnManyUniqueTimestampsInParallel() throws TimeoutException, InterruptedException {
        TimestampServiceTests.canReturnManyUniqueTimestampsInParallel(atomixTimestampService);
    }

    @Test
    public void shouldThrowIfRequestingNegativeNumbersOfTimestamps() {
        TimestampServiceTests.shouldThrowIfRequestingNegativeNumbersOfTimestamps(atomixTimestampService);
    }

    @Test
    public void shouldThrowIfRequestingZeroTimestamps() {
        TimestampServiceTests.shouldThrowIfRequestingZeroTimestamps(atomixTimestampService);
    }

    @Test
    public void shouldThrowIfRequestingTooManyTimestamps() {
        assertThatThrownBy(() -> atomixTimestampService.getFreshTimestamps(AtomixTimestampService.MAX_GRANT_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
