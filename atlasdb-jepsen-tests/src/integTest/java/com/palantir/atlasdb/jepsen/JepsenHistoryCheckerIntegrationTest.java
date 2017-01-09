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
package com.palantir.atlasdb.jepsen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.palantir.atlasdb.jepsen.lock.IsolatedProcessCorrectnessChecker;
import com.palantir.atlasdb.jepsen.lock.LockCorrectnessChecker;
import com.palantir.atlasdb.jepsen.lock.RefreshCorrectnessChecker;

import clojure.lang.Keyword;
import one.util.streamex.EntryStream;

public class JepsenHistoryCheckerIntegrationTest {
    @Test
    public void correctExampleHistoryShouldReturnValidAndNoErrors() throws IOException {
        List<Map<Keyword, ?>> convertedAllEvents = getClojureMapFromFile("correct_history.json");

        Map<Keyword, Object> results = JepsenHistoryCheckers.createWithTimestampCheckers()
                .checkClojureHistory(convertedAllEvents);

        assertThat(results).containsEntry(Keyword.intern("valid?"), true);
        assertThat(results).containsEntry(Keyword.intern("errors"), ImmutableList.of());
    }

    @Test
    public void correctLockTestHistoryShouldReturnValidAndNoErrors() throws IOException {
        List<Map<Keyword, ?>> convertedAllEvents = getClojureMapFromFile("lock_test_without_nemesis.json");

        Map<Keyword, Object> results = JepsenHistoryCheckers.createWithLockCheckers()
                .checkClojureHistory(convertedAllEvents);

        assertThat(results).containsEntry(Keyword.intern("valid?"), true);
        assertThat(results).containsEntry(Keyword.intern("errors"), ImmutableList.of());
    }

    @Test
    public void livenessFailingHistoryShouldReturnInvalidWithNemesisErrors() throws IOException {
        List<Map<Keyword, ?>> convertedAllEvents = getClojureMapFromFile("liveness_failing_history.json");

        Map<Keyword, Object> results = JepsenHistoryCheckers.createWithTimestampCheckers()
                .checkClojureHistory(convertedAllEvents);

        Map<Keyword, ?> nemesisStartEventMap = ImmutableMap.of(
                Keyword.intern("f"), "start",
                Keyword.intern("process"), "nemesis",
                Keyword.intern("type"), "info",
                Keyword.intern("value"), "start!",
                Keyword.intern("time"), 18784227842L);
        Map<Keyword, ?> nemesisStopEventMap = ImmutableMap.of(
                Keyword.intern("f"), "stop",
                Keyword.intern("process"), "nemesis",
                Keyword.intern("type"), "info",
                Keyword.intern("value"), "stop!",
                Keyword.intern("time"), 18805796986L);
        List<Map<Keyword, ?>> expected = ImmutableList.of(nemesisStartEventMap, nemesisStopEventMap);

        assertThat(results).containsEntry(Keyword.intern("valid?"), false);
        assertThat(results).containsEntry(Keyword.intern("errors"), expected);
    }

    private static List<Map<Keyword, ?>> getClojureMapFromFile(String resourcePath) throws IOException {
        List<Map<String, ?>> allEvents = new ObjectMapper().readValue(Resources.getResource(resourcePath),
                new TypeReference<List<Map<String, ?>>>() {});
        return allEvents.stream()
                .map(singleEvent -> {
                    Map<Keyword, Object> convertedEvent = new HashMap<>();
                    EntryStream.of(singleEvent)
                            .mapKeys(Keyword::intern)
                            .mapValues(value -> value instanceof String ? Keyword.intern((String) value) : value)
                            .forKeyValue(convertedEvent::put);
                    return convertedEvent;
                })
                .collect(Collectors.toList());
    }
}
